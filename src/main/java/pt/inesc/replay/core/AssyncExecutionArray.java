package pt.inesc.replay.core;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.NotImplementedException;
import org.apache.http.HttpRequest;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import pt.inesc.BufferTools;
import pt.inesc.proxy.save.CassandraClient;
import pt.inesc.proxy.save.Request;

/**
 * This class wraps a list of Request IDs and fetches the requests asynchronously in a
 * batch way.
 * The requests are accessed through an iterator. It's a kind of FIFO, a producer-consumer
 * problem with one unit of each.
 * 
 * @author darionascimento
 */
public class AssyncExecutionArray extends
        Thread
        implements Iterable<Request>, Iterator<Request> {
    private static final Logger log = LogManager.getLogger(AssyncExecutionArray.class.getName());

    // TODO: if parallel, use 1/10
    static final int BATCH_SIZE = 2000;
    /** When only BATCH_MIN_BUFFER are fetched, fetch more BATCH_SIZE requests */
    static final int BATCH_MIN_BUFFER = 1000;
    /**
     * Fetch without waiting for the consumer. Its faster but it overflow the memory
     */
    private static final boolean FETCH_CONTINUE = false;
    final CassandraClient cassandra;
    ArrayList<Request> list;
    int itPosition = -1;
    int nextFetchNotification = 0;
    int fetchPosition = 0;

    private final byte[] branchBytes;



    public AssyncExecutionArray(List<Long> execList, CassandraClient cassandra, short branch) {
        list = new ArrayList<Request>(execList.size());
        this.branchBytes = BufferTools.shortToByteArray(branch);
        for (Long rid : execList) {
            list.add(new Request(rid));
        }
        this.cassandra = cassandra;
        this.start();
    }

    /**
     * The producer
     */
    @Override
    public void run() {
        while (fetchPosition < list.size()) {
            fetch();
            synchronized (this) {
                fetchPosition += BATCH_SIZE;
                nextFetchNotification = fetchPosition - BATCH_MIN_BUFFER;
                this.notify();
                if (!FETCH_CONTINUE) {
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }


    /**
     * The consumer
     */
    @Override
    public boolean hasNext() {
        itPosition++;

        if (itPosition == list.size()) {
            return false;
        }

        if (itPosition >= nextFetchNotification) {
            synchronized (this) {
                if (itPosition == nextFetchNotification) {
                    // notify to fetch more
                    if (!FETCH_CONTINUE) {
                        this.notify();
                    }
                }
                // can still read?
                if (itPosition == fetchPosition) {
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }
            }
        }
        return true;
    }




    private void fetch() {
        int limit = fetchPosition + BATCH_SIZE;
        limit = (limit > list.size()) ? list.size() : limit;
        cassandra.getRequests(list, fetchPosition, limit);

        for (int i = fetchPosition; i < limit; i++) {
            Request r = list.get(i);
            if (r.rid == -1) {
                continue;
            }
            if (r.data == null) {
                log.error("Request without data: " + r.rid);
                continue;
            }

            BufferTools.modifyHeader(r.data, r.rid, 0, branchBytes, false, true);

            HttpRequest request = null;
            try {
                request = BufferTools.bufferToRequest(r.data);
            } catch (UnsupportedEncodingException e) {
                log.error(e);
            }
            r.request = request;
        }
    }

    @Override
    /**
     * The iterator is not thread safe. Use one iterator a time.
     */
    public Iterator<Request> iterator() {
        return this;
    }

    @Override
    public Request next() {
        return list.get(itPosition);
    }

    @Override
    public void remove() {
        throw new NotImplementedException();
    }





}
