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

    ByteBuffer connectionClose = ByteBuffer.wrap("Connection: close".getBytes());
    ByteBuffer connectionHeader = ByteBuffer.wrap("Connection: ".getBytes());
    ByteBuffer connectionAlive = ByteBuffer.wrap("Connection: keep-alive".getBytes());
    ByteBuffer contentLenght = ByteBuffer.wrap("Content-Length: ".getBytes());
    ByteBuffer newLines = ByteBuffer.wrap(new byte[] { 13, 10, 13, 10 });
    ByteBuffer separator = ByteBuffer.wrap(new byte[] { 13, 10 });


    private final ThreadPool pool;
    private SelectionKey key;
    private final InetSocketAddress backendAddress;
    SocketChannel backendSocket = null;



    public LinkedList<Request> requests = new LinkedList<Request>();
    public LinkedList<Response> responses = new LinkedList<Response>();

    public ProxyWorker(ThreadPool pool, String remoteHost, int remotePort) {
        System.out.println("New worker");
        this.pool = pool;
        backendAddress = new InetSocketAddress(remoteHost, remotePort);
        connect();
    }

    /**
     * Create connection to server
     */
    private void connect() {
        // System.out.print("Connect to backend");
        try {
            // Open socket to server and hold it
            if (backendSocket != null) {
                backendSocket.close();
            }
            backendSocket = SocketChannel.open(backendAddress);
            // backendSocket.socket().setKeepAlive(true);
        } catch (IOException e) {
            if (e.getMessage().equals("Connection refused")) {
                throw new RuntimeException("ERROR: Remote server is DOWN");
            }
            System.out.println("Connecting to real Server" + e.getMessage());
            connect();
        }
    }

    // Loop forever waiting for work to do
    @Override
    public synchronized void run() {
        while (true) {
            try {
                // Sleep and release object lock
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
                // Clear interrupt status
                interrupted();
            }
            try {
                drainAndSend(key);
                key.channel().close();
                key.selector().wakeup();
            } catch (Exception e) {
                e.printStackTrace();
                logger.error(e);
            }
            // Done. Ready for more. Return to pool
            pool.returnWorker(this);
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
    synchronized void serviceChannel(SelectionKey key) {
        this.key = key;
        // TODO test without checking this
        // key.interestOps(key.interestOps() & (~SelectionKey.OP_READ));
        notify(); // Awaken the thread
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
        Boolean close = true;
        long startTS = System.currentTimeMillis();
        System.out.println("New Req:" + startTS);
        ByteBuffer messageIdHeader = ByteBuffer.wrap(("\nId: " + startTS).getBytes());
        buffer.clear();
        // Loop while data is available; channel is nonblocking
        while (frontendChannel.read(buffer) > 0) {
            if (buffer.remaining() == 0) {
                resizeBuffer();
            }
        }
        buffer.flip(); // make buffer readable

        ByteBuffer request = ByteBuffer.allocate(buffer.limit()
                + messageIdHeader.capacity());
        int endOfFirstLine = indexOf(buffer, separator);
        int originalLimit = buffer.limit();
        System.out.println("end" + endOfFirstLine);
        buffer.limit(endOfFirstLine);
        // copy buffer to request
        while (buffer.hasRemaining())
            request.put(buffer);

        while (messageIdHeader.hasRemaining())
            request.put(messageIdHeader);

        buffer.limit(originalLimit);
        while (buffer.hasRemaining())
            request.put(buffer);

        // request is generated with id, send
        request.rewind();
        backendSocket.write(request);
        buffer.clear();
        addRequest(request, startTS);

        // Answer
        while (backendSocket.read(buffer) > 0) {
            if (buffer.remaining() == 0) {
                resizeBuffer();
            } else {
                break;
            }
            // keep how many read and if check the size
            // if
            // TODO should be a stream and detect the end based on header
        }
        long endTS = System.currentTimeMillis();
        buffer.flip(); // make buffer readable
        frontendChannel.write(buffer);

        // TODO Testar enviar directo cassandra
        addResponse(buffer, startTS, endTS);
        buffer = ByteBuffer.allocate(BUFFER_SIZE);

        // Store data
        if (close) {
            frontendChannel.close();
            connect(); // TODO Manter sessao
        } else {
            // Resume interest in OP_READ
            key.interestOps(key.interestOps() | SelectionKey.OP_READ);
        }
        // Handle flush
        if (--decrementToSave == 0) {
            flushData();
            decrementToSave = FLUSH_PERIODICITY;
        }


        // Cycle the selector so this key is active again
        key.selector().wakeup();
    }

    /**
     * Flush the data to server before continue
     */
    private void flushData() {
        new Saver(requests, responses).save();
        requests = new LinkedList<Request>();
        responses = new LinkedList<Response>();
    }

    /**
     * Exctract how long is all message.
     * 
     * @param buffer2
     * @return
     */
    private int extractMessageTotalSize(ByteBuffer buffer) {
        int pos = indexOf(buffer, contentLenght);
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

    public static void printContent(ByteBuffer buffer) {
        int position = buffer.position();
        List<Byte> content = new ArrayList<Byte>();
        for (int i = position; i < buffer.limit(); i++) {
            content.add(buffer.get(i));
        }
        System.out.println(decodeUTF8(content));
        buffer.rewind();
    }

    public static void println(ByteBuffer buffer) {
        int position = buffer.position();

        for (int i = position; i < buffer.limit(); i++) {
            System.out.print(Integer.toHexString(buffer.get(i)));
        }
        System.out.println("Limit: " + buffer.limit());
        System.out.println("Position" + buffer.position());
    }

    /**
     * Returns the index within this buffer of the first occurrence of the specified
     * pattern buffer.
     * 
     * @param buffer the buffer
     * @param pattern the pattern buffer
     * @return the position within the buffer of the first occurrence of the pattern
     *         buffer
     */
    public int indexOf(ByteBuffer buffer, ByteBuffer pattern) {
        pattern.rewind();
        int patternPos = pattern.position();
        int patternLen = pattern.remaining();
        int lastIndex = buffer.limit() - patternLen + 1;
        Label: for (int i = buffer.position(); i < lastIndex; i++) {
            for (int j = 0; j < patternLen; j++) {
                if (buffer.get(i + j) != pattern.get(patternPos + j)) {
                    continue Label;
                }
            }
            return i;
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





    private WritableByteChannel getDebugChannel() {
        File temp = new File("debug.txt");
        temp.delete();
        temp = new File("debug.txt");
        RandomAccessFile file;
        try {
            return new RandomAccessFile(temp, "rw").getChannel();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return backendSocket;
    }

    private ByteBuffer allocateBuffer() {
        return ByteBuffer.allocateDirect(BUFFER_SIZE).order(ByteOrder.BIG_ENDIAN);
    }

    private void resizeBuffer() {
        ByteBuffer newBuffer = ByteBuffer.allocate(buffer.capacity() * 2);
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
