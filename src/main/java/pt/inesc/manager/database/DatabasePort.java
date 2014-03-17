package pt.inesc.manager.database;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import pt.inesc.shared.DatabasePB.DataAccessList;

// Retrieves the requests from database
public class DatabasePort {
    public static void main(String[] args) throws IOException {
        ServerSocket socket = new ServerSocket();
        InetSocketAddress address = new InetSocketAddress("localhost", 9090);
        socket.bind(address);

        while (true) {
            Socket newSocket = socket.accept();
            receive(newSocket);
        }
    }

    public static void receive(Socket socket) throws IOException {
        InputStream stream = socket.getInputStream();
        DataAccessList data = DataAccessList.parseFrom(stream);
        System.out.println(data);
    }
}
