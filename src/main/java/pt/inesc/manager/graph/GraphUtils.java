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
    public static List<List<Long>> getExecutionList(long baseCommit, List<Long> roots, DepGraph graph) throws Exception {
        List<List<Long>> result = new LinkedList<List<Long>>();
        for (Long rootKey : roots) {
            List<Long> execArray = getExecutionList(rootKey, baseCommit, graph);
            if (execArray != null)
                result.add(execArray);
        }
        return result;
    }

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
        DependencyArray current = new DependencyArray(rootReq);

        while (!current.isEmpty()) {
            executionList.add(current);
            DependencyArray next = new DependencyArray();
            expandEntries(current, next, graph);
            current = next;
        }
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
    private static void expandEntries(DependencyArray current, DependencyArray next, DepGraph graph) {
        DependencyArray currentExpanded = new DependencyArray();
        for (Dependency entry : current) {
            long previousEnd = entry.end;
            for (long key : entry.getAfter()) {
                Dependency req = graph.getNode(key);
                req.countBeforeTmp--;
                if (req.countBeforeTmp == 0) {
                    if (req.start < previousEnd) {
                        currentExpanded.add(req);
                        // executed with current request
                        expandEntry(req, currentExpanded, next, graph);
                    } else {
                        next.add(req);
                    }
                }
            }
        }
        current.add(currentExpanded);
    }





    private static void expandEntry(Dependency entry, DependencyArray current, DependencyArray next, DepGraph graph) {
        long previousEnd = entry.end;
        for (Long childKey : entry.getAfter()) {
            Dependency child = graph.getNode(childKey);
            child.countBeforeTmp--;
            if (child.countBeforeTmp == 0) {
                if (child.start < previousEnd) {
                    current.add(child);
                    expandEntry(child, current, next, graph);
                    previousEnd = Math.max(previousEnd, child.end);
                } else {
                    next.add(child);
                }
            }
        }
    }





    /**
     * Search cycle using DSF algorithm (LIFO)
     * 
     * @param root
     * @param possibleCicles
     */
    public static void searchCycle(Long root, Long[] possibleCiclesNexts, DepGraph graph) {
        Dependency rootEntry = graph.getNode(root);
        Iterator<Long> it = rootEntry.getAfter().iterator();
        while (it.hasNext()) {
            Long next = it.next();
            Dependency nextNode = graph.getNode(next);
            if (searchCycleAux(nextNode, root, graph)) {
                it.remove();
                nextNode.countBefore--;
            }
        }
    }

    private static Boolean searchCycleAux(Dependency nextNode, Long rootStart, DepGraph graph) {
        for (long next : nextNode.getAfter()) {
            if (next == rootStart) {
                // found cycle
                if (nextNode.start > rootStart) {
                    nextNode.getAfter().remove(rootStart);
                    graph.getNode(rootStart).countBefore--;
                    return false;
                } else {
                    return true;
                }
            }
            Dependency child = graph.getNode(next);
            if (child.end > rootStart)
                if (searchCycleAux(child, rootStart, graph))
                    return true;
        }
        return false;
    }

}
