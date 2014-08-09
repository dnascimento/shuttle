package pt.inesc.manager.graph;

import java.util.List;



public class SimpleDepGraph extends
        DepGraph {
    private static final long serialVersionUID = 1L;

    @Override
    public void addNode(Long from, Long to) {
        Dependency fromEntry = getOrCreateNode(from);
        boolean isNew = fromEntry.addAfter(to);
        if (isNew) {
            Dependency toNode = getOrCreateNode(to);
            toNode.countBefore++;
        }
    }

    @Override
    public List<List<Long>> selectiveReplayList(long baseCommit, List<Long> attackSource) throws Exception {
        throw new Exception("Not supported");
    }



}
