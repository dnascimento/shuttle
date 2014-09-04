package pt.inesc.replay.core.handlers;

import java.io.IOException;
import java.nio.ByteBuffer;

import pt.inesc.BufferTools;

public class ResponseComparator {

    /**
     * Compare the message body.
     * 
     * @param previous buffer
     * @param new buffer
     * @return
     * @throws IOException
     */
    public static String compare(ByteBuffer prevB, ByteBuffer newB) throws IOException {
        StringBuilder sb = new StringBuilder();
        int headerEndPrevB = BufferTools.indexOf(prevB, BufferTools.NEW_LINES) + 4;
        int headerEndNewB = BufferTools.indexOf(newB, BufferTools.NEW_LINES) + 4;

        prevB.position(headerEndPrevB);
        newB.position(headerEndNewB);

        int prevlen = prevB.limit() - prevB.position();
        int newlen = newB.limit() - newB.position();
        if (prevlen == newlen) {
            int i = 0;
            for (; i < prevlen; i++) {
                if (prevB.get(headerEndPrevB + i) != newB.get(headerEndNewB + i)) {
                    break;
                }
            }
            if (i != prevlen) {
                sb.append("\n------ NEW RESPONSE ---------\n");
                BufferTools.printContent(newB, newB.position(), newB.limit());
                sb.append("\n------ OLD RESPONSE ---------\n");
                BufferTools.printContent(prevB, prevB.position(), prevB.limit());
            }
        }
        return sb.toString();

    }
}
