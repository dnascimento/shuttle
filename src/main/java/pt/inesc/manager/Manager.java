package pt.inesc.manager;

import java.io.IOException;
import java.net.InetSocketAddress;

import pt.inesc.manager.graph.DependencyGraph;

public class Manager {
    InetSocketAddress databasePortAddress;
    DependencyGraph graph;

    public static void main(String[] args) throws IOException {
        InetSocketAddress databasePortAddress = new InetSocketAddress("localhost", 9090);
        Manager manager = new Manager(databasePortAddress);
        manager.interf();
    }

    @SuppressWarnings("resource")
    private void interf() {
        System.out.println("Manager running....");
        System.out.println("Database port: " + databasePortAddress.getPort());
        graph.refreshableDisplay();
        System.out.println("Choose option:");
        // Scanner s = new Scanner(System.in);
        // while (true) {
        // String line = s.nextLine();
        // if (line.length() == 0)
        // continue;
        // switch (line.toCharArray()[0]) {
        // case 'a':
        // break;
        // default:
        // System.out.println("Unknown option");
        // }
        // }

    }

    public Manager(InetSocketAddress databasePortAddress) throws IOException {
        this.databasePortAddress = databasePortAddress;
        graph = new DependencyGraph();
        ServiceToDatabase serviceToDatabase = new ServiceToDatabase(graph,
                databasePortAddress);
        serviceToDatabase.start();
    }


}
