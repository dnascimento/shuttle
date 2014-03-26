package pt.inesc.redoNode.core.handlers;

public class CountingSemaphore {
    private int countSends;

    public CountingSemaphore(int countSends) {
        super();
        this.countSends = countSends;
    }

    public synchronized void decrement() {
        this.countSends--;
        if (this.countSends == 0) {
            this.notify();
        }
    }

    public synchronized void waitInSemaphore() {
        while (this.countSends == 0)
            try {
                this.wait();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
    }
}
