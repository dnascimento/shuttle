package pt.inesc.proxy.clientSide;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ProxyWorker
        implements Runnable {

    private static Logger logger = LogManager.getLogger("ProxyWorker");
    private boolean close = false;

    Socket clientSocket = null;
    Socket realSocket = null;
    InetSocketAddress remoteHost;
    BufferedWriter realOUT;
    BufferedReader realIN;

    public ProxyWorker(Socket clientSocket, String remoteHostname, int remotePort) throws IOException {
        this.clientSocket = clientSocket;
        remoteHost = new InetSocketAddress(InetAddress.getByName(remoteHostname),
                remotePort);
        connect();
    }


    private void connect() {
        try {
            // Open socket to server and hold it
            realSocket = new Socket(remoteHost.getAddress(), remoteHost.getPort());
            realSocket.setKeepAlive(true);
            realSocket.setSoTimeout(0);
            realOUT = new BufferedWriter(new OutputStreamWriter(
                    realSocket.getOutputStream()));
            realIN = new BufferedReader(
                    new InputStreamReader(realSocket.getInputStream()));
        } catch (IOException e) {
            logger.error("Connecting to real Server" + e.getMessage());
        }
    }



    @Override
    public void run() {
        try {
            close = false;
            BufferedReader clientIN = new BufferedReader(new InputStreamReader(
                    clientSocket.getInputStream()));
            BufferedWriter clientOUT = new BufferedWriter(new OutputStreamWriter(
                    clientSocket.getOutputStream()));

            while (!close) {
                HTTPackage request = receiveHTTPacket(clientIN, realOUT);
                sendHTTPPackage(request, realOUT);
                HTTPackage response = receiveHTTPacket(realIN, clientOUT);
                sendHTTPPackage(response, clientOUT);
            }
            clientIN.close();
            clientOUT.close();
            clientSocket.close();
            realIN.close();
            realOUT.close();
            realSocket.close();
        } catch (IOException e) {
            logger.error("run: " + e.getMessage());
        }
    }


    public void sendHTTPPackage(HTTPackage frame, BufferedWriter out) throws IOException {
        out.write(frame.header);
        if (frame.body != null) {
            out.write(frame.body);
            out.write("\n");
        }
        out.flush();
    }


    public HTTPackage receiveHTTPacket(BufferedReader in, BufferedWriter out) {
        boolean requestReceived = false;
        String line;
        int contentLenght = 0;
        StringBuilder rb = new StringBuilder();
        try {
            while (!requestReceived && ((line = in.readLine()) != null)) {
                if (line.startsWith("Content-Length:")) {
                    contentLenght = Integer.parseInt(line.replaceAll("\\D+", ""));
                } else if (line.equals("Connection: close")) {
                    close = true;
                } else if (line.equals("")) {
                    requestReceived = true;
                }
                rb.append(line + "\n");
            }
            char[] body = null;
            if (contentLenght != 0) {
                body = new char[contentLenght];
                try {
                    if (in.read(body, 0, contentLenght) != contentLenght) {
                        logger.error("ERROR: It must read all content");
                    }
                } catch (IOException e) {
                    logger.error("Content Reading error:" + e.getMessage());
                }
            }
            return new HTTPackage(rb.toString(), body);
        } catch (IOException e) {
            logger.error("Error Reading remote socket: ");
        }
        return null;
    }
}
