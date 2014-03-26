package pt.inesc.proxy;

import java.util.LinkedList;


public class ThreadPool {
    LinkedList<ProxyWorker> idle = new LinkedList<ProxyWorker>();

    public ThreadPool(int maxThreads, String remoteHost, int remotePort) {
        for (int i = 0; i < maxThreads; i++) {
            // Fill up the pool with worker threads
            ProxyWorker thread = new ProxyWorker(this, remoteHost, remotePort);

            // Set thread name for debugging. Start it.
            thread.setName("Worker" + (i + 1));
            thread.start();
            idle.add(thread);
        }
    }

    public ProxyWorker getWorker() {
        ProxyWorker thread = null;

        synchronized (idle) {
            if (idle.size() > 0) {
                thread = idle.remove(0);
            }
        }
        return thread;
    }

    /**
     * Called by the worker thread to return itself to the idle pool.
     */
    void returnWorker(ProxyWorker worker) {
        synchronized (idle) {
            idle.add(worker);
        }
    }

}
