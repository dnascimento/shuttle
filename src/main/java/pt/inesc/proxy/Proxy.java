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

import pt.inesc.BufferTools;
import pt.inesc.SharedProperties;

public class Proxy {
    private static final int NUMBER_OF_THREADS = 10;
    private final int localPort;
    private static final Logger log = Logger.getLogger(Proxy.class.getName());
    // Initial Operating System buffer size
    private static final Integer BUFFER_SIZE = 3 * 1024; // 3K
    private static final int N_BUFFERS = 10000;
    protected static final long READ_TIMEOUT = 1000;
    protected static final long ACCEPT_TIMEOUT = 1000;

    public static Object lockBranchRestrain = new Object();
    public static byte[] branch = BufferTools.shortToByteArray(0);
    public static boolean restrain = false;
    public static long timeTravel = 0;


    private final AsynchronousServerSocketChannel asynchronousServerSocketChannel;
    private final ExecutorService pool;
    private final AsynchronousChannelGroup group;
    private final LinkedBlockingDeque<ProxyWorker> workers;
    private final DirectBufferPool buffers;

    public Proxy(int frontendPort, String remoteHost, int remotePort) throws IOException {
        this.localPort = frontendPort;
        InetSocketAddress backendAddress = new InetSocketAddress(remoteHost, remotePort);


        new ServiceProxy(this).start();
        ProxyThreadFactory threadFactory = new ProxyThreadFactory();

        pool = new ThreadPoolExecutor(NUMBER_OF_THREADS, NUMBER_OF_THREADS, Long.MAX_VALUE, TimeUnit.MILLISECONDS,
                new LinkedBlockingDeque<Runnable>(), threadFactory);

        group = AsynchronousChannelGroup.withThreadPool(pool);
        asynchronousServerSocketChannel = AsynchronousServerSocketChannel.open(group);
        asynchronousServerSocketChannel.setOption(StandardSocketOptions.SO_RCVBUF, BUFFER_SIZE);

        workers = createWorkersPool(backendAddress);

        buffers = new DirectBufferPool("proxyMain", N_BUFFERS, BUFFER_SIZE);

        log.info("Proxy listen frontend: " + localPort + " backend: " + backendAddress);
        Thread.currentThread().setName("Proxy Main");
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
        log.setLevel(Level.ALL);
        if (args.length < 3) {
            log.error("usage: <frontend:port> <backend> <backend:port>");
            return;
        }
        int frontendPort = Integer.parseInt(args[0]);
        String backend = args[1];
        int backendPort = Integer.parseInt(args[2]);

        new Proxy(frontendPort, backend, backendPort).run();
    }


    public void run() throws IOException, InterruptedException, ExecutionException {
        final AsynchronousServerSocketChannel listener = asynchronousServerSocketChannel.bind(new InetSocketAddress(
                SharedProperties.MY_HOST, localPort));

        listener.accept(workers, new CompletionHandler<AsynchronousSocketChannel, LinkedBlockingDeque<ProxyWorker>>() {
            @Override
            public void completed(AsynchronousSocketChannel ch, LinkedBlockingDeque<ProxyWorker> workersList) {
                log.info("new connection");
                // accept the next connection
                ByteBuffer buffer = buffers.pop();
                ch.read(buffer, READ_TIMEOUT, TimeUnit.MILLISECONDS, ch, new ReadHandler(buffer, workersList, buffers));

                // fetch a object to handle this request
                ProxyWorker worker = null;
                do {
                    try {
                        worker = workersList.poll(ACCEPT_TIMEOUT, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        log.error(e);
                    }
                } while (worker == null);
                // accept only when a worker is available
                workersList.add(worker);
                listener.accept(workersList, this);
            }

            @Override
            public void failed(Throwable exc, LinkedBlockingDeque<ProxyWorker> att) {
                System.out.println("Fail to accept");
            }
        });

        group.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
    }

    /**
     * Set a new branch and enable restrain
     * 
     * @param branch
     * @param restrain
     * @return the current timestamp
     */
    public long setBranchAndRestrain(short branch, boolean restrain) {
        log.info("branch: " + branch + " restrain: " + restrain);
        byte[] b = null;
        if (branch != -1) {
            b = BufferTools.shortToByteArray(branch);
        }
        long currentId;
        synchronized (Proxy.lockBranchRestrain) {
            if (b != null)
                Proxy.branch = b;
            Proxy.restrain = restrain;
            currentId = ProxyWorker.getTimestamp();
        }
        return currentId;
    }



    public void timeTravel(long timeDelta) {
        String dateString = new SimpleDateFormat("H:m:S").format(new Date(timeDelta));
        log.info("traveling: " + dateString);
        synchronized (Proxy.lockBranchRestrain) {
            timeTravel = timeDelta;
        }
    }

    public void reset(short newBranch, long newSnapshot) {
        log.info("Proxy RESET to branch: " + newBranch);
        synchronized (Proxy.lockBranchRestrain) {
            branch = BufferTools.shortToByteArray(newBranch);
            timeTravel = 0;
        }
    }

}
