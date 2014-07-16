package pt.inesc.proxy.threading;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * ResultListener class to keep track of total matched count
 * 
 * @author abhishek
 * @param
 */
public class MatchedCountResultListener<V>
        implements ResultListener<V> {

    /**
     * matchedCount to keep track of the number of matches returned by submitted
     * task
     */
    AtomicInteger matchedCount = new AtomicInteger();

    /**
     * this method is called by ThreadPool to give back the result of callable
     * task. if the task completed successfully then increment the matchedCount by
     * result count
     */
    @Override
    public void finish(V obj) {
        // System.out.println("count is "+obj);
        matchedCount.addAndGet((Integer) obj);
    }

    /**
     * print exception thrown in running the task
     */
    @Override
    public void error(Exception ex) {
        ex.printStackTrace();
    }


    /**
     * returns the final matched count of all the finished tasks
     * 
     * @return
     */
    public int getFinalCount() {
        return matchedCount.get();
    }
}
