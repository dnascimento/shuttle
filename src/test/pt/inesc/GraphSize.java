package pt.inesc;

import java.util.ArrayList;

import pt.inesc.manager.graph.GraphShuttle;

public class GraphSize {

    static int DEPS = 2;


    public static void main(String[] args) {
        GraphShuttle graph = new GraphShuttle(1000000);
        long counter = DEPS + 5;
        long start = System.nanoTime();
        for (int i = 1; i < 1000000; i++) {
            ArrayList<Long> deps = new ArrayList<Long>(10);
            for (int k = 0; k < DEPS; k++) {
                deps.add(counter - 2 - k);
            }
            graph.addDependencies(counter++, deps);
            // if (i % 10000 == 0) {
            // System.out.println(graph.getMemorySize() / i);
            // }
        }
        long end = System.nanoTime();
        System.out.println("duration: (ms) " + (end - start) / 1000000);
        // System.out.println(graph.getTotalByteSize());

    }
}
