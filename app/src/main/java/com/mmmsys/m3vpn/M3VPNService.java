/*
** Copyright 2015, Mohamed Naufal
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package com.mmmsys.m3vpn;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.mmsys.spidernet.SpidernetRunnable;
import com.mmsys.spidernet.SpidernetUDPInput;
import com.mmsys.spidernet.SpidernetUDPOutput;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class M3VPNService extends VpnService
{
    private static final String TAG = M3VPNService.class.getSimpleName();
    private static final String VPN_ADDRESS = "10.0.0.2"; // Only IPv4 support for now
    private static final String VPN_ROUTE = "0.0.0.0"; // Intercept everything

    public static final String BROADCAST_VPN_STATE = "xyz.hexene.localvpn.VPN_STATE";

    private static boolean isRunning = false;

    private ParcelFileDescriptor vpnInterface = null;
    private Map<Integer, Object> tunConfigs = new HashMap<>();

    private PendingIntent pendingIntent;

    private ConcurrentLinkedQueue<Packet> deviceToNetworkQueue;
    private ConcurrentLinkedQueue<ByteBuffer> networkToDeviceQueue;
    private ExecutorService executorService;

    private Map<String, ConcurrentLinkedQueue<Packet>> vpnoutqueues;



    //private Selector oneUDPSelector;
    private Selector udpSelector;
    //private Selector tcpSelector;

    @Override
    public void onCreate()
    {
        super.onCreate();
        isRunning = true;
        setupVPN();

        tunConfigs = M3VPNConfig.getQueueInstance().getAll();
        if(tunConfigs==null){

        }


        try
        {
            udpSelector = Selector.open();
            //tcpSelector = Selector.open();
            deviceToNetworkQueue = new ConcurrentLinkedQueue<>();
            //networkToDeviceQueue = new ConcurrentLinkedQueue<>();

            FileChannel vpnInput = new FileInputStream(vpnInterface.getFileDescriptor()).getChannel();
            FileChannel vpnOutput = new FileOutputStream(vpnInterface.getFileDescriptor()).getChannel();

            executorService = Executors.newFixedThreadPool(3);
            //executorService.execute(new SpidernetTCPInput(vpnOutput, tcpSelector));
            //executorService.execute(new SpidernetTCPOutput(deviceToNetworkQueue, tcpSelector, this,tunConfigs));
            //executorService.execute(new SpidernetRunnable(vpnInput,deviceToNetworkQueue));


            executorService.execute(new SpidernetUDPOutput(deviceToNetworkQueue, udpSelector, this,tunConfigs));
            executorService.execute(new SpidernetUDPInput(vpnOutput,udpSelector));
            executorService.execute(new SpidernetRunnable(vpnInput,deviceToNetworkQueue));

            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(BROADCAST_VPN_STATE).putExtra("running", true));
            Log.i(TAG, "Started");
        }
        catch (IOException e)
        {
            // TODO: Here and elsewhere, we should explicitly notify the user of any errors
            // and suggest that they stop the service, since we can't do it ourselves
            Log.e(TAG, "Error starting service", e);
            cleanup();
        }
    }

    private void setupVPN()
    {
        if (vpnInterface == null)
        {
            Builder builder = new Builder();
            builder.addAddress(VPN_ADDRESS, 32);
            builder.addRoute(VPN_ROUTE, 0);
            vpnInterface = builder.setSession(getString(R.string.app_name)).setConfigureIntent(pendingIntent).establish();
        }
    }



    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        return START_STICKY;
    }

    public static boolean isRunning()
    {
        return isRunning;
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        isRunning = false;
        executorService.shutdownNow();
        cleanup();
        Log.i(TAG, "Stopped");
    }

    private void cleanup()
    {
        //deviceToNetworkTCPQueue = null;
        deviceToNetworkQueue = null;
        networkToDeviceQueue = null;
        ByteBufferPool.clear();
        closeResources(udpSelector, vpnInterface);
    }

    // TODO: Move this to a "utils" class for reuse
    private static void closeResources(Closeable... resources)
    {
        for (Closeable resource : resources)
        {
            try
            {
                resource.close();
            }
            catch (IOException e)
            {
                // Ignore
            }
        }
    }


}
