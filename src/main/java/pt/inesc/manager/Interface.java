package pt.inesc.manager;

import java.io.IOException;
import java.util.Scanner;


public class Interface {
    private final Manager manager;

    public static void main(String[] args) throws IOException {
        Manager manager = new Manager();
        Interface menu = new Interface(manager);
        menu.show();
    }

    public Interface(Manager manager) {
        super();
        this.manager = manager;
    }



    public void show() {
        System.out.println("INESC Undo Manager");
        manager.showGraph();
        Scanner s = new Scanner(System.in);
        while (true) {
            System.out.println("-------------------------------");
            System.out.println("a) Do Snapshot");
            System.out.println("b) Recover from Snapshot");
            System.out.println("c) List Snapshots");
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
            // case 'c':
            // listSnapshots();
            // break;
            case 'd':
                System.out.println("Enter the root:");
                long root = s.nextLong();
                manager.redoFromRoot(root);
            default:
                System.out.println("Invalid Option");
                break;
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
