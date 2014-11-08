package pt.inesc.replay.core;

import java.util.HashSet;
import java.util.Iterator;




public class MonitorWaiter {

    class ShowCounter extends
            Thread {
        MonitorWaiter t;


        public ShowCounter(MonitorWaiter t) {
            super();
            this.t = t;
        }


        @Override
        public void run() {
            while (true) {
                try {
                    sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println(t.getStatus());
            }
        }
    }


    public MonitorWaiter() {
        // new ShowCounter(this).start();
    }


    int counter = 0;
    HashSet<Long> pendent = new HashSet<Long>();

    public synchronized int increment(long rid) {
        counter++;
        pendent.add(rid);
        return counter;
    }

    public String getStatus() {
        StringBuilder sb = new StringBuilder();
        Iterator<Long> it = pendent.iterator();
        while (it.hasNext()) {
            sb.append(it.next());
            sb.append(",");
        }
        return sb.toString();
    }

    public synchronized void waitUntilZero() throws InterruptedException {
        if (counter == 0) {
            return;
        }
        this.wait();
    }

    public synchronized int decrement(long rid) {
        counter--;
        pendent.remove(rid);
        if (counter == 0) {
            this.notifyAll();
        }
        return counter;
    }

}
