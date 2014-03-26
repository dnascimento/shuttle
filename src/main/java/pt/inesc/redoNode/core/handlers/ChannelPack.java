package pt.inesc.redoNode.core.handlers;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

public class ChannelPack {
    public AsynchronousSocketChannel channel;
    public int bytesToProcess;
    public ByteBuffer buffer;
    public AtomicInteger sentCounter;

    public ChannelPack(AsynchronousSocketChannel channel, ByteBuffer buffer) {
        this.channel = channel;
        this.buffer = buffer;
    }

    public void reset(int bytesToProcess, AtomicInteger sentCounter) {
        this.bytesToProcess = bytesToProcess;
        this.sentCounter = sentCounter;
    }


}
