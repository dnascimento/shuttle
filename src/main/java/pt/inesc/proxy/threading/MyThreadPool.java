package pt.inesc.proxy.threading;

import java.util.LinkedList;
import java.util.concurrent.Callable;

/**
 * This class is used to execute submitted {@link Callable} tasks. this class
 * creates and manages fixed number of threads User will provide a {@link ResultListener}
 * object in order to get the Result of submitted task
 * 
 * @author abhishek
 */
public class MyThreadPool<V> {
    private final Object waitLock = new Object();

    public Object getWaitLock() {
        return waitLock;
    }

    /**
     * list of threads for completing submitted tasks
     */
    private final LinkedList<MyThread<V>> threads;
    /**
     * submitted task will be kept in this list untill they run by one of
     * threads in pool
     */
    private final LinkedList<Callable<V>> tasks;
    /**
     * shutDown flag to shut Down service
     */
    private volatile boolean shutDown;
    /**
     * ResultListener to get back the result of submitted tasks
     */
    private ResultListener<V> resultListener;

    /**
     * initializes the threadPool by starting the threads threads will wait till
     * tasks are not submitted
     * 
     * @param size
     *            Number of threads to be created and maintained in pool
     * @param myResultListener
     *            ResultListener to get back result
     */
    public MyThreadPool(int size, ResultListener<V> myResultListener) {
        tasks = new LinkedList<Callable<V>>();
        threads = new LinkedList<MyThread<V>>();
        shutDown = false;
        resultListener = myResultListener;
        for (int i = 0; i < size; i++) {
            MyThread<V> myThread = new MyThread<V>();
            myThread.setPool(this);
            threads.add(myThread);
            myThread.start();
        }
    }

    public ResultListener<V> getResultListener() {
        return resultListener;
    }

    public void setResultListener(ResultListener<V> resultListener) {
        this.resultListener = resultListener;
    }

    public boolean isShutDown() {
        return shutDown;
    }

    public int getThreadPoolSize() {
        return threads.size();
    }

    public synchronized Callable<V> removeFromQueue() {
        return tasks.poll();
    }

    public synchronized void addToTasks(Callable<V> callable) {
        tasks.add(callable);
    }

    /**
     * submits the task to threadPool. will not accept any new task if shutDown
     * is called Adds the task to the list and notify any waiting threads
     * 
     * @param callable
     */
    public void submit(Callable<V> callable) {
        if (!shutDown) {
            addToTasks(callable);
            synchronized (this.waitLock) {
                waitLock.notify();
            }
        } else {
            System.out.println("task is rejected.. Pool shutDown executed");
        }
    }

    /**
     * Initiates a shutdown in which previously submitted tasks are executed,
     * but no new tasks will be accepted. Waits if there are unfinished tasks
     * remaining
     */
    public void stop() {
        for (MyThread<V> mythread : threads) {
            mythread.shutdown();
        }
        synchronized (this.waitLock) {
            waitLock.notifyAll();
        }
        for (MyThread<V> mythread : threads) {
            try {
                mythread.join();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
}
