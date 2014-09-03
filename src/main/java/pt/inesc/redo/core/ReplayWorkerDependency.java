/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.redo.core;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import pt.inesc.proxy.save.Request;
import pt.inesc.redo.core.handlers.ChannelPack;
import pt.inesc.redo.core.handlers.HandlerWrite;


public class ReplayWorkerDependency extends
        ReplayWorker {
    private final AtomicInteger sentCounter = new AtomicInteger(0);
    protected final RedoChannelPool pool;

    public ReplayWorkerDependency(List<Long> execList, InetSocketAddress remoteHost, short branch) throws Exception {
        super(execList, remoteHost, branch);
        pool = new RedoChannelPool(remoteHost, cassandra, sentCounter);
    }




    @Override
    public void startReplay() {
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
                    Request request = cassandra.getRequest(reqID);
                    if (request.data == null) {
                        compensateRequest(reqID);
                    } else {
                        // IMPORTANT: request includes cassandra metadata at begin.
                        // DO NOT REWIND
                        writePackage(request);
                        sentCounter.incrementAndGet();
                    }
                }
            } catch (Exception e) {
                errors.add("Erro in req: " + reqID + " " + e);
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
