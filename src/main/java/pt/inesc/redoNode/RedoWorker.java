package pt.inesc.redoNode;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.util.Date;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import pt.inesc.proxy.save.CassandraClient;
import pt.inesc.redoNode.cookies.CookieMan;

public class RedoWorker
        implements Runnable {
    private static Logger logger = LogManager.getLogger("RedoWorker");

    private final InetSocketAddress remoteHost;
    private final int start;
    private final int end;
    protected SocketChannel backendSocket = null;
    public static CookieMan cookieManager = new CookieMan();
    private static final int BUFFER_SIZE = 512 * 1024;
    private ByteBuffer buffer;

    public RedoWorker(int start, int end, String remoteHostname, int remotePort) throws IOException {
        super();
        remoteHost = new InetSocketAddress(InetAddress.getByName(remoteHostname),
                remotePort);
        this.start = start;
        this.end = end;
        logger.info("New Worker");
        connect();
    }

    private void connect() throws IOException {
        // Open socket to server and hold it
        backendSocket = SocketChannel.open(remoteHost);

    }

    public void run() {
        System.out.println("time:" + new Date().getTime());
        CassandraClient cassandra = CassandraClient.getInstance();

        for (int reqID = start; reqID <= end; reqID++) {
            buffer = allocateBuffer();
            ByteBuffer request = cassandra.getRequest(reqID);
            if (request == null)
                break;
            try {
                backendSocket.write(request);
                ByteBuffer originalResponse = cassandra.getResponse(reqID);
                // TODO update cookies
                while (backendSocket.read(buffer) > 0) {
                    if (buffer.remaining() == 0) {
                        resizeBuffer();
                    }
                }
                buffer.flip(); // make buffer readable
                buffer.rewind();
                if (originalResponse != null) {
                    while (originalResponse.get() == buffer.get())
                        ;
                    // TODO Fix to compare correctly (cookies)
                    System.out.println(originalResponse.remaining() == 0
                            && buffer.remaining() == 0);
                }
                backendSocket.close();
                connect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        System.out.println("time:" + new Date().getTime());
    }


    private ByteBuffer allocateBuffer() {
        return ByteBuffer.allocateDirect(BUFFER_SIZE).order(ByteOrder.BIG_ENDIAN);
    }

    private void resizeBuffer() {
        ByteBuffer newBuffer = ByteBuffer.allocate(buffer.capacity() * 2);
        newBuffer.put(buffer);
        buffer = newBuffer;
    }
}
