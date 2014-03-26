package pt.inesc.manager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;


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
        Scanner s = new Scanner(System.in);
        while (true) {
            try {
                System.out.println("-------------------------------");
                System.out.println("a) Do Snapshot");
                System.out.println("b) Recover from Snapshot");
                System.out.println("c) Clean Database");
                System.out.println("d) Redo from root");
                String line = s.nextLine();
                if (line.length() == 0)
                    continue;
                char[] args = line.toCharArray();
                switch (args[0]) {
                // case 'a':
                // doSnapshot();
                // break;
                // case 'b':
                // recoverSnapshot();
                // break;
                case 'c':

                    cleanDatabase();

                    break;
                case 'd':
                    System.out.println("Enter the root:");
                    long root = s.nextLong();
                    manager.redoFromRoot(root);
                default:
                    System.out.println("Invalid Option");
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void cleanDatabase() throws IOException, InterruptedException {
        String command = "../voldemort/bin/voldemort-admin-tool.sh --truncate test --url tcp://localhost:6666";
        Process p = Runtime.getRuntime().exec(command);
        p.waitFor();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                p.getInputStream()));
        String line = "";
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
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
