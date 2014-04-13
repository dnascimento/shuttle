package pt.inesc.redoNode.core.handlers;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

import pt.inesc.proxy.save.CassandraClient;

public class ChannelPack {
    public AsynchronousSocketChannel channel;
    public int bytesToProcess;
    public ByteBuffer buffer;
    public AtomicInteger sentCounter;
    public CassandraClient cassandra;
    public long reqId;

    public ChannelPack(AsynchronousSocketChannel channel, ByteBuffer buffer, CassandraClient cassandra) {
        this.channel = channel;
        this.buffer = buffer;
        this.cassandra = cassandra;
    }

    public void reset(int bytesToProcess, AtomicInteger sentCounter, long reqId) {
        this.bytesToProcess = bytesToProcess;
        this.sentCounter = sentCounter;
        this.reqId = reqId;
    }



}
