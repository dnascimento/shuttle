/*
 * 
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.proxy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import pt.inesc.BufferTools;
import pt.inesc.proxy.save.RequestResponsePair;
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
    private static Logger log = Logger.getLogger(ProxyWorker.class.getName());

    /** time between attempt to flush to disk ms */
    private final int FLUSH_PERIODICITY = 4000;

    private static final int N_BUFFERS = 4000;

    private final InetSocketAddress backendAddress;
    private AsynchronousSocketChannel frontendChannel = null;
    private long startTS;
    public ArrayList<RequestResponsePair> requestResponse = new ArrayList<RequestResponsePair>(FLUSH_PERIODICITY);
    private Saver saver = null;
    private final ByteBuffer headerBase = createBaseHeader();

    private final DirectBufferPool requestBuffers;
    private final DirectBufferPool responseBuffers;


    private boolean ignore;

    private boolean keepAlive;

    private SocketChannel backend;



    private static final String IGNORE_LIST_FILE = "proxy.ignore.txt";
    private static final ArrayList<Pattern> listOfIgnorePatterns = loadIgnoreList();
    private static final Integer BUFFER_SIZE = 6 * 1024; // 2K

    private static final long READ_TIMEOUT = 5000;
    private static final long WRITE_TIMEOUT = 1000;

    private static final boolean logging = true;
    private static final boolean stamping = true;

    private static final double MULTIPLICATION_FACTOR = getMultiplicationFactor();


    public ProxyWorker(InetSocketAddress remoteAddress) {
        if (logging) {
            saver = new Saver();
            saver.start();
        }
        backendAddress = remoteAddress;
        responseBuffers = new DirectBufferPool("responseBuffers", N_BUFFERS, BUFFER_SIZE);
        requestBuffers = new DirectBufferPool("requestBuffers", N_BUFFERS, BUFFER_SIZE);
        connect();
    }

    /**
     * Create connection to server
     */
    private void connect() {
        try {
            // Open socket to server and hold it
            if (backend != null) {
                backend.close();
            }
            backend = SocketChannel.open(backendAddress);
            backend.socket().setKeepAlive(true);
            backend.socket().setSoTimeout(10);
            // backend.configureBlocking(false);
        } catch (IOException e) {
            if (e.getMessage().equals("Connection refused")) {
                log.error("ERROR: Remote server is DOWN");
                throw new RuntimeException("ERROR: Remote server is DOWN");
            }
            log.warn("Connecting to real Server", e);
            connect();
        }
    }






    /**
     * The actual code which drains the channel associated with the given key.
     * This method assumes the key has been modified prior to invocation to turn off
     * selection interest in OP_READ. When this method completes it re-enables OP_READ and
     * calls wakeup() on the selector so the selector will resume watching this channel.
     * 
     * @param buffer
     * @return
     * @return
     * @return false if no request
     * @throws Exception
     */
    ByteBuffer drainFromClient(ByteBuffer clientRequestBuffer) throws Exception {
        int lastSizeAttemp = 0;
        int size = -1;
        int headerEnd = -1;
        int endOfFirstLine;

        ReqType reqType = null;

        // Loop while data is available
        try {
            do {
                if (reqType == null) {
                    reqType = getRequestType(clientRequestBuffer);
                }
                if (clientRequestBuffer.remaining() == 0) {
                    clientRequestBuffer = resizeRequestBuffer(clientRequestBuffer);
                }

                if (headerEnd == -1) {
                    // not found yet, try to find the header
                    headerEnd = BufferTools.getHeaderEnd(lastSizeAttemp, clientRequestBuffer.position(), clientRequestBuffer);
                    lastSizeAttemp = clientRequestBuffer.position() - BufferTools.CONTENT_LENGTH.capacity();
                    if (headerEnd == -1)
                        continue;
                    // header received
                    size = BufferTools.extractMessageTotalSize(0, headerEnd, clientRequestBuffer);
                    if (clientRequestBuffer.position() == size) {
                        break;
                    }

                }
                // if no content-length specified and header is complete
                if (size == -1 && BufferTools.headerIsComplete(clientRequestBuffer)) {
                    break;
                }

                if ((reqType == ReqType.PUT || reqType == ReqType.POST) && (clientRequestBuffer.position() == size)) {
                    break;
                }
            } while ((frontendChannel.read(clientRequestBuffer).get(READ_TIMEOUT, TimeUnit.MILLISECONDS) > 0));
        } catch (Exception e) {
            log.debug("Drain Client Exception", e);
            if (clientRequestBuffer.position() != 0) {
                log.error("Client sent some data", e);
            }
            throw e;
        }
        clientRequestBuffer.flip();
        endOfFirstLine = BufferTools.indexOf(clientRequestBuffer, BufferTools.SEPARATOR);
        keepAlive = BufferTools.isKeepAlive(clientRequestBuffer, endOfFirstLine);

        int originalLimit = clientRequestBuffer.limit();
        if (endOfFirstLine == -1 && originalLimit == 0) {
            log.debug("empty buffer - keep alive connection will be closed");
            throw new Exception("Empty client request");
        }

        ByteBuffer request;
        if (stamping) {
            startTS = getTimestamp();
            // log.info(Thread.currentThread().getId() + ": New Req:" + startTS);
            ByteBuffer messageIdHeader = generateHeaderFromBase(startTS);

            // allocate a new bytebuffer with exact size to copy
            request = requestBuffers.pop(clientRequestBuffer.limit() + messageIdHeader.capacity());

            clientRequestBuffer.limit(endOfFirstLine);
            // copy from buffer to request
            while (clientRequestBuffer.hasRemaining())
                request.put(clientRequestBuffer);

            while (messageIdHeader.hasRemaining())
                request.put(messageIdHeader);

            clientRequestBuffer.limit(originalLimit);
            while (clientRequestBuffer.hasRemaining())
                request.put(clientRequestBuffer);

            request.flip();
            request.rewind();
        } else {
            request = clientRequestBuffer;
        }

        ignore = matchIgnoreList(request, endOfFirstLine);

        return request;
    }

    public void sendToBackend(ByteBuffer request) throws IOException {
        // Write to backend
        int toWrite = request.limit();
        int written = 0;
        boolean reconnected = false;
        while (true) {
            request.rewind();
            try {
                while ((written += backend.write(request)) < toWrite)
                    ;
                return;
            } catch (IOException e) {
                if (!reconnected) {
                    connect();
                    reconnected = true;
                } else
                    throw e;
            }
        }
    }

    public ByteBuffer readFromBackend(ByteBuffer originalRequest) throws IOException {
        boolean reconnected = false;
        while (true) {
            int lastSizeAttemp = 0;
            int size = -1;
            int headerEnd = -1;
            boolean bufferWasResized = false;
            ByteBuffer responseBuffer = responseBuffers.pop();
            responseBuffer.clear();
            // ByteBuffer responseBuffer =
            // ByteBuffer.allocateDirect(BUFFER_SIZE).order(ByteOrder.BIG_ENDIAN);


            // Read Answer from server
            try {
                while (backend.read(responseBuffer) > 0 || (size != -1 && responseBuffer.position() != size)) {
                    if (responseBuffer.remaining() == 0) {
                        bufferWasResized = true;
                        responseBuffer = resizeResponseBuffer(responseBuffer);
                    }

                    if (headerEnd == -1) {
                        // not found yet, try to find the header
                        headerEnd = BufferTools.getHeaderEnd(lastSizeAttemp, responseBuffer.position(), responseBuffer);
                        lastSizeAttemp = responseBuffer.position() - BufferTools.CONTENT_LENGTH.capacity();
                        if (headerEnd == -1)
                            continue;
                        // header received
                        size = BufferTools.extractMessageTotalSize(0, headerEnd, responseBuffer);
                        if (BufferTools.is304(responseBuffer, headerEnd)) {
                            // TODO
                        }
                    }
                    if (size != -1) {
                        if (responseBuffer.position() == size) {
                            break;
                        }
                    } else {
                        if (BufferTools.lastChunk(responseBuffer)) {
                            break;
                        }
                    }
                }
                if (bufferWasResized) {
                    responseBuffers.voteBufferSize(responseBuffer.position());
                }
                keepAlive = BufferTools.isKeepAlive(responseBuffer, headerEnd) || BufferTools.isChunkedRequest(responseBuffer);
                return responseBuffer;
            } catch (SocketTimeoutException e1) {
                log.error("TIMEOUT!!!: request:\n " + BufferTools.printContent(originalRequest));


            } catch (IOException e) {
                if (reconnected) {
                    log.error("read from backend: reconnection failed", e);
                    // TODO send error to client
                    throw e;
                } else {
                    reconnected = true;
                    log.warn("position: " + responseBuffer.position());
                    // TODO connect();
                    // sendToBackend(originalRequest);
                    log.warn("read from backend, try to reconnect for rid: " + startTS, e);
                }
            }
        }
    }


    public void sendToClient(ByteBuffer originalRequest, ByteBuffer responseBuffer) throws Exception {
        // send anwser to client
        long endTS = getTimestamp();
        responseBuffer.flip(); // make buffer readable


        // TODO find the ID on header
        long id = BufferTools.getId(responseBuffer);


        int toWrite = responseBuffer.limit();
        int written = 0;

        while ((written += frontendChannel.write(responseBuffer).get(WRITE_TIMEOUT, TimeUnit.MILLISECONDS)) < toWrite)
            ;


        // frontendChannel.write(END_OF_MESSAGE).get();


        if (!ignore && logging)
            requestResponse.add(new RequestResponsePair(originalRequest, originalRequest, startTS, endTS));
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
        log.error("Unknown request type");
        return null;
    }

    /**
     * Flush the data to server before continue
     */
    private void flushData() {
        ArrayList<RequestResponsePair> previous = requestResponse;

        requestResponse = new ArrayList<RequestResponsePair>(FLUSH_PERIODICITY);
        saver.save(previous, requestBuffers, responseBuffers);
    }





    // ////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Add new request to Queue This is the serialization point.
     * 
     * @param request
     * @return the ID (number in queue)
     */



    private ByteBuffer resizeResponseBuffer(ByteBuffer buffer) {
        ByteBuffer newBuffer = resizeBuffer(buffer);
        return newBuffer;
    }

    private ByteBuffer resizeRequestBuffer(ByteBuffer buffer) {
        return resizeBuffer(buffer);
    }

    private ByteBuffer resizeBuffer(ByteBuffer buffer) {
        // duplicates the buffer size
        ByteBuffer newBuffer = ByteBuffer.allocateDirect(buffer.capacity() * 2);
        buffer.flip();
        // copy old buffer
        while (buffer.hasRemaining())
            newBuffer.put(buffer);
        return newBuffer;
    }




    private ByteBuffer generateHeaderFromBase(long startTS) {
        synchronized (Proxy.lockBranchRestrain) {
            byte[] ts = new Long(startTS + Proxy.timeTravel).toString().getBytes();
            headerBase.position(5);
            headerBase.put(ts);
            headerBase.position(25);
            headerBase.put(Proxy.branch);
            headerBase.position(34);
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
        ByteBuffer header = ByteBuffer.allocate(43);
        header.put("\nID: ".getBytes());
        header.put("0000000000000000".getBytes());
        header.put("\nB: ".getBytes());
        header.put(Proxy.branch); // 5bytes
        // not restraint
        header.put("\nR: f".getBytes());
        // not redo
        header.put("\nRedo: f".getBytes());
        return header;
    }


    private static ArrayList<Pattern> loadIgnoreList() {
        try {
            File file = new File(IGNORE_LIST_FILE);
            ArrayList<Pattern> listOfIgnorePatterns = new ArrayList<Pattern>();
            if (!file.exists()) {
                return listOfIgnorePatterns;
            }
            BufferedReader ri;
            ri = new BufferedReader(new InputStreamReader(new FileInputStream(file)));

            String line;
            while ((line = ri.readLine()) != null) {
                listOfIgnorePatterns.add(Pattern.compile(line));
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
        // url = url.replaceAll(" HTTP\\/\\d.\\d", "");
        url = url.substring(0, url.length() - 9);
        // could be improved using only file extensions and Bytes
        for (Pattern regex : listOfIgnorePatterns) {
            if (regex.matcher(url).matches()) {
                return true;
            }
        }
        return false;
    }

    public boolean handle(AsynchronousSocketChannel ch, ByteBuffer buffer) {
        frontendChannel = ch;
        try {
            ch.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
            ByteBuffer request = drainFromClient(buffer);
            sendToBackend(request);
            ByteBuffer response = readFromBackend(request);
            sendToClient(request, response);

            keepAlive = true;


            // if not logging, return buffers
            if (!logging) {
                if (stamping) {
                    request.clear();
                    requestBuffers.returnBuffer(request);
                }
                response.clear();
                responseBuffers.returnBuffer(response);
            }

            // if logging, flush
            if (logging && requestResponse.size() >= FLUSH_PERIODICITY) {
                // ch.close();
                flushData();
                // keepAlive = false;
            }

            if (!keepAlive) {
                closeFrontEndChannel();
                connect();
            }
        } catch (Exception e) {
            e.printStackTrace();
            closeFrontEndChannel();
            log.error(e);
        }

        return keepAlive;
    }

    private void closeFrontEndChannel() {
        try {
            frontendChannel.close();
            log.debug("Close connection");
        } catch (IOException e) {
            log.error(e);
        }
        frontendChannel = null;
    }

    private long getTimestamp() {
        return (long) (System.nanoTime() * MULTIPLICATION_FACTOR);
    }


    private static double getMultiplicationFactor() {
        long x = System.nanoTime();
        int digits = countDigits(x);
        // target is 16 digits
        double diff = Math.pow(10, 16 - digits);
        x = (long) (x * diff);
        if (countDigits(x) != 16) {
            throw new RuntimeException("The timestamp has not 16 digits");
        }
        return diff;
    }

    private static int countDigits(long v) {
        int i = 1;
        while (v >= 10) {
            v = v / 10;
            i++;
        }
        return i;
    }
}
