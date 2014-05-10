package pt.inesc;

import org.junit.Test;

import pt.inesc.manager.branchTree.BranchTree;

public class BranchTreeTest {


    @Test
    public void branchTree() throws Exception {
        BranchTree tree = new BranchTree();
        tree.addCommit(21L);
        tree.addCommit(22L);
        tree.addCommit(23L);
        tree.fork(21L, (short) 0);
        tree.fork(22L, (short) 0);
        tree.addCommit(24L);
        System.out.println(tree.show());
    }
}
