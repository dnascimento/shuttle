/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.redo.core.handlers;

import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public class HandlerRead
        implements CompletionHandler<Integer, ChannelPack> {
    private static final Logger log = LogManager.getLogger(HandlerRead.class.getName());


    public void completed(Integer bytesRead, ChannelPack aux) {
        if (aux.buffer.remaining() == 0) {
            // More to read
            resizeBuffer(aux.buffer);
            aux.channel.read(aux.buffer, aux, new HandlerRead());
        } else {
            processRead(aux);
            int remain = aux.sentCounter.decrementAndGet();
            if (remain == 0) {
                // wake thread
                synchronized (aux.sentCounter) {
                    aux.sentCounter.notify();
                }
            }
        }
    }


    public void failed(Throwable exc, ChannelPack channel) {
        log.error("Read fail", exc);
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
            log.info("Same response:" + equals);
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
