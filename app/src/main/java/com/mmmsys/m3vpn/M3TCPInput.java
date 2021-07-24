package com.mmmsys.m3vpn;

import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class M3TCPInput implements Runnable
{
    private static final String TAG = M3TCPInput.class.getSimpleName();
    private static final int HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.TCP_HEADER_SIZE;

    private Selector selector;
    private ConcurrentLinkedQueue<ByteBuffer> outputQueue;

    public M3TCPInput(ConcurrentLinkedQueue<ByteBuffer> outputQueue, Selector selector)
    {
        this.outputQueue = outputQueue;
        this.selector = selector;
    }

    @Override
    public void run()
    {
        try
        {
            Log.i(TAG, "Started");
            while (!Thread.interrupted())
            {
                Log.d(TAG, "Executing input");

                int readyChannels = selector.select();

                if (readyChannels == 0) {
                    Thread.sleep(100);
                    continue;
                }

                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = keys.iterator();

                while (keyIterator.hasNext() && !Thread.interrupted())
                {
                    SelectionKey key = keyIterator.next();
                    if (key.isValid() && key.isReadable())
                    {
                        keyIterator.remove();

                        //we do not nead the partial packet

                        ByteBuffer receiveBuffer = ByteBufferPool.acquire();
                        // Leave space for the header
                        //receiveBuffer.position(HEADER_SIZE);

                        SocketChannel inputChannel = (SocketChannel) key.channel();
                        // XXX: We should handle any IOExceptions here immediately,
                        // but that probably won't happen with UDP
                        int readBytes = inputChannel.read(receiveBuffer);

                        //Packet referencePacket = (Packet) key.attachment();
                        //referencePacket.updateUDPBuffer(receiveBuffer, readBytes);
                        //receiveBuffer.position(HEADER_SIZE + readBytes);

                        Log.d(TAG, "Received packet ");

                        outputQueue.offer(receiveBuffer);
                    }
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
    }
}
