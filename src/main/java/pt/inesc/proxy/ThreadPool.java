package pt.inesc.proxy;

import java.util.LinkedList;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;


public class ThreadPool {
    private static final Logger log = LogManager.getLogger(ThreadPool.class.getName());

    LinkedList<ProxyWorker> idle = new LinkedList<ProxyWorker>();
    String remoteHost;
    int remotePort;
    int maxThreads;
    int nTheads = 0;


    public ThreadPool(int initCapacity, int maxThreads, String remoteHost, int remotePort) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.maxThreads = maxThreads;
        synchronized (idle) {
            for (int i = 0; i < initCapacity; i++) {
                idle.add(newWorker());
            }
        }
    }

    private ProxyWorker newWorker() {
        // Fill up the pool with worker threads
        ProxyWorker thread = new ProxyWorker(this, remoteHost, remotePort);
        // Set thread name for debugging. Start it.
        // thread.setName("Worker" + i);
        nTheads++;
        thread.start();
        return thread;
    }

    /**
     * Try to get a working thread.
     * If no more threads, create new
     * 
     * @return
     */
    public ProxyWorker getWorker() {
        ProxyWorker thread = null;
        synchronized (idle) {
            if (idle.size() > 0) {
                thread = idle.removeFirst();
            } else {
                thread = newWorker();
                log.info("thread created" + thread.getId());
            }
        }
        return thread;
    }

    /**
     * Called by the worker thread to return itself to the idle pool.
     */
    void returnWorker(ProxyWorker worker) {
        synchronized (idle) {
            idle.addFirst(worker);
        }
    }

}
