package pt.inesc.manager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.TreeMap;

import pt.inesc.manager.redo.RedoScheduler;

public class Manager {
    private static TreeMap<Integer, LinkedList<String>> snapshotList = new TreeMap<Integer, LinkedList<String>>();
    static BufferedReader terminal = new BufferedReader(new InputStreamReader(System.in));



    public static void main(String[] args) throws Exception {
        System.out.println("INESC Undo Manager");
        while (true) {
            System.out.println("-------------------------------");
            System.out.println("a) Do Snapshot");
            System.out.println("b) Recover from Snapshot");
            System.out.println("c) List Snapshots");
            System.out.println("d) Redo");

            char option = terminal.readLine().charAt(0);

            switch (option) {
            case 'a':
                doSnapshot();
                break;
            case 'b':
                recoverSnapshot();
                break;
            case 'c':
                listSnapshots();
                break;
            case 'd':
                redo();
                break;
            default:
                System.out.println("Invalid Option");
                break;
            }
        }
    }

    private static void redo() {
        RedoScheduler boss = new RedoScheduler();
        boss.run();

    }

    private static void doSnapshot() throws Exception {
        System.out.println("Enter snapshot ID: ");
        int id = Integer.parseInt(terminal.readLine());

        System.out.println("Snapshot Done:");
        System.out.println("---------------------------");
    }

    private static void recoverSnapshot() throws Exception {
        listSnapshots();
        System.out.println("Choose snapshot ID: ");
        int id = Integer.parseInt(terminal.readLine());
        // Check if belongs to snapshot list
        if (!snapshotList.keySet().contains(id)) {
            System.out.println("Invalid snapshot Id");
            return;
        }



        System.out.println("Load Done");
    }




    private static void listSnapshots() {
        System.out.println("Snapshots ID's:");
        for (Integer id : snapshotList.keySet()) {
            System.out.println(id);
        }
    }
}
