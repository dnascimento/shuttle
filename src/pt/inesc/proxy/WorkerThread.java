package pt.inesc.proxy;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
    private static final int PACKAGE_PER_FILE = 3;
    private ByteBuffer buffer = ByteBuffer.allocateDirect(16 * 1024)
                                          .order(ByteOrder.BIG_ENDIAN);
    private ThreadPool pool;
    private SelectionKey key;
    private InetSocketAddress remote;
    SocketChannel realSocket = null;
    private final static Charset UTF8_CHARSET = Charset.forName("UTF-8");
    ByteBuffer connectionClose = ByteBuffer.wrap("Connection: close".getBytes());
    ByteBuffer connectionHeader = ByteBuffer.wrap("Connection: ".getBytes());
    ByteBuffer connectionAlive = ByteBuffer.wrap("Connection: keep-alive".getBytes());
    ByteBuffer contentLenght = ByteBuffer.wrap("Content-Length: ".getBytes());
    ByteBuffer newLines = ByteBuffer.wrap(new byte[] { 13, 10, 13, 10 });
    ByteBuffer separator = ByteBuffer.wrap(new byte[] { 13, 10 });
    public static AtomicInteger id = new AtomicInteger(0);
    private static TreeMap<Integer, ByteBuffer> requests = new TreeMap<Integer, ByteBuffer>();
    private static TreeMap<Integer, ByteBuffer> responses = new TreeMap<Integer, ByteBuffer>();
    private static Lock requestsMutex = new ReentrantLock();
    RandomAccessFile aFile;
    FileChannel debugChannel;


    public WorkerThread(ThreadPool pool, String remoteHost, int remotePort) {
        this.pool = pool;
        remote = new InetSocketAddress(remoteHost, remotePort);
        try {
            aFile = new RandomAccessFile("debug.txt", "rw");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        debugChannel = aFile.getChannel();
        connect();
    }

    private void connect() {
        try {
            // Open socket to server and hold it
            if (realSocket != null) {
                realSocket.close();
            }
            realSocket = SocketChannel.open(remote);
            realSocket.socket().setKeepAlive(true);
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
        int count = 0;
        int connectionHeaderIndex = -1;
        Boolean requestReceived = false;
        Boolean close = false;
        int id = 0;
        ByteBuffer messageIdHeader;

        buffer.clear();
        // Loop while data is available; channel is nonblocking
        while ((count = channel.read(buffer)) > 0) {
            id = WorkerThread.id.getAndIncrement();
            messageIdHeader = ByteBuffer.wrap(("Id: " + id).getBytes());
            buffer.flip(); // make buffer readable
            // TODO A request can be readed separated
            // TODO Tentar colocar keepalive a dar de vez
            requestReceived = true;

            // Add Via: msgId
            int endOfFirstLine = indexOf(buffer, separator);
            // connectionHeaderIndex = indexOf(buffer, connectionHeader);
            connectionHeaderIndex = indexOf(buffer, connectionClose);

            ByteBuffer firstLine = buffer.slice();
            firstLine.position(0).limit(endOfFirstLine);
            writeToRemote(firstLine);
            separator.rewind();
            writeToRemote(separator);
            writeToRemote(messageIdHeader);
            // separator.rewind();
            // writeToRemote(separator);
            // writeToRemote(connectionAlive);

            ByteBuffer rest = buffer.slice();
            rest.position(endOfFirstLine);


            if (connectionHeaderIndex != -1) {
                // Remove Close Connection or other Connection Detail
                // rest.limit(connectionHeaderIndex - 1);
                // ByteBuffer end = buffer.slice();
                // end.position(connectionHeaderIndex);
                // separator.rewind();
                // int lineEnd = indexOf(end, separator);
                // end.position(lineEnd + 1);
                // writeToRemote(rest);
                // writeToRemote(end);
                close = true;
                writeToRemote(rest);
            } else {
                writeToRemote(rest);
            }

            // TODO GET's que nao mudam os dados (sem parametros) ignorar
            addRequest(clone(buffer), id);
            buffer.compact();
        }

        if (count < 0 || !requestReceived) {
            // Close channel on EOF; invalidates the key
            channel.close();
            return;
        }


        int written = 0;
        int toWrite = 0;
        buffer.clear();
        while ((count = realSocket.read(buffer)) > 0) {
            buffer.flip(); // make buffer readable
            try {
                toWrite = extractMessageTotalSize(buffer);
            } catch (NumberFormatException e) {
                // Bad request message
                written = channel.write(buffer);
                addResponse(clone(buffer), id);
                break;
            }


            buffer.rewind();
            // TODO If I want extract the answer ID: idFiled
            // int resId = extractMessageIdSize(buffer);

            written = channel.write(buffer);
            addResponse(clone(buffer), id);
            if (written == toWrite) {
                break;
            }
            buffer.compact();
        }

        // Store data
        if (close) {
            channel.close();
            connect();
        } else {
            // Resume interest in OP_READ
            key.interestOps(key.interestOps() | SelectionKey.OP_READ);
        }
        // Cycle the selector so this key is active again
        key.selector().wakeup();
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
        System.out.println("Limit: " + buffer.limit());
        System.out.println("Position" + buffer.position());
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




    public synchronized static void addResponse(ByteBuffer response, int id) {
        responses.put(id, response);
        if (responses.size() > PACKAGE_PER_FILE) {
            TreeMap<Integer, ByteBuffer> responsesToSave = responses;
            responses = new TreeMap<Integer, ByteBuffer>();

            requestsMutex.lock();
            TreeMap<Integer, ByteBuffer> requestsToSave = requests;
            requests = new TreeMap<Integer, ByteBuffer>();
            requestsMutex.unlock();

            new DataSaver(responsesToSave, "res").start();
            new DataSaver(requestsToSave, "req").start();
        }
    }


    static String decodeUTF8(List<Byte> lenght) {
        byte[] lenghtValue = new byte[lenght.size()];
        int i = 0;
        for (byte b : lenght) {
            lenghtValue[i++] = b;
        }
        return new String(lenghtValue, UTF8_CHARSET);
    }

    /**
     * Add new request to Queue
     * 
     * @param request
     * @return the ID (number in queue)
     */
    public static void addRequest(ByteBuffer request, int id) {
        requestsMutex.lock();
        // Exclusive zone
        requests.put(id, request);
        requestsMutex.unlock();
    }

    public void writeToRemote(ByteBuffer buffer) {
        try {
            // Debug to File
            // debugChannel.write(buffer);
            realSocket.write(buffer);
        } catch (IOException e) {
            connect();
            try {
                realSocket.write(buffer);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
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
