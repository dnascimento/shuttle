package pt.inesc.proxy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DataSaver extends
        Thread {
    private final Logger log = LogManager.getLogger("DataSaver");
    private static int lastRecorded = 0; // shared to know the last
    private final int start;

    private final ConcurrentHashMap<Integer, ByteBuffer> packages;
    private final int end;
    private final String type;
    private final ByteBuffer separator = ByteBuffer.wrap("\n===\n".getBytes());
    ByteBuffer connectionClose = ByteBuffer.wrap("Connection: close".getBytes());
    ByteBuffer newLine = ByteBuffer.wrap(new byte[] { 13, 10 });
    private WritableByteChannel channel;

    public DataSaver(ConcurrentHashMap<Integer, ByteBuffer> packagesToSave,
            String type,
            int end) {
        packages = packagesToSave;
        this.type = type;
        start = updateSharedStart(end);
        this.end = end;

        getChannel();
    }


    @Override
    public void run() {
        try {
            ByteBuffer messageIdHeader;
            ByteBuffer pack;
            for (int i = start; i <= end; i++) {
                pack = packages.get(i);
                if (pack == null)
                    continue;
                packages.remove(i);
                messageIdHeader = ByteBuffer.wrap(("id" + i + "\n").getBytes());

                channel.write(messageIdHeader);
                channel.write(pack);
                channel.write(separator);
                newLine.rewind();
                separator.rewind();
            }
            channel.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ///////////////////////////////////////////////////////
    private void getChannel() {
        File temp = new File("requests/" + type + end + ".txt");
        RandomAccessFile file;
        try {
            file = new RandomAccessFile(temp, "rw");
            channel = file.getChannel();
        } catch (FileNotFoundException e) {
            log.error(e);
        }
    }

    private synchronized int updateSharedStart(int end) {
        int start = lastRecorded;
        lastRecorded = Math.max(lastRecorded, end);
        return start;
    }

}
