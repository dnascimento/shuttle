/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.manager.utils;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * Simple thread alarm to notify the snapshot
 * 
 * @author darionascimento
 */
public class NotifyEvent extends
        Thread {

    private final Logger log = LogManager.getLogger(NotifyEvent.class.getName());
    private final String msg;
    private final long alertMoment;

    public NotifyEvent(String msg, long newRid) {
        super();
        this.msg = msg;
        this.alertMoment = newRid;
    }

    @Override
    public void run() {
        try {
            sleep((alertMoment - getTimeStamp()) / 1000);
        } catch (InterruptedException e) {
            log.error(e);
        }
        log.warn("ALERT->" + msg);
    }


    private static long getTimeStamp() {
        return System.currentTimeMillis() / 1000;
    }

}
