package com.mmmsys.m3vpn;

import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;


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

public class M3TCPOutput implements Runnable
{
    private static final String TAG = M3TCPOutput.class.getSimpleName();

    private M3VPNService vpnService;
    private ConcurrentLinkedQueue<Packet> inputQueue;
    private ConcurrentLinkedQueue<ByteBuffer> outputQueue;
    private Selector selector;

    private Map<Integer,Object> tunnelConfig = new HashMap<Integer, Object>();

    private static final int MAX_CACHE_SIZE = 6;

    private LRUCache<Integer, SocketChannel> tunnelCache =
            new LRUCache<>(MAX_CACHE_SIZE, new LRUCache.CleanupCallback<String, SocketChannel>()
            {
                @Override
                public void cleanup(Map.Entry<String, SocketChannel> eldest)
                {
                    closeChannel(eldest.getValue());
                }
            });


    private Random random = new Random();
    public M3TCPOutput(ConcurrentLinkedQueue<Packet> inputQueue, Selector selector, M3VPNService vpnService, Map<Integer,Object> tunnelConfig)
    {
        this.inputQueue = inputQueue;
        this.selector = selector;
        this.vpnService = vpnService;
        this.tunnelConfig = tunnelConfig;
    }


    @Override
    public void run()
    {
        Log.i(TAG, "Started");
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
                    if (currentPacket != null) {
                        Log.d(TAG, "Packet context "+ currentPacket.ip4Header.destinationAddress.toString());
                        break;
                    }
                    Thread.sleep(10);

                } while (!currentThread.isInterrupted());

                if (currentThread.isInterrupted())
                    break;

                //int context = currentPacket.ip4Header.typeOfService;

                int context=0;

                SocketChannel outputTunnel = tunnelCache.get(context);
                String config = (String) tunnelConfig.get(context);
                String [] ipAndPort = config.split("::");
                String destinationAddress = ipAndPort[0];
                int destinationPort = Integer.parseInt(ipAndPort[1]);
                if (outputTunnel == null) {
                    outputTunnel = SocketChannel.open();
                    vpnService.protect(outputTunnel.socket());
                    try
                    {
                        outputTunnel.connect(new InetSocketAddress(destinationAddress, destinationPort));
                    }
                    catch (IOException e)
                    {
                        Log.e(TAG, "Connection error: " + ipAndPort, e);
                        closeChannel(outputTunnel);
                        ByteBufferPool.release(currentPacket.backingBuffer);
                        ByteBufferPool.release(currentPacket.copyPacket);
                        continue;
                    }
                    outputTunnel.configureBlocking(false);
                    outputTunnel.socket().setTrafficClass(context);
                    outputTunnel.socket().setTcpNoDelay(true);
                    outputTunnel.socket().setSendBufferSize(65534);
                    outputTunnel.socket().setReceiveBufferSize(65534);
                    outputTunnel.socket().setKeepAlive(true);

                    selector.wakeup();
                    outputTunnel.register(selector, SelectionKey.OP_READ, currentPacket);
                    tunnelCache.put(context, outputTunnel);
                    Log.d(TAG, "Connection cached "+tunnelCache.size());
                }

                try
                {

                    /* Current packet*/
                    //ByteBuffer payloadBuffer = currentPacket.copyPacket.rewind();
                    //while (payloadBuffer.hasRemaining())
                    outputTunnel.write(currentPacket.copyPacket);
                    Log.d(TAG, "Packet written to socket "+ context);

                }
                catch (IOException e)
                {
                    Log.e(TAG, "Network write error: " + ipAndPort, e);
                    tunnelCache.remove(context);
                    closeChannel(outputTunnel);
                }
                ByteBufferPool.release(currentPacket.backingBuffer);
                ByteBufferPool.release(currentPacket.copyPacket);
            }



        }
        catch (InterruptedException e)
        {
            Log.i(TAG, "Stopping");
        }
        catch (IOException e)
        {
            Log.e(TAG, e.toString(), e);
        }
        finally
        {
            closeAll();
        }
    }
    private void closeChannel(SocketChannel channel)
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
    private void closeAll()
    {
        Iterator<Map.Entry<Integer, SocketChannel>> it = tunnelCache.entrySet().iterator();
        while (it.hasNext())
        {
            closeChannel(it.next().getValue());
            it.remove();
        }
    }

}
