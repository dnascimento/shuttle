package pt.inesc;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public class BufferTools {
    private static Logger logger = LogManager.getLogger(BufferTools.class.getName());
    private final static Charset UTF8_CHARSET = Charset.forName("UTF-8");
    private final static ByteBuffer LAST_CHUNK = ByteBuffer.wrap(new byte[] { 48, 13, 10, 13, 10 });
    public final static ByteBuffer NEW_LINES = ByteBuffer.wrap(new byte[] { 13, 10, 13, 10 });
    public final static ByteBuffer CONTENT_LENGTH = ByteBuffer.wrap("Content-Length: ".getBytes());
    public final static ByteBuffer SEPARATOR = ByteBuffer.wrap(new byte[] { 13, 10 });
    private final static byte[] STATUS_304 = "304".getBytes();

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
            if (buffer.get(i) == pattern.get(0)) {
                for (int j = 1; j < patternLen; j++) {
                    if (buffer.get(i + j) != pattern.get(j)) {
                        continue Label;
                    }
                }
                return i;
            }
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
        contentLenght += 4; // 4 newlines bytes
        return contentLenght;
    }


    public static String printContent(ByteBuffer buffer, int start, int end) {
        List<Byte> content = new ArrayList<Byte>();
        for (int i = start; i < end; i++) {
            content.add(buffer.get(i));
        }
        return decodeUTF8(content);
    }

    public static String printContent(ByteBuffer buffer) {
        int start = buffer.position();
        int end = buffer.limit();
        return printContent(buffer, start, end);
    }

    public static void println(ByteBuffer buffer) {
        int position = buffer.position();

        for (int i = position; i < buffer.limit(); i++) {
            System.out.print(Integer.toHexString(buffer.get(i)));
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
            if (buffer.get(pos - i) != LAST_CHUNK.get(end - i))
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
        return (buffer.get(9) == STATUS_304[0] && buffer.get(10) == STATUS_304[1] && buffer.get(11) == STATUS_304[2]);

    }
}
