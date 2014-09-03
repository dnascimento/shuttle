/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.redo.core.handlers;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

import pt.inesc.proxy.save.CassandraClient;
import pt.inesc.proxy.save.Request;
import pt.inesc.redo.core.RedoChannelPool;


public class ChannelPack {
    public AsynchronousSocketChannel channel;
    public int bytesToProcess;
    public Request request;
    public CassandraClient cassandra;
    public RedoChannelPool pool;
    public ByteBuffer buffer;
    public final AtomicInteger sentCounter;
    public final BiggestEndList biggestEnd;

    public ChannelPack(AsynchronousSocketChannel channel,
            ByteBuffer buffer,
            CassandraClient cassandra,
            RedoChannelPool redoChannelPool,
            BiggestEndList biggestEnd,
            AtomicInteger sentCounter) {
        this.buffer = buffer;
        this.channel = channel;
        this.cassandra = cassandra;
        this.pool = redoChannelPool;
        this.sentCounter = sentCounter;
        this.biggestEnd = biggestEnd;
        this.request = null;
    }

    public void reset(int bytesToProcess, Request request) {
        this.bytesToProcess = bytesToProcess;
        this.request = request;
    }

    public void renew() {
        buffer.clear();
        pool.returnChannel(this);
    }



}
