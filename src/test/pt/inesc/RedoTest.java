package pt.inesc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import pt.inesc.redo.RedoNode;

public class RedoTest {
    @Test
    public void testRedo() throws IOException {
        List<Long> requestsToExecute = new ArrayList<Long>();
        requestsToExecute.add(1397380631774L);
        requestsToExecute.add(-1L);
        RedoNode redo = new RedoNode();
        redo.newRequest(requestsToExecute, (short) 69);
        System.in.read();
    }

}
