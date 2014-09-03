/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.redo.core.handlers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public class HandlerRead
        implements CompletionHandler<Integer, ChannelPack> {
    private static final Logger log = LogManager.getLogger(HandlerRead.class.getName());



    @Override
    public void completed(Integer bytesRead, ChannelPack aux) {
        if (aux.buffer.remaining() == 0) {
            // More to read
            resizeBuffer(aux.buffer);
            aux.channel.read(aux.buffer, aux, new HandlerRead());
        } else {
            processRead(aux);
            if (aux.sentCounter != null) {
                int remain = aux.sentCounter.decrementAndGet();
                if (remain == 0) {
                    // wake thread
                    synchronized (aux.sentCounter) {
                        aux.sentCounter.notify();
                    }
                }
            }

            if (aux.biggestEnd != null) {
                synchronized (aux.biggestEnd) {
                    if (aux.biggestEnd.wasTheBiggest(aux.request.end)) {
                        aux.biggestEnd.notify();
                    }
                }
            }
        }
    }


    @Override
    public void failed(Throwable exc, ChannelPack channel) {
        log.error("Read fail", exc);
        // //TODO se isto for frequente mais vale fechar e abrir sempre
        // //Reconnect and re-write
        // java.io.IOException
    }

    private void processRead(ChannelPack aux) {
        ByteBuffer originalResponse = aux.cassandra.getResponse(aux.request.rid);
        ByteBuffer buffer = aux.buffer;
        buffer.flip(); // make buffer readable
        buffer.rewind();
        if (originalResponse != null) {
            try {
                String diff = ResponseComparator.compare(originalResponse, buffer);
                if (diff.length() > 0)
                    log.error(diff);
            } catch (IOException e) {
                log.error(e);
            }
        }
        // prepare for next read
        aux.renew();
    }

    private void resizeBuffer(ByteBuffer buffer) {
        ByteBuffer newBuffer = ByteBuffer.allocate(buffer.capacity() * 2);
        newBuffer.put(buffer);
        buffer = newBuffer;
    }




}
