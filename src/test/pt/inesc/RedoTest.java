package pt.inesc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import pt.inesc.redoNode.RedoScheduler;

public class RedoTest {
    @Test
    public void testRedo() throws IOException {
        List<Long> requestsToExecute = new ArrayList<Long>();
        requestsToExecute.add(1395828275566L);
        requestsToExecute.add(-1L);
        requestsToExecute.add(1395828275566L);
        requestsToExecute.add(-1L);
        RedoScheduler redo = new RedoScheduler();
        redo.newRequest(requestsToExecute);
    }
}
