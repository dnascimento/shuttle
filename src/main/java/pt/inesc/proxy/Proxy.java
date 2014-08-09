/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

public class Proxy {
    public static final int MY_PORT = 11100;
    public static int FRONTEND_PORT = 9000;
    public static int BACKEND_PORT = 8080;
    public static String BACKEND_HOST = "localhost";

    private static final int NUMBER_OF_THREADS = 1;
    private final int localPort;
    private static final Logger log = Logger.getLogger(Proxy.class.getName());
    // Initial Operating System buffer size
    private static final Integer BUFFER_SIZE = 2 * 1024; // 4K
    private static final int N_BUFFERS = 50;

    public static Object lockBranchRestrain = new Object();
    public static byte[] branch = shortToByteArray(0);
    public static boolean restrain = false;
    public static long timeTravel = 0;


    private final AsynchronousServerSocketChannel asynchronousServerSocketChannel;
    private final ExecutorService pool;
    private final AsynchronousChannelGroup group;
    private final LinkedBlockingDeque<ProxyWorker> workers;
    private final DirectBufferPool buffers;

    public Proxy(int localPort, String remoteHost, int remotePort) throws IOException {
        this.localPort = localPort;

        new ServiceProxy(this).start();
        ProxyThreadFactory threadFactory = new ProxyThreadFactory();

        pool = new ThreadPoolExecutor(NUMBER_OF_THREADS, NUMBER_OF_THREADS, Long.MAX_VALUE, TimeUnit.MILLISECONDS,
                new LinkedBlockingDeque<Runnable>(), threadFactory);


        group = AsynchronousChannelGroup.withThreadPool(pool);

        asynchronousServerSocketChannel = AsynchronousServerSocketChannel.open(group);
        asynchronousServerSocketChannel.setOption(StandardSocketOptions.SO_RCVBUF, BUFFER_SIZE);

        InetSocketAddress backendAddress = new InetSocketAddress(remoteHost, remotePort);
        workers = createWorkersPool(backendAddress);

        buffers = new DirectBufferPool(N_BUFFERS, BUFFER_SIZE);


        log.info("Proxy listen frontend: " + localPort + " backend: " + remotePort);



    }

    private LinkedBlockingDeque<ProxyWorker> createWorkersPool(InetSocketAddress remoteAddress) {
        LinkedBlockingDeque<ProxyWorker> workers = new LinkedBlockingDeque<ProxyWorker>(NUMBER_OF_THREADS);
        for (int i = 0; i < NUMBER_OF_THREADS; i++) {
            workers.add(new ProxyWorker(remoteAddress));
        }

        return workers;
    }

    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
        DOMConfigurator.configure("log4j.xml");
        log.setLevel(Level.DEBUG);
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


    public void run() throws IOException, InterruptedException, ExecutionException {
        final AsynchronousServerSocketChannel listener = asynchronousServerSocketChannel.bind(new InetSocketAddress(localPort));
        listener.accept(workers, new CompletionHandler<AsynchronousSocketChannel, LinkedBlockingDeque<ProxyWorker>>() {
            @Override
            public void completed(AsynchronousSocketChannel ch, LinkedBlockingDeque<ProxyWorker> workersList) {
                // accept the next connection
                listener.accept(workersList, this);

                ByteBuffer buffer = buffers.pop();
                ch.read(buffer, ch, new ReadHandler(buffer, workersList));
            }

            @Override
            public void failed(Throwable exc, LinkedBlockingDeque<ProxyWorker> att) {
                // TODO
            }
        });
        group.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);

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
