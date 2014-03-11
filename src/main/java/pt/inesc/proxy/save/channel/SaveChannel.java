package pt.inesc.proxy.save.channel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import pt.inesc.proxy.save.SaveThread;

public abstract class SaveChannel extends
        SaveThread {
    private final ByteBuffer separator = ByteBuffer.wrap("\n===\n".getBytes());
    ByteBuffer connectionClose = ByteBuffer.wrap("Connection: close".getBytes());
    ByteBuffer newLine = ByteBuffer.wrap(new byte[] { 13, 10 });
    WritableByteChannel channel;

    public SaveChannel(SaveType type, int start, int end) {
        super(type, start, end);
    }

    @Override
    public void init() {
        try {
            channel = getChannel();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void save(int keyID, ByteBuffer pack) {
        try {
            ByteBuffer messageIdHeader;
            messageIdHeader = ByteBuffer.wrap(("id" + keyID + "\n").getBytes());
            channel.write(messageIdHeader);
            pack.flip();
            pack.rewind();
            channel.write(pack);
            channel.write(separator);
            newLine.rewind();
            separator.rewind();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public abstract WritableByteChannel getChannel() throws IOException;

    @Override
    public void close() {
        try {
            closeChannel();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public abstract void closeChannel() throws IOException;

}
