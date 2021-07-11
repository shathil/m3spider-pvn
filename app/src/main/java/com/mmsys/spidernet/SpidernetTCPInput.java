package com.mmsys.spidernet;

import android.util.Log;

import com.mmmsys.m3vpn.M3ByteBufferPool;
import com.mmmsys.m3vpn.Packet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SpidernetTCPInput implements Runnable
{
    private static final String TAG = SpidernetTCPInput.class.getSimpleName();
    private static final int HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.TCP_HEADER_SIZE;

    private Selector selector;
    private FileChannel vpnOutput;
    public SpidernetTCPInput(FileChannel vpnOutput, Selector selector)
    {
        this.vpnOutput = vpnOutput;
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
                    Thread.sleep(10);
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

                        ByteBuffer receiveBuffer = M3ByteBufferPool.acquire();
                        // Leave space for the header
                        //receiveBuffer.position(HEADER_SIZE);

                        SocketChannel inputChannel = (SocketChannel) key.channel();
                        // XXX: We should handle any IOExceptions here immediately,
                        // but that probably won't happen with UDP
                        try{
                            int readBytes = inputChannel.read(receiveBuffer);

                                //referencePacket.updateUDPBuffer(receiveBuffer, readBytes);
                                //receiveBuffer.position(HEADER_SIZE + readBytes);
                                if(readBytes>0) {

                                    Packet referencePacket = new Packet(receiveBuffer);

                                    Log.d(TAG, "Received packet " + referencePacket.toString());
                                    //receiveBuffer.rewind();
                                    receiveBuffer.flip();

                                    while (receiveBuffer.hasRemaining())
                                        vpnOutput.write(receiveBuffer);
                                    //M3ByteBufferPool.release(referencePacket.backingBuffer);
                                    //
                                    //
                                    // M3ByteBufferPool.release(referencePacket.copyPacket);
                                }
                        }catch (IOException ie) {
                            Log.d(TAG,ie.toString());
                        }
                        M3ByteBufferPool.release(receiveBuffer);
                    }
                }

            }
            Log.i(TAG, "Exited");
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
