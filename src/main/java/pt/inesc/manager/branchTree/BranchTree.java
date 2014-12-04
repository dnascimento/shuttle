package pt.inesc.manager.branchTree;


import java.util.ArrayList;
import java.util.List;

import javax.management.RuntimeErrorException;

import pt.inesc.proxy.ProxyWorker;
import voldemort.undoTracker.branching.BranchPath;

import com.google.common.collect.ArrayListMultimap;

public class BranchTree {
    public static final long INIT_SNAPSHOT = 0L;
    public static final short INIT_BRANCH = 0;
    public short currentBranch = INIT_BRANCH;

    ArrayListMultimap<Short, Long> branchTree = ArrayListMultimap.create();

    public BranchTree() {
        branchTree.put(INIT_BRANCH, INIT_SNAPSHOT);
    }



    /**
     * Fork a snapshot/branch pair to a new branch and create a new snapshot
     * 
     * @param parentSnapshot
     * @param parentBranch
     * @param newSnapshot
     * @return the path of the new branch
     * @throws Exception
     */
    public BranchPath fork(long parentSnapshot) throws Exception {
        currentBranch++;
        short parentBranch = findOwnerBranch(parentSnapshot);
        List<Long> versionsOfParent = branchTree.get(parentBranch);
        List<Long> newList = copyList(versionsOfParent, parentSnapshot);
        // copy the version list of parents
        branchTree.putAll(currentBranch, newList);
        // new snapshot
        long latestVersion = snapshot(0);

        return new BranchPath(currentBranch, latestVersion, branchTree.get(parentBranch));
    }

    private List<Long> copyList(List<Long> parentList, long parentSnapshot) {
        ArrayList<Long> copy = new ArrayList<Long>();
        for (Long v : parentList) {
            if (v <= parentSnapshot) {
                copy.add(v);
            }
        }
        return copy;
    }



    /**
     * Each snapshot is written only in one branch, its owner. A snapshot may exist in
     * various branches as parentSnapshot but only in one it is not the parent.
     * 
     * @param parentSnapshot
     * @return
     * @throws Exception
     */
    private short findOwnerBranch(long parentSnapshot) throws Exception {
        for (Short branch : branchTree.keys()) {
            List<Long> snapshots = branchTree.get(branch);

            if (snapshots.contains(new Long(parentSnapshot))) {
                long min = Long.MAX_VALUE;
                for (Long next : snapshots) {
                    if (next < min) {
                        min = next;
                    }
                }
                if (min != parentSnapshot) {
                    return branch;
                }
            }
        }
        throw new Exception("Branch of parent snapshot not found");
    }

    /**
     * Add a new snapshot to the branch
     * 
     * @param snapshot
     * @param branch
     * @return
     * @throws Exception
     */
    public long snapshot(long delaySeconds) throws Exception {
        long currentInstant = ProxyWorker.getTimestamp();
        currentInstant += delaySeconds * ProxyWorker.MULTIPLICATION_FACTOR * 1000;
        if (ProxyWorker.countDigits(currentInstant) != ProxyWorker.TIMESTAMP_SIZE) {
            throw new RuntimeErrorException(null, "The snapshot timestamp is invalid");
        }
        branchTree.get(currentBranch).add(currentInstant);
        return currentInstant;
    }

    @Override
    public String toString() {
        return "BranchTree [currentBranch=" + currentBranch + "]";
    }

    public String show() {
        StringBuilder sb = new StringBuilder();
        for (Short branch : branchTree.keys()) {
            sb.append("\nBranch: ");
            for (Long snapshot : branchTree.get(branch)) {
                sb.append(snapshot.toString() + ", ");
            }
        }
        sb.append("\n");
        return sb.toString();
    }
}
