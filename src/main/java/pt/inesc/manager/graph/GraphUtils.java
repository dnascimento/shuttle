package pt.inesc.manager.graph;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class GraphUtils {


    /**
     * Separator between sequence of requests
     */
    private static final Long SEPARATOR = (long) -1;




    /**
     * Iterate the graph to get the execution lists
     * 
     * @param baseCommit
     * @return A list of sequences of requests which can be performed in parallel by
     *         distinct clients. The sequences are splitted with -1 to identify the
     *         requests which cannot be executed in parallel by the same client.
     * @throws Exception if a root is invalid
     */
    public static List<List<Long>> getExecutionListSortedByDependencies(long baseCommit, List<Long> roots, DepGraph graph) throws Exception {
        List<List<Long>> result = new LinkedList<List<Long>>();
        // for each root, get the exec list.
        for (Long rootKey : roots) {
            List<Long> execArray = getExecutionList(rootKey, baseCommit, graph);
            if (execArray != null)
                result.add(execArray);
        }
        return result;
    }


    /* -------------------------------- Aux methods -------------------------------- */

    /**
     * From a root key, extract the list of requests dependent from
     * 
     * @param rootKey (a key with counter = 0
     * @param baseCommit only requests after the commit are allowed
     * @return
     * @throws Exception
     */
    private static List<Long> getExecutionList(long rootKey, long baseCommit, DepGraph graph) throws Exception {
        LinkedList<DependencyArray> executionList = new LinkedList<DependencyArray>();
        Dependency rootReq = graph.getNode(rootKey);
        DependencyArray executeNow = new DependencyArray(rootReq);
        DependencyArray executeLater = new DependencyArray();

        while (!executeNow.isEmpty()) {
            DependencyArray expanding = new DependencyArray();

            long latestEnd = 0;
            // get the latest request which will execute now
            for (Dependency entry : executeNow) {
                latestEnd = Math.max(latestEnd, entry.end);
            }

            // for each in executeNow, expandEntry
            for (Dependency entry : executeNow) {
                expandEntry(entry, latestEnd, expanding, executeLater, graph);
            }

            executeNow.add(expanding);
            executionList.add(executeNow);
            executeNow = executeLater;
            executeLater = new DependencyArray();
        }

        // Convert list to array
        int totalSize = 0;
        for (DependencyArray a : executionList) {
            totalSize += a.size();
        }

        ArrayList<Long> execArray = new ArrayList<Long>(totalSize);
        for (DependencyArray d : executionList) {
            d.sort();
            for (Dependency dep : d) {
                if (dep.start >= baseCommit) {
                    execArray.add(dep.getKey());
                }
            }
            execArray.add(SEPARATOR);
        }

        for (Long i : execArray) {
            if (i != SEPARATOR) {
                return execArray;
            }
        }
        return null;
    }

    /**
     * Load every request which have been executed concurrently with entry
     * 
     * @param entry
     * @param groupOfparallelRequest
     * @param readyHeap
     * @return
     */
    private static void expandEntry(Dependency entry, long latestEnd, DependencyArray execNow, DependencyArray execLater, DepGraph graph) {
        List<Dependency> listReadyToExec = new ArrayList<Dependency>();

        // Get the end of the latest request that will execute in this batch
        for (long key : entry.getAfter()) {
            Dependency afterEntry = graph.getNode(key);
            // vote to execute
            afterEntry.countBeforeTmp--;
            // if all dependencies are scheduled
            if (afterEntry.countBeforeTmp == 0) {
                latestEnd = Math.max(latestEnd, afterEntry.end);
                listReadyToExec.add(afterEntry);
            }
        }

        for (Dependency entryReadyToExec : listReadyToExec) {
            // if next starts before this ends
            if (entry.start < latestEnd) {
                execNow.add(entryReadyToExec);
                // executed with current request
                expandEntry(entryReadyToExec, latestEnd, execNow, execLater, graph);
            } else {
                // it will execute later, not in parallel
                execLater.add(entryReadyToExec);
            }
        }




    }







    /**
     * Search cycle using DFS algorithm (LIFO)
     * 
     * @param root
     * @param possibleCicles
     */
    public static void searchCycle(Long root, DepGraph graph) {
        Dependency rootNode = graph.getNode(root);
        searchCycleAux(rootNode, rootNode, graph);
    }

    /**
     * Given a rootNode and one of the children of the tree, do DFS to find the root node.
     * The algorithm stops if the child ends after the rootStart. In that case,
     * A node never depends from a node that started after the request ends.
     * 
     * @param nextNode
     * @param rootStart
     * @param graph
     * @return
     */
    private static void searchCycleAux(Dependency node, Dependency rootNode, DepGraph graph) {
        Iterator<Long> it = node.getAfter().iterator();
        while (it.hasNext()) {
            Dependency child = graph.getNode(it.next());
            if (child.getKey() == rootNode.getKey()) {
                // if(node.start > root){
                graph.removeNode(node.getKey(), rootNode.getKey());
                it.remove();
            } else {
                searchCycleAux(child, rootNode, graph);
            }
        }
    }


}
