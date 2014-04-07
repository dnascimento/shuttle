package pt.inesc.redoNode.core.handlers;

import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;

public class HandlerRead
        implements CompletionHandler<Integer, ChannelPack> {


    public void completed(Integer bytesRead, ChannelPack aux) {
        if (aux.buffer.remaining() == 0) {
            // More to read
            resizeBuffer(aux.buffer);
            aux.channel.read(aux.buffer, aux, new HandlerRead());
        } else {
            processRead(aux);
            int remain = aux.sentCounter.decrementAndGet();
            System.out.println("Saw: " + remain);
            if (remain == 0) {
                // wake thread
                synchronized (aux.sentCounter) {
                    aux.sentCounter.notify();
                    System.out.println("wake");
                }
            }
        }
    }


    public void failed(Throwable exc, ChannelPack channel) {
        System.out.println("Read fail");
        exc.printStackTrace();
        // //TODO se isto for frequente mais vale fechar e abrir sempre
        // //Reconnect and re-write
        // java.io.IOException

    }

    private void processRead(ChannelPack aux) {
        ByteBuffer originalResponse = aux.cassandra.getResponse(aux.reqId);
        ByteBuffer buffer = aux.buffer;
        buffer.flip(); // make buffer readable
        buffer.rewind();
        if (originalResponse != null) {
            while (originalResponse.get() == buffer.get())
                ;


            boolean equals = (originalResponse.remaining() == 0) && (buffer.remaining() == 0);
            if (!equals) {
                // TODO show difference
            }
            // TODO Fix to compare correctly (cookies)
            System.out.println("Same response:" + equals);
        }
        // prepare for next read
        aux.buffer.clear();
    }

    private void resizeBuffer(ByteBuffer buffer) {
        ByteBuffer newBuffer = ByteBuffer.allocate(buffer.capacity() * 2);
        newBuffer.put(buffer);
        buffer = newBuffer;
    }




}
