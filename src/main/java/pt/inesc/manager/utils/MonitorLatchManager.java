package pt.inesc.manager.utils;



public class MonitorLatchManager {
    static final int CHECK_TERMINAL_PERIOD_MS = 1000;

    int pendent = 0;

    public synchronized int set(int value) {
        pendent = value;
        return pendent;
    }

    public synchronized int increment(int value) {
        pendent += value;
        return pendent;
    }

    public int increment() {
        return this.increment(1);
    }

    public synchronized void waitUntilZero() throws InterruptedException {
        if (pendent == 0) {
            return;
        }

        this.wait();

    }

    public synchronized int decrement(int value) {
        pendent -= value;
        if (pendent == 0) {
            this.notifyAll();
        }
        return pendent;
    }

    public int decrement() {
        return this.decrement(1);
    }
}
