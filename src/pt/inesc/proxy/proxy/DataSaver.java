package pt.inesc.proxy.proxy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DataSaver extends
        Thread {

    public Collection<ByteBuffer> log;
    public int id;
    private static Logger logger = LogManager.getLogger("DataSaver");
    private String type;
    private static ByteBuffer separator = ByteBuffer.wrap("\n================================\n".getBytes());
    ByteBuffer connectionClose = ByteBuffer.wrap("Connection: close".getBytes());


    public DataSaver(LinkedList<ByteBuffer> requests, int id) {
        log = requests;
        this.id = id;
        type = "req";

    }


    public DataSaver(Map<Integer, ByteBuffer> responsesToSave, int id) {
        log = responsesToSave.values();
        this.id = id;
        type = "res";
    }


    @Override
    public void run() {
        try {
            File temp = new File("requests/" + type + id + ".txt");
            System.out.println(temp.getAbsolutePath());
            RandomAccessFile file = new RandomAccessFile(temp, "rw");
            FileChannel fileChannel = file.getChannel();

            if (temp.exists()) {
                logger.error("ERRO!!!! File already exists");
                return;
            }
            try {
                for (ByteBuffer pack : log) {
                    int index = indexOf(pack, connectionClose);
                    if (index != -1) {
                        // TODO Retirar a string daqui
                    }
                    fileChannel.write(pack);
                    fileChannel.write(separator);
                }
                fileChannel.close();
                file.close();
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        }

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
