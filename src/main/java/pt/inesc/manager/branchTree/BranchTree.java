package pt.inesc.manager.branchTree;


import java.util.Iterator;
import java.util.LinkedList;

public class BranchTree {
    public static final long INIT_COMMIT = 0L;
    public static final short INIT_BRANCH = 0;
    public short currentBranch = INIT_BRANCH;




    /**
     * Track the most recent snapshot of each branch
     */
    LinkedList<BranchNode> branches;
    LinkedList<Long> snapshots;

    public BranchTree() {
        branches = new LinkedList<BranchNode>();
        snapshots = new LinkedList<Long>();
        branches.addLast(new BranchNode(INIT_COMMIT, INIT_BRANCH, null));
        snapshots.addLast(INIT_COMMIT);
    }

    /**
     * Track the path described by one of branches
     * 
     * @param snapshot
     * @param branch
     * @return
     * @throws Exception
     */
    public LinkedList<BranchNode> getPath(long snapshot, short branch) throws Exception {
        BranchNode c = null;
        for (BranchNode n : branches) {
            if (n.snapshot == snapshot && n.branch == branch) {
                c = n;
                break;
            }
        }
        if (c == null) {
            throw new Exception("Can not find the path of provided snapshot and branch");
        }
        LinkedList<BranchNode> path = new LinkedList<BranchNode>();
        while (c != null) {
            path.add(c);
            c = c.parent;
        }
        return path;
    }

    /**
     * Fork a snapshot/branch pair to a new branch
     * 
     * @param parentSnapshot
     * @param parentBranch
     * @param newSnapshot
     * @return the path of the new branch
     * @throws Exception
     */
    public short fork(long parentSnapshot, short parentBranch) throws Exception {
        currentBranch++;
        BranchNode c = null;
        for (BranchNode n : branches) {
            if (n.branch == parentBranch) {
                c = n;
                break;
            }
        }

        while (c != null && c.snapshot != parentSnapshot) {
            c = c.parent;
        }
        if (c == null) {
            throw new Exception("Can not find the required snapshot and branch to fork");
        }

        BranchNode newNode = new BranchNode(parentSnapshot, currentBranch, c);
        branches.addLast(newNode);
        return currentBranch;
    }

    /**
     * Add a new snapshot to the branch
     * 
     * @param snapshot
     * @param branch
     * @throws Exception
     */
    public void addSnapshot(long snapshot) throws Exception {
        BranchNode c = null;
        Iterator<BranchNode> it = branches.iterator();

        while (it.hasNext()) {
            c = it.next();
            if (c.branch == currentBranch) {
                break;
            }
        }

        if (c == null) {
            throw new Exception("Can not find the required branch to create a new snapshot");
        }
        it.remove();
        BranchNode newNode = new BranchNode(snapshot, currentBranch, c);
        branches.addLast(newNode);
        snapshots.addLast(snapshot);
    }

    @Override
    public String toString() {
        return "BranchTree [currentBranch=" + currentBranch + ", branches=" + branches + "]";
    }

    public String show() {
        int nBranches = branches.size();
        int nSnapshots = snapshots.size();
        char[][] table = new char[nSnapshots][nBranches];

        // expand the path of every branch
        for (BranchNode base : branches) {
            BranchNode k = base;
            do {
                int nSnapshot = snapshots.indexOf(k.snapshot);
                table[nSnapshot][base.branch] = 'x';
                k = k.parent;
                if (k == null || k.branch != base.branch) {
                    table[nSnapshot][base.branch] = 'o';
                    break;
                }
            } while (k != null);
        }
        // set the |
        for (int i = 0; i < nBranches; i++) {
            boolean set = false;
            for (int k = 0; k < nSnapshots; k++) {
                if (table[k][i] == 'o') {
                    set = true;
                }
                if (table[k][i] == '\u0000' && set) {
                    table[k][i] = '|';
                }
            }
        }

        // display
        StringBuilder sb = new StringBuilder();
        sb.append("               ");
        for (int k = 0; k < nSnapshots; k++) {
            sb.append(String.format("%02d", k));
        }
        sb.append("\n");
        for (int k = 0; k < nSnapshots; k++) {
            sb.append(String.format("%013d", snapshots.get(k)));
            sb.append(" :");
            for (int i = 0; i < nBranches; i++) {
                sb.append(table[k][i]);
                sb.append(' ');
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
