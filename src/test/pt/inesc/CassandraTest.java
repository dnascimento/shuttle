/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import pt.inesc.proxy.save.CassandraClient;
import pt.inesc.proxy.save.Request;
import voldemort.undoTracker.KeyAccess;
import voldemort.utils.ByteArray;


public class CassandraTest {

    CassandraClient client = new CassandraClient();

    @Test
    public void Test() {
        long rid = 2;
        byte[] string = "darionascimento".getBytes();
        ByteBuffer data = ByteBuffer.wrap(string);
        Request pack = new Request(data, rid);
        client.putRequest(pack);
        ByteBuffer result = client.getRequest(rid);
        assertEquals(result.limit() - result.position(), data.capacity());
        boolean equals = true;

        while (result.hasRemaining()) {
            if (result.get() != data.get()) {
                equals = false;
                break;
            }
        }
        assertTrue(equals);
    }

    @Test
    public void keys() {
        Set<KeyAccess> l = new HashSet<KeyAccess>();
        l.add(new KeyAccess(new ByteArray("awesome".getBytes()), "store"));
        l.add(new KeyAccess(new ByteArray("cool".getBytes()), "store"));
        l.add(new KeyAccess(new ByteArray("magnific".getBytes()), "store"));
        client.addKeys(l, 69L);

        Set<KeyAccess> l2 = client.getKeys(69L);
        l2.removeAll(l);
        assertTrue(l2.isEmpty());
    }
}
