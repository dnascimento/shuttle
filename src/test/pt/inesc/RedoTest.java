/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */

package pt.inesc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.log4j.xml.DOMConfigurator;
import org.junit.Test;

import pt.inesc.redo.RedoNode;

public class RedoTest {



    @Test
    public void testRedo() throws Exception {
        DOMConfigurator.configure("log4j.xml");
        List<Long> requestsToExecute = new ArrayList<Long>();
        requestsToExecute.addAll(Arrays.asList(1401053845727L,
                                               1401017917859L,
                                               1401017949825L,
                                               1401017951353L,
                                               -1L,
                                               1401017950803L,
                                               1401017917859L,
                                               1401017949825L,
                                               1401017951353L));
        requestsToExecute.add(-1L);
        RedoNode redo = new RedoNode();
        redo.newRequest(requestsToExecute, (short) 1, false);
        redo.startOrder();
        System.in.read();
        redo.newRequest(requestsToExecute, (short) 2, false);
        redo.startOrder();
    }

}
