/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.replay.core.handlers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import pt.inesc.BufferTools;
import pt.inesc.manager.utils.MonitorWaiter;
import pt.inesc.proxy.save.CassandraClient;

public class HandlerRead
        implements CompletionHandler<Integer, AsynchronousSocketChannel> {
    private static final Logger log = LogManager.getLogger(HandlerRead.class.getName());

    private final CassandraClient cassandra;
    private final ByteBuffer buffer;
    private final MonitorWaiter sentCounter;
    public static final ByteBuffer RESPONSE = ByteBuffer.wrap("HTTP".getBytes());



    public HandlerRead(CassandraClient cassandra, ByteBuffer buffer, MonitorWaiter sentCounter) {
        super();
        this.cassandra = cassandra;
        this.buffer = buffer;
        this.sentCounter = sentCounter;
    }

    @Override
    public void completed(Integer bytesRead, AsynchronousSocketChannel channel) {
        if (buffer.remaining() == 0) {
            // More to read
            resizeBuffer(buffer);
            System.out.println("Buffer is too small");
            channel.read(buffer, channel, this);
        } else {
            buffer.flip(); // make buffer readable
            buffer.rewind();

            System.out.println(BufferTools.printContent(buffer));

            // search for all responses within the buffer
            ArrayList<Long> ids = BufferTools.getIds(buffer);
            for (Long rid : ids) {
                int left = sentCounter.decrement(rid);
                System.out.println("Read: " + rid + ", left: " + left);
            }

            // handle next read
            buffer.clear();
            channel.read(buffer, channel, this);
        }
    }

    @Override
    public void failed(Throwable exc, AsynchronousSocketChannel channel) {
        log.error("Read request failed", exc);
        // TODO Handle this exception
    }

    private void processRead(ChannelPack aux, long rid) {
        ByteBuffer originalResponse = cassandra.getResponse(rid);
        if (originalResponse != null) {
            try {
                String diff = ResponseComparator.compare(originalResponse, buffer);
                if (diff.length() > 0)
                    log.error(diff);
            } catch (IOException e) {
                log.error(e);
            }
        }
    }

    private void resizeBuffer(ByteBuffer buffer) {
        ByteBuffer newBuffer = ByteBuffer.allocate(buffer.capacity() * 2);
        newBuffer.put(buffer);
        buffer = newBuffer;
    }
}
