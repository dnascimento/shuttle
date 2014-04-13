package pt.inesc.manager;

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
            sleep(alertMoment - System.currentTimeMillis());
        } catch (InterruptedException e) {
            log.error(e);
        }
        log.info("ALERT->" + msg);
    }


}
