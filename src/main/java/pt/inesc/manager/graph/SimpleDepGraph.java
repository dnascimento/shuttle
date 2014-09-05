package pt.inesc.manager.graph;

import java.util.List;



public class SimpleDepGraph extends
        DepGraph {
    private static final long serialVersionUID = 1L;

    @Override
    public void addNode(Long from, Long to) {



        Dependency fromNode = getOrCreateNode(from);
        Dependency toNode = getOrCreateNode(to);

        // avoid double linked edges
        if (toNode.isAfter(from)) {
            return;
        }

        boolean isNew = fromNode.addAfter(to);
        if (isNew) {
            toNode.countBefore++;
        }
    }

    @Override
    public List<List<Long>> selectiveReplayList(long baseCommit, List<Long> attackSource) throws Exception {
        throw new Exception("Not supported");
    }

    @Override
    public void removeNode(Long from, Long to) {
        Dependency nextNode = getNode(to);
        nextNode.countBefore--;
    }



}
