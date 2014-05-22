package pt.inesc.redo.core.handlers;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
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
        int headerEndPrevB = BufferTools.indexOf(prevB, BufferTools.NEW_LINES);
        int headerEndNewB = BufferTools.indexOf(newB, BufferTools.NEW_LINES);
        int prevBOldPos = prevB.position();
        int newBOldPos = newB.position();

        prevB.position(headerEndPrevB);
        newB.position(headerEndNewB);


        BufferedReader bPrev = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(prevB.array())));
        BufferedReader bNew = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(newB.array())));
        String prevLine, newLine;
        while ((prevLine = bPrev.readLine()) != null) {
            newLine = bNew.readLine();
            if (!prevLine.equals(newLine)) {
                sb.append("+");
                sb.append(newLine);
                sb.append("\n");
                sb.append("-");
                sb.append(prevLine);
                sb.append("\n");
            }
        }
        while ((newLine = bNew.readLine()) != null) {
            sb.append("+");
            sb.append(newLine);
            sb.append("\n");
            sb.append("-");
            sb.append(" ");
            sb.append("\n");
        }

        bNew.close();
        bPrev.close();

        prevB.position(prevBOldPos);
        newB.position(newBOldPos);
        return sb.toString();

    }
}
