/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.proxy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.LinkedList;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import pt.inesc.BufferTools;
import pt.inesc.proxy.save.Request;
import pt.inesc.proxy.save.RequestResponseListPair;
import pt.inesc.proxy.save.Response;
import pt.inesc.proxy.save.Saver;


/**
 * A worker thread class which can drain channels and echo-back the input. Each instance
 * is constructed with a reference to the owning thread pool object. When started, the
 * thread loops forever waiting to be awakened to service the channel associated * with a
 * SelectionKey object. The worker is tasked by calling its serviceChannel() method with a
 * SelectionKey object. The serviceChannel() method stores * the key reference in the
 * thread object then calls notify() to wake it up. When the channel has been drained, the
 * worker * thread returns itself to its parent pool.
 */
public class ProxyWorker extends
        Thread {
    private static Logger logger = LogManager.getLogger(ProxyWorker.class.getName());
    private int countWait = 0;

    private final int FLUSH_PERIODICITY = 1;
    /** time between attempt to flush to disk ms */
    private final int BUFFER_SIZE = 2048;

    private ByteBuffer buffer = allocateBuffer();
    private final ThreadPool pool;
    private int decrementToSave = FLUSH_PERIODICITY;
    private SelectionKey key;
    private final InetSocketAddress backendAddress;
    private SocketChannel backendSocket = null;
    private SocketChannel frontendChannel = null;
    private long startTS;
    private boolean ignore;
    public LinkedList<Request> requests = new LinkedList<Request>();
    public LinkedList<Response> responses = new LinkedList<Response>();
    private final Saver saver;
    private final ByteBuffer headerBase = createBaseHeader();
    private static final String IGNORE_LIST_FILE = "proxy.ignore.txt";
    private static final ArrayList<String> listOfIgnorePatterns = loadIgnoreList();

    public ProxyWorker(ThreadPool pool, String remoteHost, int remotePort) {
        logger.info("New worker: " + this.getId());
        logger.setLevel(Level.ERROR);
        saver = new Saver();
        saver.start();
        this.pool = pool;
        backendAddress = new InetSocketAddress(remoteHost, remotePort);
        connect();
    }

    /**
     * Create connection to server
     */
    private void connect() {
        try {
            // Open socket to server and hold it
            if (backendSocket != null) {
                backendSocket.close();
            }
            backendSocket = SocketChannel.open(backendAddress);
            backendSocket.socket().setKeepAlive(true);

        } catch (IOException e) {
            if (e.getMessage().equals("Connection refused")) {
                throw new RuntimeException("ERROR: Remote server is DOWN");
            }
            logger.warn("Connecting to real Server", e);
            connect();
        }
    }

    // Loop forever waiting for work to do
    @Override
    public synchronized void run() {
        boolean firstTime = true;
        // execution cycle
        while (true) {
            try {
                serveNewRequest(key, false, firstTime);
                firstTime = false;
                drainAndSend(key);
            } catch (Exception e) {
                logger.error("Execution", e);
                connect();
            }

            buffer = ByteBuffer.allocate(BUFFER_SIZE);
            // Force to close every connection
            // try {
            // key.channel().close();
            // } catch (Exception e) {
            // logger.error("Close channel", e);
            // }
            if (key.channel().isOpen()) {
                // resume interest to keep the connection open
                key.interestOps(key.interestOps() | SelectionKey.OP_READ);
            }
            // Store data
            if (--decrementToSave == 0) {
                flushData();
            }
            // connect();
        }
    }



    /**
     * Called to initiate a unit of work by this worker thread * on the provided
     * SelectionKey object. This method is synchronized, as is the run() method, so only
     * one key can be serviced at a given time. Before waking the worker thread, and
     * before returning to the main selection loop, this key's interest set is * updated
     * to remove OP_READ. This will cause the selector * to ignore read-readiness for this
     * channel while the worker thread is servicing it.
     */
    synchronized void serveNewRequest(SelectionKey key, boolean notification, boolean firstTime) {
        if (notification) {
            this.key = key;
            // // Remove the flag of reading ready, it will be read
            key.interestOps(key.interestOps() & (~SelectionKey.OP_READ));
            logger.info("Worker" + this.getId() + " notify start");
            logger.info("How many wait are waiting before notify? " + countWait);
            this.notify(); // Awaken the thread
            logger.info("Worker" + this.getId() + " notify end");
        } else {
            // Ready for more. Return to pool
            // Sleep and release object lock, wait for wake from serveNewRequest
            // Notify the selector that I will be available
            if (key != null && !firstTime) {
                pool.returnWorker(this);
                logger.info("selector wake");
                key.selector().wakeup();
            }
            if (key != null && firstTime) {
                return;
            }

            try {
                logger.info("Worker" + Thread.currentThread().getId() + " will wait");
                logger.info("How many wait before " + Thread.currentThread().getId() + "sleep? " + countWait);
                countWait++;
                this.wait();
                countWait--;
                logger.info("Worker" + Thread.currentThread().getId() + " wait end");

            } catch (InterruptedException e) {
                logger.error("Sleep thread", e);
                interrupted();
            }
        }
    }

    /**
     * The actual code which drains the channel associated with the given key.
     * This method assumes the key has been modified prior to invocation to turn off
     * selection interest in OP_READ. When this method completes it re-enables OP_READ and
     * calls wakeup() on the selector so the selector will resume watching this channel.
     * 
     * @throws IOException
     */
    void drainAndSend(SelectionKey key) throws IOException {
        frontendChannel = (SocketChannel) key.channel();
        int lastSizeAttemp = 0;
        int size = -1;
        ReqType reqType = null;
        // Loop while data is available; channel is nonblocking
        while ((frontendChannel.read(buffer) > 0)
                || ((reqType == ReqType.PUT || reqType == ReqType.POST) && (buffer.position() != size))) {
            if (reqType == null) {
                reqType = getRequestType(buffer);
            }
            if (buffer.remaining() == 0) {
                resizeBuffer();
            }
            if ((reqType == ReqType.PUT || reqType == ReqType.POST) && (size == -1)) {
                size = BufferTools.extractMessageTotalSize(lastSizeAttemp, buffer.position(), buffer);
                lastSizeAttemp = buffer.position() - BufferTools.CONTENT_LENGTH.capacity();
                if (buffer.position() == size) {
                    break;
                }
            }
        }
        buffer.flip();
        sendToServer(buffer);
    }


    public void sendToServer(ByteBuffer buffer) throws IOException {
        int endOfFirstLine = BufferTools.indexOf(buffer, BufferTools.SEPARATOR);
        int originalLimit = buffer.limit();
        if (endOfFirstLine == -1 && originalLimit == 0) {
            logger.info("empty buffer - keep alive connection will be closed");
            key.channel().close();
            return;
        }

        startTS = System.currentTimeMillis();
        logger.info(Thread.currentThread().getId() + ": New Req:" + startTS);
        ByteBuffer messageIdHeader = generateHeaderFromBase(startTS);
        ByteBuffer request = ByteBuffer.allocate(buffer.limit() + messageIdHeader.capacity());


        buffer.limit(endOfFirstLine);
        // copy from buffer to request
        while (buffer.hasRemaining())
            request.put(buffer);

        while (messageIdHeader.hasRemaining())
            request.put(messageIdHeader);

        buffer.limit(originalLimit);
        while (buffer.hasRemaining())
            request.put(buffer);

        // request is generated with id, send
        request.rewind();
        // Write to backend
        int toWrite = request.limit();
        int written = 0;
        while ((written += backendSocket.write(request)) < toWrite)
            ;

        buffer.clear();
        boolean ignore = matchIgnoreList(request, endOfFirstLine);
        if (!ignore)
            addRequest(request, startTS);
        readFromBackend();
    }

    public void readFromBackend() throws IOException {
        int lastSizeAttemp = 0;
        int size = -1;
        int headerEnd = -1;
        // Read Answer from server
        while (backendSocket.read(buffer) > 0 || (size != -1 && buffer.position() != size)) {
            if (buffer.remaining() == 0) {
                resizeBuffer();
            }
            if (headerEnd == -1) {
                // not found yet, try to find the header
                headerEnd = BufferTools.indexOf(lastSizeAttemp, buffer.position(), buffer, BufferTools.NEW_LINES);
                lastSizeAttemp = buffer.position() - BufferTools.CONTENT_LENGTH.capacity();
                if (headerEnd == -1)
                    continue;
                // header received
                size = BufferTools.extractMessageTotalSize(0, headerEnd, buffer);
            }
            if (size != -1) {
                if (buffer.position() == size) {
                    break;
                }
            } else {
                if (BufferTools.lastChunk(buffer)) {
                    break;
                }
            }
        }
        sendToClient(startTS, frontendChannel);
    }

    public void sendToClient(long startTS, SocketChannel frontendChannel) throws IOException {
        // send anwser to client
        long endTS = System.currentTimeMillis();
        buffer.flip(); // make buffer readable
        int toWrite = buffer.limit();
        int written = 0;
        while ((written += frontendChannel.write(buffer)) < toWrite)
            ;

        if (!ignore)
            addResponse(buffer, startTS, endTS);

        logger.info("Request done");
    }



    private ReqType getRequestType(ByteBuffer buffer) {
        byte letter = buffer.get(0);
        switch (letter) {
        case 71:
            return ReqType.GET;
        case 68:
            return ReqType.DELETE;
        case 80:
            if (buffer.get(1) == 79) {
                return ReqType.POST;
            } else {
                return ReqType.PUT;
            }
        default:
            break;
        }
        logger.error("Unknown request type");
        return null;
    }

    /**
     * Flush the data to server before continue
     */
    private void flushData() {
        RequestResponseListPair pair = new RequestResponseListPair(requests, responses);
        requests = new LinkedList<Request>();
        responses = new LinkedList<Response>();
        saver.save(pair);
        decrementToSave = FLUSH_PERIODICITY;
    }






    // ////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Add new request to Queue This is the serialization point.
     * 
     * @param request
     * @return the ID (number in queue)
     */
    public void addRequest(ByteBuffer request, long start) {
        requests.add(new Request(request, start));
    }

    public void addResponse(ByteBuffer response, long start, long end) {
        responses.add(new Response(response, start, end));
    }



    @SuppressWarnings("unused")
    private WritableByteChannel getDebugChannel() {
        File temp = new File("debug.txt");
        temp.delete();
        temp = new File("debug.txt");
        RandomAccessFile file;
        try {
            return new RandomAccessFile(temp, "rw").getChannel();
        } catch (FileNotFoundException e) {
            logger.error("Debug channel", e);
        }
        return backendSocket;
    }

    private ByteBuffer allocateBuffer() {
        return ByteBuffer.allocateDirect(BUFFER_SIZE).order(ByteOrder.BIG_ENDIAN);
    }

    private void resizeBuffer() {
        ByteBuffer newBuffer = ByteBuffer.allocate(buffer.capacity() * 2);
        buffer.flip();
        // copy old buffer
        while (buffer.hasRemaining())
            newBuffer.put(buffer);
        buffer = newBuffer;
    }


    public LinkedList<Request> getAndResetRequestList() {
        if (requests.size() != 0) {
            LinkedList<Request> tmp = requests;
            requests = new LinkedList<Request>();
            return tmp;
        }
        return null;
    }

    public LinkedList<Response> getAndResetResponseList() {
        if (responses.size() != 0) {
            LinkedList<Response> tmp = responses;
            responses = new LinkedList<Response>();
            return tmp;
        }
        return null;
    }

    private ByteBuffer generateHeaderFromBase(long startTS) {
        synchronized (Proxy.lockBranchRestrain) {
            byte[] ts = new Long(startTS + Proxy.timeTravel).toString().getBytes();
            headerBase.position(5);
            headerBase.put(ts);
            headerBase.position(22);
            headerBase.put(Proxy.branch);
            headerBase.position(31);
            if (Proxy.restrain) {
                headerBase.put((byte) 't');
            } else {
                headerBase.put((byte) 'f');
            }
        }
        headerBase.position(0);
        return headerBase;
    }

    private ByteBuffer createBaseHeader() {
        ByteBuffer header = ByteBuffer.allocate(40);
        header.put("\nID: ".getBytes());
        header.put("0000000000000".getBytes());
        header.put("\nB: ".getBytes());
        header.put(Proxy.branch); // 5bytes
        header.put("\nR: f".getBytes());
        header.put("\nRedo: f".getBytes());
        return header;
    }


    private static ArrayList<String> loadIgnoreList() {
        try {
            File file = new File(IGNORE_LIST_FILE);
            ArrayList<String> listOfIgnorePatterns = new ArrayList<String>();
            if (!file.exists()) {
                return listOfIgnorePatterns;
            }
            BufferedReader ri;
            ri = new BufferedReader(new InputStreamReader(new FileInputStream(file)));

            String line;
            while ((line = ri.readLine()) != null) {
                listOfIgnorePatterns.add(line);
            }
            ri.close();
            return listOfIgnorePatterns;
        } catch (IOException e) {
            return listOfIgnorePatterns;
        }
    }

    /**
     * Check if the url matches one of provided regexs
     * 
     * @param header
     * @param endOfFirstLine
     * @return
     */
    private boolean matchIgnoreList(ByteBuffer header, int endOfFirstLine) {
        String url = BufferTools.printContent(header, 0, endOfFirstLine);
        url = url.replaceAll(" HTTP\\/\\d.\\d", "");
        for (String regex : listOfIgnorePatterns) {
            if (url.matches(regex)) {
                return true;
            }
        }
        return false;
    }
}
