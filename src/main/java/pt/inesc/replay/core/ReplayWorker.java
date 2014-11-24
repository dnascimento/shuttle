/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.replay.core;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import pt.inesc.proxy.save.CassandraClient;
import pt.inesc.proxy.save.Request;
import pt.inesc.replay.ReplayNode;
import pt.inesc.replay.core.ReplayWorker.HttpCallback;
import pt.inesc.replay.core.unlock.VoldemortUnlocker;
import voldemort.undoTracker.KeyAccess;
import voldemort.undoTracker.SRD;
import voldemort.utils.ByteArray;

import com.google.common.collect.ArrayListMultimap;

public class ReplayWorker extends
        Thread {

    private int requestRate = 700;
    private int requestRateSent = 0;
    private long currentSecound = 0;
    private long delay;
    /** Max number of requests pendent before adapt the throughput */
    private static final int MAX_QUEUE_SIZE = 8000;
    private static final int MIN_QUEUE_SIZE = 2000;
    private static final int ABSOLUT_LIMIT_QUEUE_SIZE = 12000;
    private static final int THROUGHPUT_ADJUSTMENT = 100;
    private static final long MAX_DELAY = 800;
    private static final long MIN_SLEEP_TIME_MS = 5;
    private static final String USER_AGENT = "Shuttle";

    private static final int REQUEST_TIMEOUT = 2500000;
    /**
     * If the requests send is much bigger than the target rate, for instance if the queue
     * is huge, then the delay can be huge. We set a maximum value for delay
     */


    protected static Logger log = LogManager.getLogger(ReplayWorker.class.getName());

    protected final AssyncExecutionArray executionArray;
    // TODO public static CookieMan cookieManager = new CookieMan();

    protected final CassandraClient cassandra;
    protected final short branch;
    protected final List<String> errors = new LinkedList<String>();
    protected long totalRequests = 0;
    protected final VoldemortUnlocker unlocker;

    private final CloseableHttpAsyncClient httpclient;
    private final HttpHost target;

    private final MonitorWaiter executingCounter = new MonitorWaiter();


    public ReplayWorker(List<Long> execList, InetSocketAddress remoteHost, short branch, CassandraClient cassandra) throws Exception {
        super();
        log.info("New replay worker");
        this.cassandra = cassandra;
        executionArray = new AssyncExecutionArray(execList, cassandra, branch);
        this.branch = branch;
        // create a variable group of threads to handle each channel
        // TODO check this unlocker
        unlocker = new VoldemortUnlocker();
        // define throughput:
        delay = (long) (((double) 1 / requestRate) * 1000);

        RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(REQUEST_TIMEOUT).setConnectTimeout(REQUEST_TIMEOUT).build();

        httpclient = HttpAsyncClients.custom()
                                     .setDefaultRequestConfig(requestConfig)
                                     .setUserAgent(USER_AGENT)
                                     .setKeepAliveStrategy(DefaultConnectionKeepAliveStrategy.INSTANCE)
                                     .build();
        httpclient.start();

        target = new HttpHost(remoteHost.getAddress(), remoteHost.getPort());
    }

    @Override
    public void run() {
        log.info("Start time:" + new Date().getTime());
        // if the request start is smaller than the biggest executing end, then, wait.
        Iterator<Request> requestIT = executionArray.iterator();

        while (requestIT.hasNext()) {
            Request request = requestIT.next();
            try {
                if (request.rid == -1) {
                    // wait for all to execute
                    // System.out.println("Wait until zero: ");
                    executingCounter.waitUntilZero();
                    // logger.info("continue: " + totalRequests + " out of: " +
                    // executionArray.size());
                    continue;
                } else {
                    // Load a batch of requests
                    if (request.request == null) {
                        compensateRequest(request.rid);
                    } else {
                        int pendentRequests = executingCounter.increment(request.rid);
                        throughputControl(pendentRequests);
                        writePackage(request);
                    }
                    totalRequests++;
                }
            } catch (Exception e) {
                errors.add("Erro in req: " + request.rid + " " + e);
                log.error("Erro", e);
            }
        }
        try {
            executingCounter.waitUntilZero();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        log.info("Replay end");
        ReplayNode.addErrors(errors, totalRequests);
    }

    protected void compensateRequest(long reqID) throws Exception {
        // logger.warn("Request deleted: " + reqID +
        // " fetching the keys to compensate...");
        // the request was delete, unlock the original keys
        ArrayListMultimap<ByteArray, KeyAccess> keys = cassandra.getKeys(reqID);
        if (keys != null && !keys.isEmpty()) {
            unlocker.unlockKeys(keys, new SRD(reqID, branch, false));
        } else {
            throw new Exception("Request not found " + reqID);
        }
    }



    /**
     * Returns the index within this buffer of the first occurrence of the specified
     * pattern buffer.
     * 
     * @param startPosition
     * @param buffer the buffer
     * @param pattern the pattern buffer
     * @return the position within the buffer of the first occurrence of the pattern
     *         buffer (in the 1st byte of the pattern)
     */
    public int indexOf(int startPosition, int end, ByteBuffer buffer, ByteBuffer pattern) {
        int patternLen = pattern.limit();
        int lastIndex = end - patternLen + 1;
        Label: for (int i = startPosition; i < lastIndex; i++) {
            if (buffer.get(i) == pattern.get(0)) {
                for (int j = 1; j < patternLen; j++) {
                    if (buffer.get(i + j) != pattern.get(j)) {
                        continue Label;
                    }
                }
                return i;
            }
        }
        return -1;
    }







    /**
     * The retrieved execution list is built of a single list ordered by requestID. This
     * method ensures that a request is sent only after the requests, which end before,
     * are executed.
     */
    private void writePackage(Request requestPackage) throws Exception {
        // System.out.println("Start request: " + requestPackage.rid);
        httpclient.execute(target, requestPackage.request, new HttpCallback(requestPackage));


    }

    /**
     * The delay controls the throughput, at end of each second, the throughput is
     * compared and the delay is adapted.
     * 
     * @param pendentRequests
     */
    private void throughputControl(int pendentRequests) {
        // delay the request to control the throughput
        if (delay > MIN_SLEEP_TIME_MS) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }


        long now = System.currentTimeMillis() / 1000;
        if (currentSecound == 0) {
            currentSecound = now;
        }

        if (now != currentSecound) {
            currentSecound = now;

            // update request rate every second
            if (pendentRequests > MAX_QUEUE_SIZE) {
                requestRate -= THROUGHPUT_ADJUSTMENT;
                requestRate = (requestRate <= 0) ? 1 : requestRate;
            }
            if (pendentRequests < MIN_QUEUE_SIZE) {
                requestRate += THROUGHPUT_ADJUSTMENT;
            }


            log.warn("Real written rate: " + requestRateSent + " targetRate: " + requestRate + " delay: " + delay + " total: "
                    + totalRequests + " pendent: " + pendentRequests);


            // calculate the new delay
            delay = (long) (((double) 1 / requestRate) * 1000);
            delay = (delay > MAX_DELAY) ? MAX_DELAY : delay;
            delay = (delay <= 0) ? 0 : delay;

            requestRateSent = 0;

        }
        requestRateSent++;

        // Delay the requests if the limit is exceeded
        if (pendentRequests > ABSOLUT_LIMIT_QUEUE_SIZE) {
            delay = MAX_DELAY;
        }
    }


    class HttpCallback
            implements FutureCallback<HttpResponse> {

        private Request req;

        HttpCallback(Request req) {
            this.req = req;
        }

        @Override
        public void completed(final HttpResponse response) {
            // StatusLine status = response.getStatusLine();
            // System.out.println(status.getStatusCode() + " " +
            // status.getReasonPhrase());

            long id = 0;
            Header[] headers = response.getAllHeaders();
            for (Header h : headers) {
                if (h.getName().equals("Id")) {
                    id = Long.valueOf(h.getValue());
                }
            }
            if (id == 0) {
                System.err.println("id not found");
            }
            executingCounter.decrement(id);
            // clean to GC
            req.request = null;
            req = null;
        }

        @Override
        public void failed(final Exception ex) {
            System.err.println(ex);
        }

        @Override
        public void cancelled() {
            System.out.println(" cancelled");
        }
    }


}
