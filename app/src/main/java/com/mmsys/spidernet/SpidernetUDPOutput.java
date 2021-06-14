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

package com.mmsys.spidernet;

import android.util.Log;

import com.mmmsys.m3vpn.M3ByteBufferPool;
import com.mmmsys.m3vpn.M3LRUCache;
import com.mmmsys.m3vpn.M3VPNService;
import com.mmmsys.m3vpn.Packet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SpidernetUDPOutput implements Runnable
{
    private static final String TAG = SpidernetUDPOutput.class.getSimpleName();

    private M3VPNService vpnService;
    private ConcurrentLinkedQueue<Packet> inputQueue;
    private Selector selector;
    private Map<Integer,Object> tunnelConfig = new HashMap<Integer, Object>();

    private static final int MAX_CACHE_SIZE = 6;

    private M3LRUCache<Integer, DatagramChannel> tunnelCache =
            new M3LRUCache<>(MAX_CACHE_SIZE, new M3LRUCache.CleanupCallback<String, DatagramChannel>()
            {
                @Override
                public void cleanup(Map.Entry<String, DatagramChannel> eldest)
                {
                    closeChannel(eldest.getValue());
                }
            });

    /**Upon acitivyt the client will reccecie  the list as an asynchronous method and push the criticla secction. The servicec reeive it  call
     * via the function params. In case if the list is empty the service falls back to the default function*/
    public SpidernetUDPOutput(ConcurrentLinkedQueue<Packet> inputQueue, Selector selector, M3VPNService vpnService, Map<Integer,Object> tunnelConfig)
    {
        this.inputQueue = inputQueue;
        this.selector = selector;
        this.vpnService = vpnService;
        this.tunnelConfig = tunnelConfig;
    }



    @Override
    public void run()
    {
        Log.i(TAG, "Started "+tunnelConfig.size());
        try
        {

            Thread currentThread = Thread.currentThread();
            while (true)
            {
                Packet currentPacket;
                // TODO: Block when not connected
                do
                {
                    currentPacket = inputQueue.poll();
                    if (currentPacket != null)
                        break;
                    Thread.sleep(10);
                } while (!currentThread.isInterrupted());

                if (currentThread.isInterrupted())
                    break;

                int context = currentPacket.ip4Header.typeOfService;
                DatagramChannel outputTunnel = tunnelCache.get(context);
                String config = (String) tunnelConfig.get(context);
                String [] ipAndPort = config.split("::");
                String destinationAddress = ipAndPort[0];
                int destinationPort = Integer.parseInt(ipAndPort[1]);
                if (outputTunnel == null) {
                    outputTunnel = DatagramChannel.open();
                    vpnService.protect(outputTunnel.socket());
                    try
                    {
                        outputTunnel.connect(new InetSocketAddress(destinationAddress, destinationPort));
                    }
                    catch (IOException e)
                    {
                        Log.e(TAG, "Connection error: " + ipAndPort, e);
                        closeChannel(outputTunnel);
                        M3ByteBufferPool.release(currentPacket.backingBuffer);
                        M3ByteBufferPool.release(currentPacket.copyPacket);
                        continue;
                    }
                    outputTunnel.configureBlocking(false);
                    outputTunnel.socket().setTrafficClass(context);
                    outputTunnel.socket().setSendBufferSize(65534);

                    selector.wakeup();
                    outputTunnel.register(selector, SelectionKey.OP_READ, currentPacket);
                    tunnelCache.put(context, outputTunnel);
                }

                try
                {

                    /* Current packet*/
                    //ByteBuffer payloadBuffer = currentPacket;
                    //while (payloadBuffer.hasRemaining())
                    outputTunnel.write(currentPacket.copyPacket);

                }
                catch (IOException e)
                {
                    Log.e(TAG, "Network write error: " + ipAndPort, e);
                    tunnelCache.remove(context);
                    closeChannel(outputTunnel);
                }
                M3ByteBufferPool.release(currentPacket.backingBuffer);
                M3ByteBufferPool.release(currentPacket.copyPacket);
            }
        }
        catch (InterruptedException e)
        {
            Log.i(TAG, "Stopping");
        }
        catch (IOException e)
        {
            Log.i(TAG, e.toString(), e);
        }
        finally
        {
            closeAll();
        }
    }

    private void closeAll()
    {
        Iterator<Map.Entry<Integer, DatagramChannel>> it = tunnelCache.entrySet().iterator();
        while (it.hasNext())
        {
            closeChannel(it.next().getValue());
            it.remove();
        }
    }

    private void closeChannel(DatagramChannel channel)
    {
        try
        {
            channel.close();
        }
        catch (IOException e)
        {
            // Ignore
        }
    }
}
