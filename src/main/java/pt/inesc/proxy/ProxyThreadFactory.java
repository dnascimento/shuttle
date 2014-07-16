package pt.inesc.proxy;

import java.util.concurrent.ThreadFactory;

public class ProxyThreadFactory
        implements ThreadFactory {

    int id = 0;

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r);
        t.setName("Proxy-Worker-Pool" + (id++));

        return t;
    }
}
