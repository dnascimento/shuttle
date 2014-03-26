package pt.inesc.redoNode;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import pt.inesc.proxy.save.CassandraClient;
import pt.inesc.redoNode.cookies.CookieMan;
import pt.inesc.redoNode.handlers.ChannelPack;
import pt.inesc.redoNode.handlers.HandlerWrite;

public class RedoWorker extends
        Thread {
    private static Logger logger = LogManager.getLogger("RedoWorker");

    private final InetSocketAddress remoteHost;
    private final long[] executionArray;
    public static CookieMan cookieManager = new CookieMan();
    private static final int BUFFER_SIZE = 512 * 1024;

    private static final int INIT_NUMBER_OF_THREADS_AND_CHANNELS = 1;

    private final AtomicInteger sentCounter = new AtomicInteger(0);
    AsynchronousChannelGroup group;
    LinkedList<ChannelPack> availableChannels = new LinkedList<ChannelPack>();


    public RedoWorker(long[] executionArray, String remoteHostname, int remotePort) throws IOException {
        super();
        this.executionArray = executionArray;
        remoteHost = new InetSocketAddress(InetAddress.getByName(remoteHostname),
                remotePort);
        logger.info("New Worker");
        // create a variable group of threads to handle each channel
        ExecutorService executor = Executors.newCachedThreadPool();
        group = AsynchronousChannelGroup.withCachedThreadPool(executor,
                                                              INIT_NUMBER_OF_THREADS_AND_CHANNELS);
        createChannels(INIT_NUMBER_OF_THREADS_AND_CHANNELS);
    }

    /** Creates n channels to the host: connect and add to available channel list */
    private void createChannels(int nChannels) {
        for (int i = 0; i < nChannels; i++) {
            createPackChannel();
        }
    }


    private ChannelPack createPackChannel() {
        AsynchronousSocketChannel socketChannel = createChannel();
        ByteBuffer buffer = allocateBuffer();
        ChannelPack pack = new ChannelPack(socketChannel, buffer);
        availableChannels.add(pack);
        return pack;
    }

    private AsynchronousSocketChannel createChannel() {
        try {
            AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open(group);
            socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
            socketChannel.connect(remoteHost).get();
            return socketChannel;
        } catch (Exception e) {
            // TODO logger and fix where to catch, cannot return null
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void run() {
        System.out.println("time:" + new Date().getTime());
        CassandraClient cassandra = new CassandraClient();
        Iterator<ChannelPack> channelsIterator = availableChannels.iterator();

        for (long reqID : executionArray) {
            if (reqID == -1) {
                try {
                    synchronized (sentCounter) {
                        sentCounter.wait();
                        System.out.println("continue");
                        refreshSockets();
                        // all channel were used, refresh
                        channelsIterator = availableChannels.iterator();
                        continue;
                    }
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            ChannelPack pack;
            if (!channelsIterator.hasNext()) {
                System.out.println("I need sockets");
                pack = createPackChannel();
            } else {
                pack = channelsIterator.next();
            }
            System.out.println("got socket");
            ByteBuffer request = cassandra.getRequest(reqID);
            // NOTE: request includes cassandra metadata at begin. DO NOT rewind

            sentCounter.incrementAndGet();
            pack.reset(request.limit() - request.position(), sentCounter);
            System.out.println("Channel remain open: " + pack.channel.isOpen());

            pack.channel.write(request, pack, new HandlerWrite());
        }
    }

    /**
     * Closes the current sockets and open new sockets ready to process the next requests
     */
    private void refreshSockets() {
        // TODO avoid it by keeping the connection alive
        for (ChannelPack ch : availableChannels) {
            try {
                ch.channel.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            ch.channel = createChannel();
        }
    }



    private ByteBuffer allocateBuffer() {
        return ByteBuffer.allocateDirect(BUFFER_SIZE).order(ByteOrder.BIG_ENDIAN);
    }

}
