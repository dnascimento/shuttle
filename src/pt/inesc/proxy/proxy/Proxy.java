package pt.inesc.proxy.proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class Proxy {
    private static final int MAX_THREADS = 20;
    private ThreadPool pool;
    private final int localPort;


    public Proxy(int localPort, String remoteHost, int remotePort) {
        this.localPort = localPort;
        pool = new ThreadPool(MAX_THREADS, remoteHost, remotePort);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        new Proxy(9000, "localhost", 8080).run();
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
                SelectionKey key = it.next();

                // New connection?
                if (key.isAcceptable()) {
                    ServerSocketChannel server = (ServerSocketChannel) key.channel();
                    SocketChannel channel = server.accept();
                    registerChannel(selector, channel, SelectionKey.OP_READ);
                    // TODO check if throws rradable
                }

                if (key.isReadable()) {
                    readDataFromSocket(key);
                }

                // Remove the key
                it.remove();
            }
        }
    }


    /**
     * Sample data handler method for a channel with data ready to read. * @param key A
     * SelectionKey object associated with a channel determined by the selector to be
     * ready for reading. If the channel returns an EOF condition, it is closed here,
     * which automatically invalidates the associated key. The selector will then
     * de-register the channel on the next select call.
     * 
     * @throws IOException
     */
    private void readDataFromSocket(SelectionKey key) throws IOException {
        WorkerThread worker = pool.getWorker();
        if (worker == null) {
            return;
        }
        // Invoking this wakes up the worker thread, then returns
        worker.serviceChannel(key);
    }

    private void registerChannel(Selector selector, SocketChannel channel, int opRead) throws IOException {

        if (channel == null) {
            return;
        }

        // set the new channel non-blooking
        channel.configureBlocking(false);

        // register with the selector
        channel.register(selector, opRead);
    }
}
