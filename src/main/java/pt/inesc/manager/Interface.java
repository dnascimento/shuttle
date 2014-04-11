package pt.inesc.manager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import pt.inesc.proxy.save.CassandraClient;
import voldemort.client.ClientConfig;
import voldemort.client.protocol.admin.AdminClient;
import voldemort.client.protocol.admin.AdminClientConfig;
import voldemort.cluster.Node;

import com.google.common.collect.Lists;


public class Interface extends
        Thread {
    private final Manager manager;

    public Interface(Manager manager) {
        super();
        this.manager = manager;
    }



    @Override
    public void run() {
        System.out.println("INESC Undo Manager");
        manager.showGraph();
        @SuppressWarnings("resource")
        Scanner s = new Scanner(System.in);
        while (true) {
            System.out.println("-------------------------------");
            System.out.println("a) Do Snapshot:");
            System.out.println("b) Recover from Snapshot");
            System.out.println("c) Clean Cassandra");
            System.out.println("d) Clean Voldemort");
            System.out.println("e) Redo from root");
            System.out.println("f) save graph");
            System.out.println("g) reset graph");
            String line = s.nextLine();
            if (line.length() == 0)
                continue;
            char[] args = line.toCharArray();
            switch (args[0]) {
            case 'a':
                System.out.println("Enter snapshot rid:");
                long rid = s.nextLong();
                try {
                    manager.setNewSnapshotRID(rid);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case 'c':
                cleanCassandra();
                break;
            case 'd':
                cleanVoldemort();
                break;
            case 'e':
                System.out.println(manager.getRoots());
                System.out.println("Enter the root: (enter to all roots, multi separated by comma)");
                String rootArgs = s.nextLine();
                String[] tokens = rootArgs.split(",");
                long[] roots = new long[tokens.length];
                int i = 0;
                for (String t : tokens) {
                    roots[i++] = Long.parseLong(t);
                }
                manager.redoFromRoot(roots);
                break;
            case 'f':
                try {
                    manager.saveGraph();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                break;

            case 'g':
                manager.resetGraph();
                break;
            default:
                System.out.println("Invalid Option");
                break;
            }
        }
    }

    private void cleanCassandra() {
        new CassandraClient().truncatePackageTable();
    }


    private void cleanVoldemort() {
        String voldemortUrl = "tcp://localhost:6666";
        List<String> voldemortStores;

        voldemortStores = new ArrayList<String>(Arrays.asList("test",
                                                              "questionStore",
                                                              "answerStore",
                                                              "commentStore",
                                                              "index"));

        AdminClient adminClient = new AdminClient(voldemortUrl, new AdminClientConfig(), new ClientConfig());

        List<Integer> nodeIds = Lists.newArrayList();
        for (Node node : adminClient.getAdminClientCluster().getNodes()) {
            nodeIds.add(node.getId());
        }
        for (String storeName : voldemortStores) {
            for (Integer currentNodeId : nodeIds) {
                System.out.println("Truncating " + storeName + " on node " + currentNodeId);
                adminClient.storeMntOps.truncate(currentNodeId, storeName);
            }
        }
    }

    //
    // private static void doSnapshot() throws Exception {
    // System.out.println("Enter snapshot ID: ");
    // int id = Integer.parseInt(terminal.readLine());
    //
    // System.out.println("Snapshot Done:");
    // System.out.println("---------------------------");
    // }
    //
    // private static void recoverSnapshot() throws Exception {
    // listSnapshots();
    // System.out.println("Choose snapshot ID: ");
    // int id = Integer.parseInt(terminal.readLine());
    // // Check if belongs to snapshot list
    // if (!snapshotList.keySet().contains(id)) {
    // System.out.println("Invalid snapshot Id");
    // return;
    // }
    // System.out.println("Load Done");
    // }



    //
    // private static void listSnapshots() {
    // System.out.println("Snapshots ID's:");
    // for (Integer id : snapshotList.keySet()) {
    // System.out.println(id);
    // }
    // }
}
