package pt.inesc.proxy.save;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import pt.inesc.proxy.BytePackage;
import pt.inesc.proxy.WorkerThread;


public abstract class SaveThread extends
        Thread {

    private final Logger log = LogManager.getLogger("Saver Thread");
    private final ByteBuffer separator = ByteBuffer.wrap("\n===\n".getBytes());
    ByteBuffer connectionClose = ByteBuffer.wrap("Connection: close".getBytes());
    ByteBuffer newLine = ByteBuffer.wrap(new byte[] { 13, 10 });

    private final ConcurrentHashMap<Integer, BytePackage> packages;
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
        try {
            WritableByteChannel channel = getChannel();
            ByteBuffer messageIdHeader;
            BytePackage pack;
            System.out.println("save");
            for (int keyID = start; keyID <= end; keyID++) {
                pack = packages.get(keyID);
                if (pack == null) {
                    continue;
                }
                packages.remove(keyID);
                messageIdHeader = ByteBuffer.wrap(("id" + keyID + "\n").getBytes());

                channel.write(messageIdHeader);
                for (ByteBuffer buffer : pack.getBytesList()) {
                    buffer.flip();
                    buffer.rewind();
                    channel.write(buffer);
                }
                channel.write(separator);
                newLine.rewind();
                separator.rewind();
                packages.remove(keyID);
            }
            closeChannel();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    abstract WritableByteChannel getChannel() throws IOException;

    abstract void closeChannel() throws IOException;

}
