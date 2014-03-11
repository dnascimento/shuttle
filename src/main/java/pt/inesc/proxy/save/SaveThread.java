package pt.inesc.proxy.save;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

import pt.inesc.proxy.WorkerThread;


public abstract class SaveThread extends
        Thread {
    public enum SaveType {
        Request, Response
    }

    private final ConcurrentHashMap<Integer, ByteBuffer> packages;
    private final int start, end;

    public SaveThread(SaveType type, int start, int end) {
        switch (type) {
        case Request:
            packages = WorkerThread.requests;
            break;
        case Response:
            packages = WorkerThread.responses;
            break;
        default:
            packages = null;
        }
        this.start = start;
        this.end = end;
    }


    @Override
    public void run() {
        init();
        for (int keyID = start; keyID <= end; keyID++) {
            ByteBuffer buffer = packages.remove(keyID);
            if (buffer == null) {
                continue;
            }
            buffer.rewind();
            save(keyID, buffer);
        }
        close();
    }

    public abstract void save(int id, ByteBuffer pack);

    public abstract void close();

    public abstract void init();


}
