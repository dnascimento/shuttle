/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.redo.core;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import pt.inesc.proxy.save.CassandraClient;
import pt.inesc.redo.RedoNode;
import pt.inesc.redo.core.cookies.CookieMan;
import pt.inesc.redo.core.handlers.ChannelPack;
import pt.inesc.redo.core.handlers.HandlerWrite;
import pt.inesc.redo.core.unlock.VoldemortUnlocker;
import voldemort.undoTracker.KeyAccess;
import voldemort.undoTracker.RUD;
import voldemort.utils.ByteArray;

import com.google.common.collect.ArrayListMultimap;


public class RedoWorker extends
        Thread {
    private static Logger logger = LogManager.getLogger(RedoWorker.class.getName());

    private final List<Long> executionArray;
    public static CookieMan cookieManager = new CookieMan();

    private final CassandraClient cassandra;
    private final AtomicInteger sentCounter = new AtomicInteger(0);
    private final ByteBuffer ID_MARK = ByteBuffer.wrap("ID: ".getBytes());
    private final byte[] branchBytes;
    private final short branch;
    private final List<String> errors = new LinkedList<String>();
    private final RedoChannelPool pool;
    private long totalRequests = 0;
    private final VoldemortUnlocker unlocker;

    public RedoWorker(List<Long> execList, InetSocketAddress remoteHost, short branch) throws Exception {
        super();
        this.executionArray = execList;
        logger.info("New Worker");
        cassandra = new CassandraClient();
        this.branchBytes = shortToByteArray(branch);
        this.branch = branch;

        // create a variable group of threads to handle each channel
        pool = new RedoChannelPool(remoteHost, cassandra);
        unlocker = new VoldemortUnlocker();
    }


    @Override
    public void run() {
        logger.info("time:" + new Date().getTime());

        for (long reqID : executionArray) {
            try {
                logger.info("Redo Request:" + reqID);
                if (reqID == -1) {
                    synchronized (sentCounter) {
                        if (sentCounter.get() != 0) {
                            sentCounter.wait();
                            logger.info("continue");
                        }
                    }
                } else {
                    totalRequests++;
                    ChannelPack channel = pool.getChannel();
                    ByteBuffer request = cassandra.getRequest(reqID);
                    if (request == null) {
                        logger.warn("Request deleted: " + reqID + " fetching the keys to compensate...");
                        pool.returnChannel(channel);
                        // the request was delete, unlock the original keys
                        ArrayListMultimap<ByteArray, KeyAccess> keys = cassandra.getKeys(reqID);
                        if (keys != null && !keys.isEmpty()) {
                            logger.warn("Unlocking the keys: " + keys);
                            unlocker.unlockKeys(keys, new RUD(reqID, branch, false));
                        } else {
                            throw new Exception("Request not found " + reqID);
                        }
                    } else {
                        // IMPORTANT: request includes cassandra metadata at begin. DO NOT
                        // rewind
                        writePackage(channel, request, reqID);
                        sentCounter.incrementAndGet();
                    }
                }
            } catch (Exception e) {
                errors.add("Erro in req: " + reqID + " " + e);
                logger.error("Erro", e);
            }
        }
        logger.info("Redo end");
        RedoNode.addErrors(errors, totalRequests);
    }

    private void writePackage(ChannelPack pack, ByteBuffer data, long rid) throws InterruptedException, ExecutionException {
        // ProxyWorker.printContent(data);
        setNewHeader(data, rid);
        pack.reset(data.limit() - data.position(), sentCounter, rid);
        pack.channel.write(data, pack, new HandlerWrite());
    }


    private void setNewHeader(ByteBuffer data, long rid) {
        int initPosition = data.position();
        int startOfId = indexOf(data.position(), data.limit(), data, ID_MARK) + ID_MARK.capacity();
        byte[] ts = new Long(rid).toString().getBytes();
        data.position(startOfId);
        data.put(ts);
        data.position(startOfId + 17);
        data.put(branchBytes);
        // restrain is always false
        data.position(startOfId + 34);
        data.put((byte) 't');
        data.position(initPosition);
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

}
