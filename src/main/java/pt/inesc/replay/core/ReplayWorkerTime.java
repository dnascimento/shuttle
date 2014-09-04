/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.replay.core;

import java.net.InetSocketAddress;
import java.util.List;

import pt.inesc.proxy.save.Request;
import pt.inesc.replay.core.handlers.BiggestEndList;
import pt.inesc.replay.core.handlers.ChannelPack;
import pt.inesc.replay.core.handlers.HandlerWrite;


public class ReplayWorkerTime extends
        ReplayWorker {
    private final BiggestEndList biggestEnd = new BiggestEndList();
    protected final RedoChannelPool pool;

    public ReplayWorkerTime(List<Long> execList, InetSocketAddress remoteHost, short branch) throws Exception {
        super(execList, remoteHost, branch);
        pool = new RedoChannelPool(remoteHost, cassandra, biggestEnd);

    }



    /**
     * The retrieved execution list is built of a single list ordered by requestID. This
     * method ensures that a request is sent only after the requests, which end before,
     * are executed.
     */
    @Override
    public void startReplay() {
        // if the request start is smaller than the biggest executing end, then, wait.
        for (long reqId : executionArray) {
            try {
                Request request = cassandra.getRequest(reqId);
                while (true) {
                    long biggestEndExecuting;
                    synchronized (biggestEnd) {
                        biggestEndExecuting = biggestEnd.getBiggest();
                        if (request.rid <= biggestEndExecuting || biggestEnd.isEmpty()) {
                            biggestEnd.addEnd(request.end);
                            break;
                        }
                        biggestEnd.wait();
                        logger.info("continue");
                    }
                }
                // execute request
                writePackage(request);
            } catch (Exception e) {
                errors.add("Erro in req: " + reqId + " " + e);
                logger.error("Erro", e);
            }
        }

    }

    private void writePackage(Request request) throws Exception {
        ChannelPack pack = pool.getChannel();
        // ProxyWorker.printContent(data);
        setNewHeader(request.data, request.rid);
        pack.reset(request.data.limit() - request.data.position(), request);
        pack.channel.write(request.data, pack, new HandlerWrite());
    }


}
