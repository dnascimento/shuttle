package pt.inesc.proxy.proxy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DataSaver extends
        Thread {

    public TreeMap<Integer, ByteBuffer> log;
    public int id;
    private Logger logger = LogManager.getLogger("DataSaver");
    private String type;
    private ByteBuffer separator = ByteBuffer.wrap("\n===\n".getBytes());
    ByteBuffer connectionClose = ByteBuffer.wrap("Connection: close".getBytes());


    public DataSaver(TreeMap<Integer, ByteBuffer> packagesToSave, String type) {
        log = packagesToSave;
        id = packagesToSave.lastKey();
        this.type = type;
    }

    @Override
    public void run() {
        try {
            File temp = new File("requests/" + type + id + ".txt");
            RandomAccessFile file = new RandomAccessFile(temp, "rw");
            FileChannel fileChannel = file.getChannel();
            ByteBuffer pack;
            Entry<Integer, ByteBuffer> entry;
            try {
                while ((entry = log.pollFirstEntry()) != null) {
                    pack = entry.getValue();

                    // int index = indexOf(pack, connectionClose);
                    // if (index != -1) {
                    // TODO Retirar a string daqui?
                    // }
                    fileChannel.write(ByteBuffer.wrap((entry.getKey().toString() + "\n").getBytes()));
                    fileChannel.write(pack);
                    fileChannel.write(separator);
                    separator.rewind();
                }
                fileChannel.close();
                file.close();
            } catch (IOException e) {
                logger.error(e.getMessage());
            } catch (BufferOverflowException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        }
        log = null;

    }

    public int indexOf(ByteBuffer buffer, ByteBuffer pattern) {
        int patternPos = pattern.position();
        int patternLen = pattern.remaining();
        int lastIndex = buffer.limit() - patternLen + 1;
        Label: for (int i = buffer.position(); i < lastIndex; i++) {
            for (int j = 0; j < patternLen; j++) {
                if (buffer.get(i + j) != pattern.get(patternPos + j)) {
                    continue Label;
                }
            }
            return i;
        }
        return -1;
    }
}
