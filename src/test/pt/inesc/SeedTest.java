/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */

package pt.inesc;

import java.util.Random;

import junit.framework.Assert;

import org.junit.Test;

public class SeedTest {

    int numberOfTrials = 10000;
    int numberOfExecs = 10000;

    @Test
    public void testRedo() throws Exception {
        long seed = 100L;
        double[] base = new double[numberOfExecs];

        for (int i = 0; i < numberOfExecs; i++) {
            base[i] = randomGenerator(seed);
        }

        while (numberOfTrials-- > 0) {
            for (int i = 0; i < numberOfExecs; i++) {
                Assert.assertEquals(base[i], randomGenerator(seed));
            }
        }
    }

    double randomGenerator(long seed) {
        Random generator = new Random(seed);
        return generator.nextDouble();
    }
}
