package com.mmsys.spidernet;

import android.util.Log;

import com.mmmsys.m3vpn.M3ByteBufferPool;
import com.mmmsys.m3vpn.Packet;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SpidernetRunnable implements Runnable
{
    private static final String TAG = SpidernetRunnable.class.getSimpleName();

    private FileChannel vpnInputChannel;
    private int tunnels;
    private ConcurrentLinkedQueue<Packet> vpndeviceToNetworkQueue;
    private ConcurrentLinkedQueue<ByteBuffer> vpnnetworkToDeviceQueue;


    public SpidernetRunnable(FileChannel inputChannel, ConcurrentLinkedQueue<Packet> deviceToNetworkQueue) {

        this.vpndeviceToNetworkQueue = deviceToNetworkQueue;
        //this.vpnnetworkToDeviceQueue = networkToDeviceQueue;
        this.vpnInputChannel = inputChannel;


    }

    @Override
    public void run()
    {
        Log.i(TAG, "Started");
        FileChannel vpnInput = vpnInputChannel;
        try
        {
            ByteBuffer bufferToNetwork = null;
            boolean dataSent = true;
            boolean dataReceived;
            while (!Thread.interrupted())
            {
                if (dataSent)
                    bufferToNetwork = M3ByteBufferPool.acquire();
                else
                    bufferToNetwork.clear();

                // TODO: Block when not connected
                int readBytes = vpnInput.read(bufferToNetwork);
                if (readBytes > 0)
                {
                    dataSent = true;
                    bufferToNetwork.flip();
                    Packet packet = new Packet(bufferToNetwork);
                    vpndeviceToNetworkQueue.offer(packet);
                }
                else
                {
                    dataSent = false;
                }


                // TODO: Sleep-looping is not very battery-friendly, consider blocking instead
                // Confirm if throughput with ConcurrentQueue is really higher compared to BlockingQueue
                if (!dataSent) {
                    //Log.d(TAG, "VPN thread  sleeping");
                    Thread.sleep(10);
                }
            }
        }
        catch (InterruptedException e)
        {
            Log.i(TAG, "Stopping");
        }
        catch (IOException e)
        {
            Log.w(TAG, e.toString(), e);
        }
        finally
        {
            closeResources(vpnInput);
        }
    }
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