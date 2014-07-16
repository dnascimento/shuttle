package pt.inesc.proxy.threading;

import java.util.concurrent.Callable;

public class MyThread<V> extends
        Thread {
    /**
     * MyThreadPool object, from which the task to be run
     */
    private MyThreadPool<V> pool;
    private boolean active = true;

    public boolean isActive() {
        return active;
    }

    public void setPool(MyThreadPool<V> p) {
        pool = p;
    }

    /**
     * Checks if there are any unfinished tasks left. if there are , then runs
     * the task and call back with output on resultListner Waits if there are no
     * tasks available to run If shutDown is called on MyThreadPool, all waiting
     * threads will exit and all running threads will exit after finishing the
     * task
     */
    @Override
    public void run() {
        ResultListener<V> result = pool.getResultListener();
        Callable<V> task;
        while (true) {
            task = pool.removeFromQueue();
            if (task != null) {
                try {
                    V output = task.call();
                    result.finish(output);
                } catch (Exception e) {
                    result.error(e);
                }
            } else {
                if (!isActive())
                    break;
                else {
                    synchronized (pool.getWaitLock()) {
                        try {
                            pool.getWaitLock().wait();
                        } catch (InterruptedException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    void shutdown() {
        active = false;
    }
}
