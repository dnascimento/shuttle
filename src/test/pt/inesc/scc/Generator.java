package pt.inesc.scc;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

public class Generator {

    public static void main(String[] args) {
        int V = 8000000;
        int E = 8000000;


        /** make graph **/
        List<Integer>[] g = new List[V];
        for (int i = 0; i < V; i++)
            g[i] = new ArrayList<Integer>();

        Random rand = new Random();
        for (int i = 0; i < E; i++) {
            int x = rand.nextInt(V);
            int y = rand.nextInt(V);
            g[x].add(y);
        }


        Date start;
        List<List<Integer>> scComponents;
        Date end;
        // Tarjan
        start = new Date();
        Tarjan t = new Tarjan();
        /** print all strongly connected components **/
        scComponents = t.getSCComponents(g);
        end = new Date();
        System.out.println("Tarjan: " + (end.getTime() - start.getTime()));
        List<List<Integer>> resultTarjan = scComponents;

        // Gabow
        start = new Date();
        Gabow gab = new Gabow();
        scComponents = gab.getSCComponents(g);
        end = new Date();
        System.out.println("Gabown: " + (end.getTime() - start.getTime()));
        List<List<Integer>> resultGab = scComponents;

        // Tarjan
        start = new Date();
        t = new Tarjan();
        /** print all strongly connected components **/
        scComponents = t.getSCComponents(g);
        end = new Date();
        System.out.println("Tarjan: " + (end.getTime() - start.getTime()));
        resultTarjan = scComponents;

        start = new Date();
        gab = new Gabow();
        scComponents = gab.getSCComponents(g);
        end = new Date();
        System.out.println("Gabown: " + (end.getTime() - start.getTime()));
        resultGab = scComponents;

        if (resultGab.size() != resultTarjan.size()) {
            System.out.println("Different sizes");
        }

        for (int i = 0; i < resultGab.size(); i++) {
            if (resultGab.get(i).size() != resultTarjan.get(i).size()) {
                System.out.println("Different sizes");
            }
        }

    }
}
