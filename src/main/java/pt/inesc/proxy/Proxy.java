package pt.inesc.proxy;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

public class Proxy {
    private static final int INIT_NUMBER_OF_THREADS = 1;
    private static final int MAX_NUMBER_OF_THREADS = 20;
    private final ThreadPool pool;
    private final int localPort;
    private final Logger log = LogManager.getLogger("Proxy");


    public Proxy(int localPort, String remoteHost, int remotePort) {
        this.localPort = localPort;
        pool = new ThreadPool(INIT_NUMBER_OF_THREADS, MAX_NUMBER_OF_THREADS, remoteHost,
                remotePort);
        log.info("Proxy listen frontend: " + localPort + " backend: " + remotePort);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        DOMConfigurator.configure("log4j.xml");
        new File("./requests/").delete();
        new File("./requests/").mkdir();
        new Proxy(9000, "", 80).run();

    }


    public void run() throws IOException {
        // Allocate an unbound server socket channel
        ServerSocketChannel ssc = ServerSocketChannel.open();

        // Get the associated ServerSocket to bind it with
        ServerSocket serverSocket = ssc.socket();

        // Create a new Selector for use below
        Selector selector = Selector.open();

        // Set the port the server channel will listen to
        serverSocket.bind(new InetSocketAddress(localPort));

        // Set nonblocking mode for the listening socket
        ssc.configureBlocking(false);

        // On socket accept, the selector is wake
        ssc.register(selector, SelectionKey.OP_ACCEPT);

        while (true) {
            // This may block for a long time. Upon returning, the
            // selected set contains keys of the ready channels.
            int n = selector.select();
            if (n == 0) {
                continue;
            }

            // Get an iterator over the set of selected keys
            Iterator<SelectionKey> it = selector.selectedKeys().iterator();

            while (it.hasNext()) {
                try {
                    SelectionKey key = it.next();
                    // New connection?
                    if (key.isAcceptable()) {
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        SocketChannel channel = server.accept();
                        System.out.println("New socket");
                        registerChannel(selector, channel, SelectionKey.OP_READ);
                    }
                    // The selector was waked to read?
                    if (key.isReadable()) {
                        readDataFromSocket(key);
                    }
                } catch (Exception e) {
                    // TODO
                    e.printStackTrace();
                } finally {
                    // Remove the key
                    it.remove();
                }
            }
        }
    }


    /**
     * Sample data handler method for a channel with data ready to read.
     * 
     * @param key A SelectionKey object associated with a channel determined by the
     *            selector to be ready for reading. If the channel returns an EOF
     *            condition, it is closed here, which automatically invalidates the
     *            associated key. The selector will then de-register the channel on the
     *            next select call.
     * @throws IOException
     */
    private void readDataFromSocket(SelectionKey key) throws IOException {
        ProxyWorker worker = pool.getWorker();
        if (worker == null) {
            System.out.println("No worker available");
            return;
        }
        // Remove the flag of reading ready, it will be read
        key.interestOps(key.interestOps() & (~SelectionKey.OP_READ));
        System.out.println("Readable by " + worker.getId());
        // Invoking this wakes up the worker thread, then returns
        worker.serveNewRequest(key, false);
    }

    private void registerChannel(Selector selector, SocketChannel channel, int opRead) throws IOException {
        if (channel == null) {
            return;
        }

        // set the new channel non-blooking
        channel.configureBlocking(false);

        // register with the selector to wake when ready to read
        channel.register(selector, opRead);
    }
}
