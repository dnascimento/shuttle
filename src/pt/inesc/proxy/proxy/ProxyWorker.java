package pt.inesc.proxy.proxy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ProxyWorker
        implements Runnable {


    private static final int PACKAGE_PER_FILE = 10;

    private static Logger logger = LogManager.getLogger("ProxyWorker");
    private boolean close = false;
    public static int id = 0;
    private static LinkedList<HTTPackage> requests = new LinkedList<HTTPackage>();
    private static Map<Integer, HTTPackage> responses = new TreeMap<Integer, HTTPackage>();
    private static Lock requestsMutex = new ReentrantLock();


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
                int id = addRequest(request);
                addResponse(response, id);
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

    public synchronized static void addResponse(HTTPackage response, int id) {
        responses.put(id, response);
        if (responses.size() > PACKAGE_PER_FILE) {
            Map<Integer, HTTPackage> responsesToSave = responses;
            responses = new HashMap<Integer, HTTPackage>();

            requestsMutex.lock();
            LinkedList<HTTPackage> requestsToSave = requests;
            requests = new LinkedList<HTTPackage>();
            requestsMutex.unlock();

            new DataSaver(responsesToSave, id).start();
            new DataSaver(requestsToSave, id).start();
        }
    }


    /**
     * Add new request to Queue
     * 
     * @param request
     * @return the ID (number in queue)
     */
    public static int addRequest(HTTPackage request) {
        requestsMutex.lock();
        // Exclusive zone
        int id = ProxyWorker.id++;
        requests.add(request);
        requestsMutex.unlock();
        return id;
    }



}
