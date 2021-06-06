package com.mmmsys.m3vpn;

import android.util.Log;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.ConcurrentLinkedQueue;

public class M3Runnable implements Runnable
{
    private static final String TAG = M3Runnable.class.getSimpleName();

    private FileDescriptor vpnFileDescriptor;
    private int tunnels;
    private ConcurrentLinkedQueue<Packet> vpndeviceToNetworkQueue;
    private ConcurrentLinkedQueue<ByteBuffer> vpnnetworkToDeviceQueue;


    public M3Runnable(FileDescriptor vpnFileDescriptor, ConcurrentLinkedQueue<Packet> deviceToNetworkQueue,
                       ConcurrentLinkedQueue<ByteBuffer> networkToDeviceQueue) {

        this.vpndeviceToNetworkQueue = deviceToNetworkQueue;
        this.vpnnetworkToDeviceQueue = networkToDeviceQueue;
        this.vpnFileDescriptor = vpnFileDescriptor;


    }

    @Override
    public void run()
    {
        Log.i(TAG, "Started");

        FileChannel vpnInput = new FileInputStream(vpnFileDescriptor).getChannel();
        FileChannel vpnOutput = new FileOutputStream(vpnFileDescriptor).getChannel();

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

                ByteBuffer bufferFromNetwork = vpnnetworkToDeviceQueue.poll();
                if (bufferFromNetwork != null)
                {
                    bufferFromNetwork.flip();
                    while (bufferFromNetwork.hasRemaining())
                        vpnOutput.write(bufferFromNetwork);
                    dataReceived = true;

                    M3ByteBufferPool.release(bufferFromNetwork);
                }
                else
                {
                    dataReceived = false;
                }

                // TODO: Sleep-looping is not very battery-friendly, consider blocking instead
                // Confirm if throughput with ConcurrentQueue is really higher compared to BlockingQueue
                if (!dataSent && !dataReceived)
                    Thread.sleep(10);
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
            closeResources(vpnInput, vpnOutput);
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