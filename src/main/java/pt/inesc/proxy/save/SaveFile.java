package pt.inesc.proxy.save;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public class SaveFile {
    private final String FOLDER = "requests/";
    private final String EXTENSION = ".txt";
    private final ByteBuffer separator = ByteBuffer.wrap("\n===\n".getBytes());
    ByteBuffer newLine = ByteBuffer.wrap(new byte[] { 13, 10 });
    WritableByteChannel channelRequest;
    WritableByteChannel channelResponse;
    Long ts;

    public SaveFile() {
        ts = System.currentTimeMillis();
        openChannels();
    }

    public void save(WritableByteChannel channel, long keyID, ByteBuffer pack) {
        try {
            pack.rewind();
            ByteBuffer messageIdHeader;
            messageIdHeader = ByteBuffer.wrap(("id" + keyID + "\n").getBytes());
            channel.write(messageIdHeader);
            channel.write(pack);
            channel.write(separator);
            newLine.rewind();
            separator.rewind();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void openChannels() {
        try {
            channelRequest = getChannel("req", ts.toString());
            channelResponse = getChannel("res", ts.toString());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void closeChannels() {
        try {
            channelRequest.close();
            channelResponse.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    @SuppressWarnings("resource")
    public WritableByteChannel getChannel(String type, String id) throws FileNotFoundException {
        File temp = new File(FOLDER + type + id + EXTENSION);
        RandomAccessFile file;
        file = new RandomAccessFile(temp, "rw");
        WritableByteChannel channel = file.getChannel();
        return channel;
    }

    public void putRequest(long key, ByteBuffer data) {
        save(channelRequest, key, data);

    }

    public void putResponse(long key, ByteBuffer data) {
        save(channelResponse, key, data);
    }

}
