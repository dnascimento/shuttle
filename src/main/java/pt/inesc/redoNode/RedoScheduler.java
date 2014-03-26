package pt.inesc.redoNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pt.inesc.redoNode.core.RedoWorker;
import pt.inesc.shared.RedoPB.ExecList;


/**
 * Pool of channels ready to connect to Real Server and get the data Then return the data
 * to original thread and continue
 */
public class RedoScheduler {

    protected ExecutorService threadPool = Executors.newFixedThreadPool(1);
    private final InetSocketAddress myServerSocketAddress;
    ServerSocket myServerSocket;

    public RedoScheduler() throws IOException {
        myServerSocketAddress = new InetSocketAddress("localhost", 9050);
        myServerSocket = new ServerSocket();
        myServerSocket.bind(myServerSocketAddress);

    }

    public void newRequest(List<Long> execList) {
        try {
            threadPool.execute(new RedoWorker(execList, "localhost", 8080));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void exec() {
        while (true) {
            Socket newSocket;
            try {
                newSocket = myServerSocket.accept();
                newConnection(newSocket);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }

    private void newConnection(Socket socket) throws IOException {
        InputStream stream = socket.getInputStream();
        ExecList list = ExecList.parseFrom(stream);
        List<Long> execList = list.getRidList();
        newRequest(execList);
    }
}
