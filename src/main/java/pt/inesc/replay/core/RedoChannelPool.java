package pt.inesc.replay.core;

import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import pt.inesc.proxy.save.CassandraClient;
import pt.inesc.replay.core.handlers.BiggestEndList;
import pt.inesc.replay.core.handlers.ChannelPack;

public class RedoChannelPool {
    private static Logger logger = LogManager.getLogger(RedoChannelPool.class.getName());
    private final InetSocketAddress remoteHost;
    private static final int INIT_NUMBER_OF_THREADS_AND_CHANNELS = 1;
    private final CassandraClient cassandra;
    LinkedList<ChannelPack> availableChannels = new LinkedList<ChannelPack>();
    AsynchronousChannelGroup group;
    private static final int BUFFER_SIZE = 512 * 1024;

    private BiggestEndList biggestEnd;
    private AtomicInteger sentCounter;

    private RedoChannelPool(InetSocketAddress remoteHost, CassandraClient cassandra) throws Exception {
        this.remoteHost = remoteHost;
        this.cassandra = cassandra;
        ExecutorService executor = Executors.newCachedThreadPool();
        group = AsynchronousChannelGroup.withCachedThreadPool(executor, INIT_NUMBER_OF_THREADS_AND_CHANNELS);
    }


    public RedoChannelPool(InetSocketAddress remoteHost, CassandraClient cassandra, BiggestEndList biggestEnd) throws Exception {
        this(remoteHost, cassandra);
        this.biggestEnd = biggestEnd;

        for (int i = 0; i < INIT_NUMBER_OF_THREADS_AND_CHANNELS; i++) {
            createPackChannel();
        }
    }

    public RedoChannelPool(InetSocketAddress remoteHost, CassandraClient cassandra, AtomicInteger sentCounter) throws Exception {
        this(remoteHost, cassandra);
        this.sentCounter = sentCounter;

        for (int i = 0; i < INIT_NUMBER_OF_THREADS_AND_CHANNELS; i++) {
            createPackChannel();
        }
    }


    public synchronized ChannelPack getChannel() throws Exception {
        if (availableChannels.isEmpty()) {
            logger.debug("I need sockets");
            createPackChannel();
        }
        ChannelPack p = availableChannels.removeFirst();
        return p;
    }


    public synchronized void returnChannel(ChannelPack channelPack) {
        try {
            channelPack.channel.close();
            channelPack.channel = createChannel();
            availableChannels.add(channelPack);
        } catch (Exception e) {
            logger.error(e);
        }
    }






    private AsynchronousSocketChannel createChannel() throws Exception {
        AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open(group);
        socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
        socketChannel.connect(remoteHost).get();
        return socketChannel;
    }

    private ChannelPack createPackChannel() throws Exception {
        AsynchronousSocketChannel socketChannel = createChannel();
        ByteBuffer buffer = allocateBuffer();
        ChannelPack pack = new ChannelPack(socketChannel, buffer, cassandra, this, biggestEnd, sentCounter);
        availableChannels.add(pack);
        return pack;
    }



    private ByteBuffer allocateBuffer() {
        return ByteBuffer.allocateDirect(BUFFER_SIZE).order(ByteOrder.BIG_ENDIAN);
    }


}
