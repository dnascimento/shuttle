package pt.inesc.proxy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import pt.inesc.proxy.save.Request;
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
    private static Logger logger = LogManager.getLogger("WorkerThread");

    private static final int FLUSH_PERIODICITY = 5;
    private int decrementToSave = FLUSH_PERIODICITY;

    /** time between attempt to flush to disk ms */
    private static final int BUFFER_SIZE = 512 * 1024;
    private ByteBuffer buffer = allocateBuffer();
    private final static Charset UTF8_CHARSET = Charset.forName("UTF-8");


    ByteBuffer CONNECTION_CLOSE = ByteBuffer.wrap("Connection: close".getBytes());
    ByteBuffer CONNECTION = ByteBuffer.wrap("Connection: ".getBytes());
    ByteBuffer KEEP_ALIVE = ByteBuffer.wrap("Connection: keep-alive".getBytes());
    ByteBuffer CONTENT_LENGTH = ByteBuffer.wrap("Content-Length: ".getBytes());
    ByteBuffer newLines = ByteBuffer.wrap(new byte[] { 13, 10, 13, 10 });
    ByteBuffer separator = ByteBuffer.wrap(new byte[] { 13, 10 });


    private final ThreadPool pool;
    private SelectionKey key;
    private final InetSocketAddress backendAddress;
    SocketChannel backendSocket = null;

    public LinkedList<Request> requests = new LinkedList<Request>();
    public LinkedList<Response> responses = new LinkedList<Response>();

    public ProxyWorker(ThreadPool pool, String remoteHost, int remotePort) {
        logger.info("New worker");
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
        // execution cycle
        while (true) {
            try {
                serveNewRequest(key, true);
                drainAndSend(key);
            } catch (Exception e) {
                logger.error("Execution", e);
            }

            buffer = ByteBuffer.allocate(BUFFER_SIZE);
            // Store data
            try {
                key.channel().close();
            } catch (Exception e) {
                logger.error("Close channel", e);
            }

            if (--decrementToSave == 0) {
                // TODO flushData();
            }
            connect();

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
    synchronized void serveNewRequest(SelectionKey key, boolean sleep) {
        if (sleep) {
            // Ready for more. Return to pool
            // Sleep and release object lock, wait for wake from serveNewRequest
            // Notify the selector that I will be available
            pool.returnWorker(this);

            if (key != null) {
                key.selector().wakeup();
            }

            try {
                this.wait();
            } catch (InterruptedException e) {
                logger.error("Sleep thread", e);
                interrupted();
            }
        } else {
            this.key = key;
            // Remove the flag of reading ready, it will be read
            key.interestOps(key.interestOps() & (~SelectionKey.OP_READ));
            this.notify(); // Awaken the thread
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
        SocketChannel frontendChannel = (SocketChannel) key.channel();
        long startTS = System.currentTimeMillis();
        System.out.println("New Req:" + startTS);
        ByteBuffer messageIdHeader = ByteBuffer.wrap(("\nId: " + startTS).getBytes());

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
                size = extractMessageTotalSize(lastSizeAttemp, buffer.position(), buffer);
                lastSizeAttemp = buffer.position() - CONTENT_LENGTH.capacity();
            }
        }
        buffer.flip();

        ByteBuffer request = ByteBuffer.allocate(buffer.limit()
                + messageIdHeader.capacity());
        int endOfFirstLine = indexOf(buffer, separator);
        int originalLimit = buffer.limit();
        if (endOfFirstLine == -1 && buffer.limit() == 0) {
            logger.warn("empty buffer");
            return;
        }

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
        addRequest(request, startTS);

        lastSizeAttemp = 0;
        size = -1;
        // Read Answer from server
        while (backendSocket.read(buffer) > 0
                || (size != -1 && buffer.position() != size)) {
            if (buffer.remaining() == 0) {
                resizeBuffer();
            }
            if (size == -1) {
                size = extractMessageTotalSize(lastSizeAttemp, buffer.position(), buffer);
                lastSizeAttemp = buffer.position() - CONTENT_LENGTH.capacity();
            }
        }

        // send anwser to client
        long endTS = System.currentTimeMillis();
        buffer.flip(); // make buffer readable
        toWrite = buffer.limit();
        written = 0;
        while ((written += frontendChannel.write(buffer)) < toWrite)
            ;

        addResponse(buffer, startTS, endTS);
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
        new Saver(requests, responses).save();
        requests = new LinkedList<Request>();
        responses = new LinkedList<Response>();
        decrementToSave = FLUSH_PERIODICITY;
    }

    /**
     * Exctract how long is all message.
     * 
     * @param lastSizeAttemp
     * @param end
     * @param buffer2
     * @return
     */
    private int extractMessageTotalSize(int start, int end, ByteBuffer buffer) {
        int pos = indexOf(start, end, buffer, CONTENT_LENGTH);
        if (pos == -1) {
            return -1;
        }
        int i = 0;
        byte b;
        List<Byte> lenght = new ArrayList<Byte>();
        while ((b = buffer.get(pos + 16 + i++)) != (byte) 13) {
            lenght.add(b);
        }
        int contentLenght = Integer.parseInt(decodeUTF8(lenght));
        contentLenght += indexOf(buffer, newLines);
        contentLenght += 4; // 4 newlines bytes
        return contentLenght;
    }


    private static void printContent(ByteBuffer buffer, int start, int end) {
        List<Byte> content = new ArrayList<Byte>();
        for (int i = start; i < end; i++) {
            content.add(buffer.get(i));
        }
        System.out.println(decodeUTF8(content));
    }

    public static void printContent(ByteBuffer buffer) {
        int start = buffer.position();
        int end = buffer.limit();
        printContent(buffer, start, end);
    }

    public static void println(ByteBuffer buffer) {
        int position = buffer.position();

        for (int i = position; i < buffer.limit(); i++) {
            System.out.print(Integer.toHexString(buffer.get(i)));
        }
        System.out.println("Limit: " + buffer.limit());
        System.out.println("Position" + buffer.position());
    }


    public int indexOf(ByteBuffer buffer, ByteBuffer pattern) {
        return indexOf(0, buffer.limit(), buffer, pattern);
    }

    /**
     * Returns the index within this buffer of the first occurrence of the specified
     * pattern buffer.
     * 
     * @param startPosition
     * @param buffer the buffer
     * @param pattern the pattern buffer
     * @return the position within the buffer of the first occurrence of the pattern
     *         buffer
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

    static String decodeUTF8(List<Byte> lenght) {
        byte[] lenghtValue = new byte[lenght.size()];
        int i = 0;
        for (byte b : lenght) {
            lenghtValue[i++] = b;
        }
        return new String(lenghtValue, UTF8_CHARSET);
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



}
