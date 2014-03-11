package pt.inesc.proxy;

import java.util.LinkedList;


public class ThreadPool {
    LinkedList<WorkerThread> idle = new LinkedList<WorkerThread>();

    public ThreadPool(int maxThreads, String remoteHost, int remotePort) {
        for (int i = 0; i < maxThreads; i++) {
            // Fill up the pool with worker threads
            WorkerThread thread = new WorkerThread(this, remoteHost, remotePort);

            // Set thread name for debugging. Start it.
            thread.setName("Worker" + (i + 1));
            thread.start();
            idle.add(thread);
        }
    }

    public WorkerThread getWorker() {
        WorkerThread thread = null;

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
    void returnWorker(WorkerThread worker) {
        synchronized (idle) {
            idle.add(worker);
        }
    }

}
