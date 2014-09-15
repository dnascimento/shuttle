package pt.inesc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public class BufferTools {
    private static final Logger logger = LogManager.getLogger(BufferTools.class.getName());
    private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");
    private static final ByteBuffer LAST_CHUNK = ByteBuffer.wrap(new byte[] { 48, 13, 10, 13, 10 });
    public static final ByteBuffer NEW_LINES = ByteBuffer.wrap(new byte[] { 13, 10, 13, 10 });
    public static final ByteBuffer CONTENT_LENGTH = ByteBuffer.wrap("Content-Length: ".getBytes());
    public static final ByteBuffer SEPARATOR = ByteBuffer.wrap(new byte[] { 13, 10 });
    private static final byte[] STATUS_304 = "304".getBytes();
    private static final ByteBuffer CONNECTION = ByteBuffer.wrap(("Connection: ").getBytes());
    private static final ByteBuffer HTTP1 = ByteBuffer.wrap(("1.1").getBytes());


    private BufferTools() {
        // hide the public constructor of the utility class
    }

    /**
     * Returns the index within this buffer of the first occurrence of the specified
     * pattern buffer.
     * 
     * @param startPosition
     * @param buffer the buffer
     * @param pattern the pattern buffer
     * @return the position within the buffer of the first occurrence of the pattern
     *         buffer
     */
    public static int indexOf(int startPosition, int end, ByteBuffer buffer, ByteBuffer pattern) {
        int patternLen = pattern.limit();
        int lastIndex = end - patternLen + 1;
        Label: for (int i = startPosition; i < lastIndex; i++) {
            if (buffer.get(i) != pattern.get(0)) {
                continue;
            }
            for (int j = 1; j < patternLen; j++) {
                if (buffer.get(i + j) != pattern.get(j)) {
                    continue Label;
                }
            }
            return i;
        }
        return -1;
    }


    public static String decodeUTF8(List<Byte> lenght) {
        byte[] lenghtValue = new byte[lenght.size()];
        int i = 0;
        for (byte b : lenght) {
            lenghtValue[i++] = b;
        }
        return new String(lenghtValue, UTF8_CHARSET);
    }

    public static int indexOf(ByteBuffer buffer, ByteBuffer pattern) {
        return indexOf(0, buffer.limit(), buffer, pattern);
    }

    /**
     * Exctract how long is all message.
     * 
     * @param lastSizeAttemp
     * @param end
     * @param buffer2
     * @return
     */
    public static int extractMessageTotalSize(int start, int end, ByteBuffer buffer) {
        int pos = indexOf(start, end, buffer, CONTENT_LENGTH);
        if (pos == -1) {
            return -1;
        }
        int i = 0;
        byte b;
        List<Byte> lenght = new ArrayList<Byte>();
        while ((b = buffer.get(pos + 16 + i++)) != (byte) 13) {
            lenght.add(b);
        }
        int contentLenght = Integer.parseInt(decodeUTF8(lenght));
        contentLenght += indexOf(buffer, NEW_LINES);
        // 4 newlines bytes
        contentLenght += 4;
        return contentLenght;
    }


    public static String printContent(ByteBuffer buffer, int start, int end) {
        List<Byte> content = new ArrayList<Byte>();
        for (int i = start; i < end; i++) {
            content.add(buffer.get(i));
        }
        return decodeUTF8(content);
    }

    public static void printAll(ByteBuffer buffer) {
        int end = buffer.limit();
        logger.info(printContent(buffer, 0, end));
    }

    public static String printContent(ByteBuffer buffer) {
        int start = buffer.position();
        int end = buffer.limit();
        return printContent(buffer, start, end);
    }

    public static void println(ByteBuffer buffer) {
        int position = buffer.position();

        for (int i = position; i < buffer.limit(); i++) {
            logger.info(Integer.toHexString(buffer.get(i)));
        }
        logger.info("Limit: " + buffer.limit());
        logger.info("Position" + buffer.position());
    }

    /*
     * Search for patter at end of chunk
     */
    public static boolean lastChunk(ByteBuffer buffer) {
        int pos = buffer.position() - 1;
        int end = LAST_CHUNK.capacity() - 1;
        for (int i = 0; i <= end; i++) {
            if (buffer.get(pos - i) != LAST_CHUNK.get(end - i)) {
                return false;
            }
        }
        return true;
    }

    /*
     * Search for patter at end of chunk
     */
    public static boolean headerIsComplete(ByteBuffer buffer) {
        int pos = buffer.position() - 1;
        int end = SEPARATOR.capacity() - 1;
        for (int i = 0; i <= end; i++) {
            if (buffer.get(pos - i) != SEPARATOR.get(end - i))
                return false;
        }
        return true;
    }



    /**
     * Check if 304 message
     * 
     * @param buffer
     * @param headerEnd
     * @return
     */
    public static boolean is304(ByteBuffer buffer, int headerEnd) {
        return buffer.get(9) == STATUS_304[0] && buffer.get(10) == STATUS_304[1] && buffer.get(11) == STATUS_304[2];
    }

    public static boolean isKeepAlive(ByteBuffer buffer, int endOfFirstLine) {
        int index = BufferTools.indexOf(buffer, CONNECTION);
        if (index == -1) {
            return isHTTP1(buffer, endOfFirstLine);
        }
        int letter = buffer.get(index + CONNECTION.capacity() + 1);
        // if 2nd char is a "e": Keep-Alive
        return letter == 101 || letter == 69;
    }

    private static boolean isHTTP1(ByteBuffer buffer, int endOfFirstLine) {
        return BufferTools.indexOf(endOfFirstLine - 3, endOfFirstLine, buffer, HTTP1) != -1;
    }


    @SuppressWarnings("resource")
    public static WritableByteChannel getDebugChannel() throws FileNotFoundException {
        String filename = "debug.txt";
        File temp = new File(filename);
        temp.delete();
        temp = new File(filename);
        return new RandomAccessFile(temp, "rw").getChannel();
    }
}
