package pt.inesc.redo.core;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import pt.inesc.proxy.save.CassandraClient;
import pt.inesc.redo.RedoNode;
import pt.inesc.redo.core.cookies.CookieMan;
import pt.inesc.redo.core.handlers.ChannelPack;
import pt.inesc.redo.core.handlers.HandlerWrite;

public class RedoWorker extends
        Thread {
    private static Logger logger = LogManager.getLogger(RedoWorker.class.getName());

    private final InetSocketAddress remoteHost;
    private final List<Long> executionArray;
    public static CookieMan cookieManager = new CookieMan();
    private static final int BUFFER_SIZE = 512 * 1024;

    private static final int INIT_NUMBER_OF_THREADS_AND_CHANNELS = 1;
    private final CassandraClient cassandra;
    private final AtomicInteger sentCounter = new AtomicInteger(0);
    AsynchronousChannelGroup group;
    LinkedList<ChannelPack> availableChannels = new LinkedList<ChannelPack>();
    private final ByteBuffer ID_MARK = ByteBuffer.wrap("ID: ".getBytes());
    private final byte[] branch;


    public RedoWorker(List<Long> execList, InetSocketAddress remoteHost, short branch) throws IOException {
        super();
        this.executionArray = execList;
        this.remoteHost = remoteHost;
        logger.info("New Worker");
        cassandra = new CassandraClient();
        this.branch = shortToByteArray(branch);

        // create a variable group of threads to handle each channel
        ExecutorService executor = Executors.newCachedThreadPool();
        group = AsynchronousChannelGroup.withCachedThreadPool(executor, INIT_NUMBER_OF_THREADS_AND_CHANNELS);
        createChannels(INIT_NUMBER_OF_THREADS_AND_CHANNELS);

    }


    @Override
    public void run() {
        System.out.println("time:" + new Date().getTime());
        Iterator<ChannelPack> channelsIterator = availableChannels.iterator();

        for (long reqID : executionArray) {
            System.out.println("Redo Request:" + reqID);
            if (reqID == -1) {
                try {
                    synchronized (sentCounter) {
                        if (sentCounter.get() != 0) {
                            sentCounter.wait();
                        }
                        System.out.println("continue");
                        refreshSockets();
                        // all channel were used, refresh
                        channelsIterator = availableChannels.iterator();
                        continue;
                    }
                } catch (InterruptedException e) {
                    logger.error(e);
                }
            }
            ChannelPack pack;
            if (!channelsIterator.hasNext()) {
                logger.debug("I need sockets");
                pack = createPackChannel();
            } else {
                pack = channelsIterator.next();
            }
            logger.debug("got socket");
            ByteBuffer request = cassandra.getRequest(reqID);
            if (request == null) {
                logger.error("Request not found " + reqID);
                continue;
            }
            // IMPORTANT: request includes cassandra metadata at begin. DO NOT rewind
            sentCounter.incrementAndGet();

            logger.debug("Channel remain open: " + pack.channel.isOpen());
            try {
                writePackage(pack, request, reqID);
            } catch (Exception e) {
                logger.error("Erro", e);
            }
        }

        logger.info("Redo end");
        RedoNode.sendAck();
    }

    private void writePackage(ChannelPack pack, ByteBuffer data, long rid) throws InterruptedException,
            ExecutionException {
        // ProxyWorker.printContent(data);
        setNewHeader(data, rid);
        pack.reset(data.limit() - data.position(), sentCounter, rid);
        pack.channel.write(data, pack, new HandlerWrite());

    }


    private void setNewHeader(ByteBuffer data, long rid) {
        int initPosition = data.position();
        int startOfId = indexOf(data.position(), data.limit(), data, ID_MARK) + ID_MARK.capacity();
        byte[] ts = new Long(rid).toString().getBytes();
        data.position(startOfId);
        data.put(ts);
        data.position(startOfId + 17);
        data.put(branch);
        // restrain is always false
        data.position(initPosition);
    }

    /**
     * Returns the index within this buffer of the first occurrence of the specified
     * pattern buffer.
     * 
     * @param startPosition
     * @param buffer the buffer
     * @param pattern the pattern buffer
     * @return the position within the buffer of the first occurrence of the pattern
     *         buffer (in the 1st byte of the pattern)
     */
    public int indexOf(int startPosition, int end, ByteBuffer buffer, ByteBuffer pattern) {
        int patternLen = pattern.limit();
        int lastIndex = end - patternLen + 1;
        Label: for (int i = startPosition; i < lastIndex; i++) {
            if (buffer.get(i) == pattern.get(0)) {
                for (int j = 1; j < patternLen; j++) {
                    if (buffer.get(i + j) != pattern.get(j)) {
                        continue Label;
                    }
                }
                return i;
            }
        }
        return -1;
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
                logger.error(e);
            }
            ch.channel = createChannel();
        }
    }



    private ByteBuffer allocateBuffer() {
        return ByteBuffer.allocateDirect(BUFFER_SIZE).order(ByteOrder.BIG_ENDIAN);
    }


    /**
     * Convert a short to byte array including the leading zeros and using 1 byte per char
     * encode
     * 
     * @param s
     * @return
     */
    private byte[] shortToByteArray(int s) {
        byte[] r = new byte[5];
        int base = 10000;
        int tmp;
        for (short i = 0; i < 5; i++) {
            tmp = (s / base);
            r[i] = (byte) (tmp + '0');
            s -= tmp * base;
            base /= 10;
        }
        return r;
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
        ChannelPack pack = new ChannelPack(socketChannel, buffer, cassandra);
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
            logger.error(e);
        }
        return null;
    }
}
