package pt.inesc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.http.HttpRequest;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import pt.inesc.proxy.Proxy;
import pt.inesc.proxy.ReqType;

public class BufferTools {
    private static final Logger logger = LogManager.getLogger(BufferTools.class.getName());
    private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");
    private static final ByteBuffer LAST_CHUNK = ByteBuffer.wrap(new byte[] { 48, 13, 10, 13, 10 });
    public static final ByteBuffer NEW_LINES = ByteBuffer.wrap(new byte[] { 13, 10, 13, 10 });
    public static final ByteBuffer CONTENT_LENGTH = ByteBuffer.wrap("Content-Length: ".getBytes());
    public static final ByteBuffer CHUNKED = ByteBuffer.wrap("Transfer-Encoding: chunked".getBytes());

    private static final ByteBuffer ID_MARK = ByteBuffer.wrap("Id: ".getBytes());
    public static final ByteBuffer SEPARATOR = ByteBuffer.wrap(new byte[] { 13, 10 });
    private static final byte[] STATUS_304 = "304".getBytes();
    private static final ByteBuffer CONNECTION = ByteBuffer.wrap(("Connection: ").getBytes());
    private static final ByteBuffer HTTP1 = ByteBuffer.wrap(("1.1").getBytes());
    private static final int ID_SIZE = 16;
    private static final String JSON = "application/json";


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
        byte firstByteInPattern = pattern.get(0);
        Label: for (int i = startPosition; i < lastIndex; i++) {
            if (buffer.get(i) != firstByteInPattern) {
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

    /**
     * Returns the index of the first character of the pattern
     * 
     * @param buffer
     * @param pattern
     * @return
     */
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
     * @throws Exception
     */
    public static int extractMessageTotalSize(int start, int headerEnd, ByteBuffer buffer) {
        int pos = indexOf(start, headerEnd, buffer, CONTENT_LENGTH);
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
        contentLenght += headerEnd;
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

    /**
     * Print with the specifications
     * 
     * @param buffer
     */
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

    public static boolean isChunkedRequest(ByteBuffer clientRequestBuffer) {
        return BufferTools.indexOf(clientRequestBuffer, CHUNKED) != -1;
    }

    public static boolean isKeepAlive(ByteBuffer buffer, int endOfHeader) {
        int index = BufferTools.indexOf(buffer, CONNECTION);
        if (index == -1) {
            return isHTTP1(buffer, endOfHeader);
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

    public static long getId(ByteBuffer buffer) {
        int pos = indexOf(buffer, ID_MARK);
        if (pos == -1) {
            return -1;
        }
        pos += +ID_MARK.capacity();
        int i = 0;
        byte b;
        List<Byte> lenght = new ArrayList<Byte>();
        while ((b = buffer.get(pos + i++)) != (byte) 13) {
            lenght.add(b);
        }
        String txt = decodeUTF8(lenght);
        long id = Long.parseLong(txt);
        return id;
    }

    public static int getHeaderEnd(int lastSizeAttemp, int position, ByteBuffer clientRequestBuffer) {
        return indexOf(lastSizeAttemp, clientRequestBuffer.position(), clientRequestBuffer, NEW_LINES);
    }

    public static boolean startsWith(ByteBuffer buffer, ByteBuffer pattern) {
        int patternLen = pattern.limit();
        for (int j = 0; j < patternLen; j++) {
            if (buffer.get(j) != pattern.get(j))
                return false;
        }
        return true;
    }

    /**
     * Search all possible message header ids
     * 
     * @param buffer
     * @return
     */
    public static ArrayList<Long> getIds(ByteBuffer buffer) {
        ArrayList<Long> ids = new ArrayList<Long>();

        for (int i = buffer.position(); i < buffer.limit(); i++) {
            // match the ID:
            int pos = indexOf(i, buffer.limit(), buffer, ID_MARK);
            if (pos == -1) {
                // no more ids
                return ids;
            }
            // extract the ID value
            int j = 0;
            byte b;
            List<Byte> lenght = new ArrayList<Byte>();
            while ((b = buffer.get(pos + ID_MARK.capacity() + j++)) != (byte) 13) {
                lenght.add(b);
            }
            String idString = decodeUTF8(lenght);
            if (idString.length() != ID_SIZE) {
                logger.error("The ID: " + idString + " has a wrong size. Extracted from:\n " + printContent(buffer));
            } else {
                ids.add(Long.parseLong(idString));
            }
            // continue
            i = pos + j;
        }
        return ids;
    }

    public static ByteBuffer createBaseHeader() {
        ByteBuffer header = ByteBuffer.allocate(47);
        ID_MARK.rewind();
        header.put(ID_MARK);
        ID_MARK.rewind();
        header.put("0000000000000000".getBytes());
        header.put(SEPARATOR);
        SEPARATOR.rewind();
        header.put("B: ".getBytes());
        header.put(Proxy.branch); // 5bytes
        // not restraint
        header.put(SEPARATOR);
        SEPARATOR.rewind();
        header.put("R: f".getBytes());
        // not redo
        header.put(SEPARATOR);
        SEPARATOR.rewind();
        header.put("Redo: f".getBytes());
        header.put(SEPARATOR);
        SEPARATOR.rewind();
        header.position(0);
        return header;
    }



    public static void modifyHeader(ByteBuffer header, long startTS, long timeTravel, byte[] branch, boolean restrain, boolean replay) {
        int initialPos = header.position();
        int headerOffset = initialPos;
        if (initialPos != 0) {
            headerOffset = indexOf(header.position(), header.limit(), header, ID_MARK);
        }

        // modify time?
        if (startTS != -1) {
            byte[] ts = Long.valueOf(startTS + timeTravel).toString().getBytes();
            header.position(4 + headerOffset);
            header.put(ts);
        }

        // modify branch
        header.position(25 + headerOffset);
        header.put(branch);

        // modify restrain
        header.position(35 + headerOffset);
        if (restrain) {
            header.put((byte) 't');
        } else {
            header.put((byte) 'f');
        }

        // modify replay
        header.position(44 + headerOffset);
        if (replay) {
            header.put((byte) 't');
        } else {
            header.put((byte) 'f');
        }

        // set ready to send
        header.position(initialPos);
    }

    /**
     * Convert a short to byte array including the leading zeros and using 1 byte per char
     * encode
     * 
     * @param s
     * @return
     */
    public static byte[] shortToByteArray(int s) {
        byte[] r = new byte[5];
        int base = 10000;
        int tmp;
        for (short i = 0; i < 5; i++) {
            tmp = (s / base);
            r[i] = (byte) (tmp + '0');
            s -= tmp * base;
            base /= 10;
        }
        return r;
    }


    public static HttpRequest bufferToRequest(ByteBuffer request) throws UnsupportedEncodingException {
        int i = request.position();
        int start = i;
        LinkedList<String> headerLines = new LinkedList<String>();
        String reqBody = null;
        do {
            if (request.get(i++) == SEPARATOR.get(0) && request.get(i++) == SEPARATOR.get(1)) {
                byte[] line;
                if (request.get(start) == SEPARATOR.get(0) && request.get(start + 1) == SEPARATOR.get(1)) {
                    // request body
                    if ((start + 2) != request.limit()) {
                        request.position(start + 2); // set to first char
                        line = new byte[request.limit() - request.position()];
                        request.get(line, 0, line.length);
                        String s = new String(line);
                        reqBody = s;
                    }
                    break;
                }
                // header line
                line = new byte[i - start - 2];
                request.get(line, 0, line.length);
                start = i;
                request.position(start);
                String s = new String(line);
                headerLines.add(s);
            }
        } while (i != request.limit());


        String[] top = headerLines.removeFirst().split(" ");
        ReqType type = ReqType.valueOf(top[0]);
        String url = top[1];

        HttpRequestBase httpRequest = null;
        switch (type) {
        case GET:
            httpRequest = new HttpGet(url);
            break;
        case DELETE:
            httpRequest = new HttpDelete(url);
            break;
        case PUT:
            httpRequest = new HttpPut(url);
            break;
        case POST:
            httpRequest = new HttpPost(url);
        }

        String contentType = null;
        for (String line : headerLines) {
            int split = line.indexOf(':');
            String headerEntryName = line.substring(0, split);
            String headerEntryValue = line.substring(split + 2, line.length());

            if (headerEntryName.equals("Content-Length")) {
                continue;
            }
            if (headerEntryName.equals("Content-Type")) {
                contentType = headerEntryValue;
            }
            httpRequest.setHeader(headerEntryName, headerEntryValue);
        }

        if (contentType == null) {
            assert (reqBody == null);
        } else {
            ((HttpEntityEnclosingRequestBase) httpRequest).setEntity(new StringEntity(reqBody));
        }


        return httpRequest;
    }
}
