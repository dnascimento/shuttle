package pt.inesc.proxy.proxy;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A worker thread class which can drain channels and echo-back the input. Each instance
 * is constructed with a reference to the owning thread pool object. When started, the
 * thread loops forever waiting to be awakened to service the channel associated * with a
 * SelectionKey object. The worker is tasked by calling its serviceChannel() method with a
 * SelectionKey object. The serviceChannel() method stores * the key reference in the
 * thread object then calls notify() to wake it up. When the channel has been drained, the
 * worker * thread returns itself to its parent pool.
 */
public class WorkerThread extends
        Thread {
    private static Logger logger = LogManager.getLogger("ProxyWorker");

    private static final int PACKAGE_PER_FILE = 2;
    private ByteBuffer buffer = ByteBuffer.allocateDirect(16 * 1024)
                                          .order(ByteOrder.BIG_ENDIAN);
    private ThreadPool pool;
    private SelectionKey key;
    private InetSocketAddress remote;
    SocketChannel realSocket = null;
    Charset cs = Charset.forName("UTF-8");
    ByteBuffer connectionClose = ByteBuffer.wrap("Connection: close".getBytes());
    public static int id = 0;
    private static LinkedList<ByteBuffer> requests = new LinkedList<ByteBuffer>();
    private static Map<Integer, ByteBuffer> responses = new TreeMap<Integer, ByteBuffer>();
    private static Lock requestsMutex = new ReentrantLock();
    FileChannel debugChannel;
    RandomAccessFile file;


    public WorkerThread(ThreadPool pool, String remoteHost, int remotePort) {
        this.pool = pool;
        // TODO ABRIR SOCKET PARA O DESTINO
        remote = new InetSocketAddress(remoteHost, remotePort);

        try {
            File temp = new File("holy.txt");
            System.out.println(temp.getAbsolutePath());
            file = new RandomAccessFile(temp, "rw");
            debugChannel = file.getChannel();

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        connect();
    }

    private void connect() {
        try {
            // Open socket to server and hold it
            realSocket = SocketChannel.open(remote);
            realSocket.socket().setKeepAlive(true);
            realSocket.socket().setSoTimeout(0);
        } catch (IOException e) {
            System.out.println("Connecting to real Server" + e.getMessage());
        }
    }

    // Loop forever waiting for work to do
    @Override
    public synchronized void run() {
        System.out.println(getName() + " is ready");
        while (true) {
            try {
                // Sleep and release object lock
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
                // Clear interrupt status
                interrupted();
            }
            if (key == null) {
                continue; // just in case
            }

            try {
                // Juice
                drainAndSend(key);
            } catch (Exception e) {
                System.out.println("Caught '" + e + "' closing channel");
                e.printStackTrace();

                // Close channel and nudge selector
                try {
                    key.channel().close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                key.selector().wakeup();
            }
            key = null;
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
        key.interestOps(key.interestOps() & (~SelectionKey.OP_READ));
        notify(); // Awaken the thread
    }

    /**
     * The actual code which drains the channel associated with * the given key. This
     * method assumes the key has been modified prior to invocation to turn off selection
     * interest in OP_READ. When this method completes it re-enables OP_READ and calls
     * wakeup() on the selector so the selector will resume watching this channel.
     */

    void drainAndSend(SelectionKey key) throws Exception {
        SocketChannel channel = (SocketChannel) key.channel();
        int count;
        CharBuffer viewCharBuffer = buffer.asCharBuffer();
        buffer.clear();
        Boolean close = false;
        int closeIndex = 0;
        int id = -1;
        // TODO Corrigir os IDs pelas excepcoes (da valores estupidos)


        // Loop while data is available; channel is nonblocking
        while ((count = channel.read(buffer)) > 0) {
            buffer.flip(); // make buffer readable
            closeIndex = indexOf(buffer, connectionClose);
            if (closeIndex != -1) {
                close = true;
            }
            id = addRequest(clone(buffer));
            closeIndex = indexOf(buffer, connectionClose);
            if (closeIndex != -1) {
                close = true;
            }

            // Send the data; may not go all at once
            while (buffer.hasRemaining()) {

                // cs.decode(buffer).toString().getBytes();
                // buffer.flip();
                try {
                    realSocket.write(buffer);
                } catch (IOException e) {
                    connect();
                    realSocket.write(buffer);
                }
            }
            buffer.compact();
        }
        if (count < 0) {
            // Close channel on EOF; invalidates the key
            channel.close();
            return;
        }
        buffer.clear();
        while ((count = realSocket.read(buffer)) > 0) {
            // System.out.println("Reading");
            // buffer.flip(); // make buffer readable
            // System.out.println(cs.decode(buffer));
            buffer.flip(); // make buffer readable
            channel.write(buffer);
            addResponse(clone(buffer), id);
            buffer.compact();
            // TODO Write to File to Store (Assnyc)
        }

        // Store data
        if (close) {
            connect();
            channel.close();
        } else {
            // Resume interest in OP_READ
            key.interestOps(key.interestOps() | SelectionKey.OP_READ);
        }

        // Cycle the selector so this key is active again
        key.selector().wakeup();
    }

    private static void println(ByteBuffer bufferP) {
        for (int i = 0; i < bufferP.limit(); i++) {
            System.out.print(Integer.toHexString(bufferP.get(i)));
        }
        System.out.println("Limit: " + bufferP.limit());
        System.out.println("Position" + bufferP.position());
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




    public synchronized static void addResponse(ByteBuffer response, int id) {
        responses.put(id, response);
        if (responses.size() > PACKAGE_PER_FILE) {
            Map<Integer, ByteBuffer> responsesToSave = responses;
            responses = new HashMap<Integer, ByteBuffer>();

            requestsMutex.lock();
            LinkedList<ByteBuffer> requestsToSave = requests;
            requests = new LinkedList<ByteBuffer>();
            requestsMutex.unlock();

            new DataSaver(responsesToSave, id).start();
            new DataSaver(requestsToSave, id).start();
        }
    }



    /**
     * Add new request to Queue
     * 
     * @param request
     * @return the ID (number in queue)
     */
    public static int addRequest(ByteBuffer request) {
        requestsMutex.lock();
        // Exclusive zone
        int id = WorkerThread.id++;
        System.out.println("update");
        requests.add(request);
        requestsMutex.unlock();
        return id;
    }


    public static ByteBuffer clone(ByteBuffer original) {
        ByteBuffer clone = ByteBuffer.allocate(original.capacity());
        original.rewind();// copy from the beginning
        clone.put(original);
        original.rewind();
        clone.flip();
        return clone;
    }
}
