package pt.inesc.manager.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Stack;

/**
 * Gabow Algorithm to remove the cycles of graph
 **/

/** class Gabow **/
class GabowShuttle {
    /** preorder number counter **/
    private int preCount;
    /** to store given graph **/
    private final HashMap<Long, Dependency> graph;

    /** to store all scc **/
    private List<List<Dependency>> sccComp;
    private Stack<Dependency> stack1;
    private Stack<Dependency> stack2;

    public GabowShuttle(HashMap<Long, Dependency> graph) {
        this.graph = graph;
    }

    /** function to get all strongly connected components **/
    public List<List<Dependency>> getSCComponents() {
        Set<Long> keys = graph.keySet();

        // clean the previous state
        for (Dependency val : graph.values()) {
            val.chk = false;
            val.preorder = 0;
            val.visited = false;
        }


        stack1 = new Stack<Dependency>();
        stack2 = new Stack<Dependency>();
        sccComp = new ArrayList<List<Dependency>>();

        for (Long key : keys) {
            Dependency dep = graph.get(key);
            if (!dep.visited) {
                dfs(dep);
            }
        }
        return sccComp;
    }

    /** function dfs **/
    public void dfs(Dependency dep) {
        dep.preorder = preCount++;
        dep.visited = true;
        stack1.push(dep);
        stack2.push(dep);

        for (Long w : dep.getAfter()) {
            Dependency wVal = graph.get(w);
            if (!wVal.visited)
                dfs(wVal);
            else if (!wVal.chk) {
                while (stack2.peek().preorder > wVal.preorder) {
                    stack2.pop();
                }
            }
        }
        if (stack2.peek() == dep) {
            stack2.pop();
            List<Dependency> component = new ArrayList<Dependency>();
            Dependency w;
            do {
                w = stack1.pop();
                component.add(w);
                w.chk = true;
            } while (w != dep);

            if (component.size() > 1) {
                // only scc with more than 1 element represent a cycle
                sccComp.add(component);
            }
        }
    }
}
