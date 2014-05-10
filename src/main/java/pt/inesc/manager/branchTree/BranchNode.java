package pt.inesc.manager.branchTree;


public class BranchNode
        implements Comparable<BranchNode> {
    public final long commit;
    public final short branch;
    public final BranchNode parent;

    public BranchNode(long commit, short branch, BranchNode parent) {
        super();
        this.commit = commit;
        this.branch = branch;
        this.parent = parent;
    }

    @Override
    public String toString() {
        return "[b:" + branch + ", c:" + commit + ", p" + parent + "]";
    }

    public int compareTo(BranchNode o) {
        if (commit != o.commit) {
            return (int) (commit - o.commit);
        }
        return branch - o.branch;
    }

}
