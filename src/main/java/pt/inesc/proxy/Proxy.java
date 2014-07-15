/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.StandardSocketOptions;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

public class Proxy {
    public static final int MY_PORT = 11100;
    public static int FRONTEND_PORT = 9000;
    public static int BACKEND_PORT = 8080;
    public static String BACKEND_HOST = "localhost";

    private static final int INIT_NUMBER_OF_THREADS = 2;
    private static final int MAX_NUMBER_OF_THREADS = 2;
    private final ThreadPool pool;
    private final int localPort;
    private static final Logger log = Logger.getLogger(Proxy.class.getName());

    public static Object lockBranchRestrain = new Object();
    public static byte[] branch = shortToByteArray(0);
    public static boolean restrain = false;
    public static long timeTravel = 0;

    public Proxy(int localPort, String remoteHost, int remotePort) throws IOException {
        log.setLevel(Level.ERROR);
        this.localPort = localPort;
        pool = new ThreadPool(INIT_NUMBER_OF_THREADS, MAX_NUMBER_OF_THREADS, remoteHost, remotePort);
        log.info("Proxy listen frontend: " + localPort + " backend: " + remotePort);
        new ServiceProxy(this).start();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        DOMConfigurator.configure("log4j.xml");
        if (args.length > 0) {
            if (args.length < 3) {
                log.error("usage: <frontend-port> <backend-port> <backend-address>");
                return;
            }
            FRONTEND_PORT = Integer.parseInt(args[0]);
            BACKEND_PORT = Integer.parseInt(args[1]);
            BACKEND_HOST = args[2];
        }


        new Proxy(FRONTEND_PORT, BACKEND_HOST, BACKEND_PORT).run();

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
            int n = selector.select(2000);
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
                        log.info("New socket");
                        // notify the selector about op_read
                        registerChannel(selector, channel, SelectionKey.OP_READ);
                    }
                    // The selector was waked to read?
                    if (key.isReadable()) {
                        readDataFromSocket(key);
                    }
                } catch (Exception e) {
                    log.error(e);
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
        // Remove the flag of reading ready, drop request or take it
        if (worker == null) {
            log.error("No worker available");
            return;
        }
        log.info("Readable by " + worker.getId());
        // Invoking this wakes up the worker thread, then returns
        worker.serveNewRequest(key, true, false);
    }

    private void registerChannel(Selector selector, SocketChannel channel, int opRead) throws IOException {
        if (channel == null) {
            return;
        }

        // set the new channel non-blooking
        channel.configureBlocking(false);
        channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
        // register with the selector to wake when ready to read
        channel.register(selector, opRead);
    }

    public void setBranchAndRestrain(short branch, boolean restrain) {
        log.info("new branch: " + branch + " restrain: " + restrain);
        byte[] b = null;
        if (branch != -1) {
            b = shortToByteArray(branch);
        }
        synchronized (Proxy.lockBranchRestrain) {
            if (b != null)
                Proxy.branch = b;
            Proxy.restrain = restrain;
        }
    }

    /**
     * Convert a short to byte array including the leading zeros and using 1 byte per char
     * encode
     * 
     * @param s
     * @return
     */
    private static byte[] shortToByteArray(int s) {
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


    public void timeTravel(long timeDelta) {
        String dateString = new SimpleDateFormat("H:m:S").format(new Date(timeDelta));
        log.info("traveling: " + dateString);
        synchronized (Proxy.lockBranchRestrain) {
            timeTravel = timeDelta;
        }
    }

    public void reset(short newBranch, long newCommit) {
        log.info("Proxy RESET to branch: " + newBranch);
        synchronized (Proxy.lockBranchRestrain) {
            branch = shortToByteArray(newBranch);
            timeTravel = 0;
        }

    }
}
