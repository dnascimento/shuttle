/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.replay.core.handlers;

import java.nio.channels.AsynchronousSocketChannel;

import pt.inesc.proxy.save.Request;
import pt.inesc.replay.core.ReplayChannelPool;


public class ChannelPack {
    public AsynchronousSocketChannel channel;
    public int bytesToProcess;
    public ReplayChannelPool pool;
    public Request request;

    public ChannelPack(AsynchronousSocketChannel channel, ReplayChannelPool replayChannelPool) {
        this.channel = channel;
        this.pool = replayChannelPool;
    }

    public void set(int bytesToProcess, Request request) {
        this.bytesToProcess = bytesToProcess;
        this.request = request;
    }

    public void returnChannel() {
        pool.returnChannel(this);
    }
}
