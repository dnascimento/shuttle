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
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import pt.inesc.BufferTools;
import pt.inesc.manager.utils.MonitorWaiter;
import pt.inesc.proxy.save.CassandraClient;
import pt.inesc.proxy.save.Request;
import pt.inesc.replay.ReplayNode;
import pt.inesc.replay.core.cookies.CookieMan;
import pt.inesc.replay.core.handlers.ChannelPack;
import pt.inesc.replay.core.handlers.HandlerWrite;
import pt.inesc.replay.core.unlock.VoldemortUnlocker;
import voldemort.undoTracker.KeyAccess;
import voldemort.undoTracker.SRD;
import voldemort.utils.ByteArray;

import com.google.common.collect.ArrayListMultimap;


public class ReplayWorker extends
        Thread {
    protected static Logger logger = LogManager.getLogger(ReplayWorker.class.getName());

    protected final List<Long> executionArray;
    public static CookieMan cookieManager = new CookieMan();

    protected final CassandraClient cassandra;
    protected final ByteBuffer ID_MARK = ByteBuffer.wrap("ID: ".getBytes());
    protected final byte[] branchBytes;
    protected final short branch;
    protected final List<String> errors = new LinkedList<String>();
    protected long totalRequests = 0;
    protected final VoldemortUnlocker unlocker;


    private final MonitorWaiter executingCounter = new MonitorWaiter();
    protected final RedoChannelPool pool;


    public ReplayWorker(List<Long> execList, InetSocketAddress remoteHost, short branch) throws Exception {
        super();
        this.executionArray = execList;
        logger.info("New Worker");
        cassandra = new CassandraClient();
        this.branchBytes = shortToByteArray(branch);
        this.branch = branch;
        // create a variable group of threads to handle each channel
        unlocker = new VoldemortUnlocker();

        pool = new RedoChannelPool(remoteHost, cassandra, executingCounter);
    }


    @Override
    public void run() {
        logger.info("Start time:" + new Date().getTime());

        // if the request start is smaller than the biggest executing end, then, wait.
        for (long reqId : executionArray) {
            try {
                if (reqId == -1) {
                    // wait for all to execute
                    executingCounter.waitUntilZero();
                    logger.info("continue");
                    continue;
                } else {
                    // execute request
                    Request request = cassandra.getRequest(reqId);
                    if (request == null) {
                        compensateRequest(reqId);
                    } else {
                        executingCounter.increment();
                        writePackage(request);
                    }
                    totalRequests++;
                }
            } catch (Exception e) {
                errors.add("Erro in req: " + reqId + " " + e);
                logger.error("Erro", e);
            }
        }

        logger.info("Redo end");
        ReplayNode.addErrors(errors, totalRequests);
    }

    protected void compensateRequest(long reqID) throws Exception {
        logger.warn("Request deleted: " + reqID + " fetching the keys to compensate...");
        // the request was delete, unlock the original keys
        ArrayListMultimap<ByteArray, KeyAccess> keys = cassandra.getKeys(reqID);
        if (keys != null && !keys.isEmpty()) {
            unlocker.unlockKeys(keys, new SRD(reqID, branch, false));
        } else {
            throw new Exception("Request not found " + reqID);
        }
    }

    protected void setNewHeader(ByteBuffer data, long rid) {
        int initPosition = data.position();
        int startOfId = indexOf(data.position(), data.limit(), data, ID_MARK) + ID_MARK.capacity();
        byte[] ts = new Long(rid).toString().getBytes();

        data.position(startOfId);
        data.put(ts);
        data.position(startOfId + 20);
        data.put(branchBytes);
        // restrain is always false
        data.position(startOfId + 37);
        data.put((byte) 't');
        data.position(initPosition);
        System.out.println(BufferTools.printContent(data));
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
     * Convert a short to byte array including the leading zeros and using 1 byte per char
     * encode
     * 
     * @param s
     * @return
     */
    private byte[] shortToByteArray(int s) {
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



    /**
     * The retrieved execution list is built of a single list ordered by requestID. This
     * method ensures that a request is sent only after the requests, which end before,
     * are executed.
     */


    private void writePackage(Request request) throws Exception {
        ChannelPack pack = pool.getChannel();
        // ProxyWorker.printContent(data);
        setNewHeader(request.data, request.rid);
        pack.reset(request.data.limit() - request.data.position(), request);
        pack.channel.write(request.data, pack, new HandlerWrite());
    }


}
