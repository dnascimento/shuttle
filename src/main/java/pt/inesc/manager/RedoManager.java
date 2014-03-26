package pt.inesc.manager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

import pt.inesc.shared.RedoPB.ExecList;

/**
 * Retrieve the execution lists and invoke redo nodes to perform these lists
 */
public class RedoManager {
    // TODO registry of nodes with an open server socket

    public void executeList(List<Long> execList) throws IOException {
        ExecList list = ExecList.newBuilder().addAllRid(execList).build();
        sendList(list);
    }

    /**
     * Send list of request ids to re-execute to a remote host
     * 
     * @param list
     * @throws IOException
     */
    private void sendList(ExecList list) throws IOException {
        InetSocketAddress endpoint = getFreeEndPoint();
        Socket socket = new Socket();
        socket.connect(endpoint);
        list.writeTo(socket.getOutputStream());
        socket.close();
    }

    /**
     * Get a free registered node
     * 
     * @return
     */
    private InetSocketAddress getFreeEndPoint() {
        // TODO dos nos registados escolher um livre
        return new InetSocketAddress("localhost", 9050);
    }
}
