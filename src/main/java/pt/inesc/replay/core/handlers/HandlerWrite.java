/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.replay.core.handlers;

import java.nio.channels.CompletionHandler;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;


public class HandlerWrite
        implements CompletionHandler<Integer, ChannelPack> {
    private static Logger logger = LogManager.getLogger(HandlerWrite.class.getName());



    @Override
    public void completed(Integer bytesWritten, ChannelPack aux) {
        if (bytesWritten != aux.bytesToProcess) {
            logger.error("The socket did not write everything");
        }
        // System.err.println("request written: " + aux.request.rid);
        aux.returnChannel();
    }

    @Override
    public void failed(Throwable exc, ChannelPack channel) {
        logger.error(exc);
        System.err.println(exc);
    }



}
