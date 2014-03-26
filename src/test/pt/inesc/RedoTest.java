package pt.inesc;

import org.junit.Test;

import pt.inesc.redoNode.RedoScheduler;

public class RedoTest {
    @Test
    public void testRedo() {
        long[] requestsToExecute = new long[] { 1395828275566L, -1, 1395828275566L, -1 };
        RedoScheduler redo = new RedoScheduler();
        redo.newRequest(requestsToExecute);
    }
}
