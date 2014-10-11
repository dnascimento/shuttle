package pt.inesc;

import java.util.Arrays;

import pt.inesc.manager.graph.GraphShuttle;

public class GraphSize {



    public static void main(String[] args) {
        GraphShuttle graph = new GraphShuttle();
        for (int i = 1; i < 200313; i++) {
            graph.addDependencies(i,
                                  Arrays.asList(System.currentTimeMillis(),
                                                System.currentTimeMillis(),
                                                System.currentTimeMillis(),
                                                System.currentTimeMillis(),
                                                System.currentTimeMillis(),
                                                System.currentTimeMillis(),
                                                System.currentTimeMillis(),
                                                System.currentTimeMillis(),
                                                System.currentTimeMillis(),
                                                System.currentTimeMillis(),
                                                System.currentTimeMillis()));
            if (i % 10000 == 0) {
                System.out.println(graph.getMemorySize() / i);
            }
        }

        System.out.println(graph.getTotalByteSize());
    }
}
