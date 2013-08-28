package pt.inesc.proxy.redo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RedoWorker
        implements Runnable {
    private final InetSocketAddress remoteHost;
    private int start;
    private int end;
    protected Socket clientSocket = null;
    private BufferedWriter out;
    private BufferedReader in;

    private BufferedReader requestFile;
    private BufferedReader responseFile;
    private LinkedList<File> requestList;
    private LinkedList<File> responseList;
    private String originalCookie;

    private final String SEPARATOR = "===";

    private static final String DIRECTOY = "./requests/";

    private static Logger logger = LogManager.getLogger("RedoWorker");

    public static CookieMan cookieManager = new CookieMan();

    public RedoWorker(int start, int end, String remoteHostname, int remotePort) throws IOException {
        super();
        remoteHost = new InetSocketAddress(InetAddress.getByName(remoteHostname),
                remotePort);
        this.start = start;
        this.end = end;
        logger.info("New Worker");
        connect();
    }

    private void connect() throws IOException {
        // Open socket to server and hold it
        clientSocket = new Socket(remoteHost.getAddress(), remoteHost.getPort());
        clientSocket.setKeepAlive(true);
        clientSocket.setSoTimeout(0);
        out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
    }

    @Override
    public void run() {
        System.out.println("time:" + new Date().getTime());

        // List and sort all request files
        requestList = getFileList("req");
        // List and sort all response files
        responseList = getFileList("res");


        // start
        for (File file : requestList) {
            try {
                processFile(file);
            } catch (FileNotFoundException e) {
                logger.error("RedoWorkeer:79:" + e.getMessage());
                e.printStackTrace();
            }
        }
        System.out.println("time:" + new Date().getTime());
    }






    private String getNextResponse() throws IOException {
        if (responseFile == null) {
            File resFile = responseList.pollFirst();
            responseFile = new BufferedReader(new FileReader(resFile));
        }
        originalCookie = "";
        String line = null;
        StringBuilder sb = new StringBuilder();
        while ((line = responseFile.readLine()) != null) {
            sb.append(line);
            if (line.equals(SEPARATOR)) {
                return sb.toString();
            } else if (line.startsWith("Set-Cookie:")) {
                // Got a Cookie
                originalCookie = line.replace("Set-Cookie:", "");
            }
        }
        // Last request, file is done
        responseFile.close();
        responseFile = null;
        return sb.toString();
    }

    private void processFile(File file) throws FileNotFoundException {
        requestFile = new BufferedReader(new FileReader(file));
        String line = null;
        StringBuilder sb = new StringBuilder();
        Boolean responseReceived = false;
        try {
            while ((line = requestFile.readLine()) != null) {
                // Cookies converter
                if (line.startsWith("Cookie:")) {
                    System.out.println(line);
                    line = line.replace("Cookie:", "");
                    line = "Cookie:" + cookieManager.toNewRequest(line);
                }
                if (line.equals(SEPARATOR)) {
                    String request = sb.toString();
                    // Full Request done

                    // Send to server
                    request = request.substring(0, request.length() - 1);
                    System.out.println(request);
                    try {
                        out.write(request);
                        out.flush();
                    } catch (IOException e) {
                        logger.warn(e.getMessage());
                        logger.warn("connecting again");
                        connect();
                        out.write(sb.toString());
                        out.flush();
                    }

                    getNextResponse();

                    responseReceived = false;
                    int contentLenght = 0;





                    StringBuilder responseB = new StringBuilder();
                    // Get response
                    try {
                        while (!responseReceived && ((line = in.readLine()) != null)) {
                            if (line.startsWith("Content-Length:")) {
                                contentLenght = Integer.parseInt(line.replaceAll("\\D+",
                                                                                 ""));
                            } else if (line.startsWith("Set-Cookie:")) {
                                String cookie = line.replace("Set-Cookie:", "");
                                // header is done
                                cookieManager.fromNewResponse(cookie, originalCookie);
                            }

                            else if (line.equals("")) {
                                responseReceived = true;
                            }
                            responseB.append(line + "\n");
                        }
                    } catch (NumberFormatException e) {
                        logger.error("Wrong conent lenght: " + line);
                    } catch (IOException e) {
                        logger.error("Error Reading remote socket: ");
                    }

                    System.out.println("RESPONSE:-----------------------");
                    System.out.println(responseB.toString());

                    if (contentLenght != 0) {
                        char[] buffer = new char[contentLenght];
                        try {
                            if (in.read(buffer, 0, contentLenght) != contentLenght) {
                                // TODO Ignore content
                                logger.error("ERROR: It must read all content");
                            }
                        } catch (IOException e) {
                            logger.error("Content Reading error:" + e.getMessage());
                        }
                    }

                    if (request.contains("Connection: close")) {
                        // Open the connection again
                        connect();
                    }

                    sb = new StringBuilder();
                    System.out.println("NEW REQUEST---------------------------------------");

                } else {
                    sb.append(line + "\n");
                }
            }
            requestFile.close();
        } catch (IOException e) {
            logger.error("File Reading error:" + e.getMessage());
        }
    }


    /**
     * Sort by ID all directory file start by "startBy"
     * 
     * @param startBy
     * @return
     */
    private LinkedList<File> getFileList(String startBy) {
        // Read requests from directory
        File folder = new File(DIRECTOY);
        LinkedList<File> listOfFiles = new LinkedList<File>();
        int id;
        String filename;
        for (File file : folder.listFiles()) {
            filename = file.getName();
            if (filename.startsWith(startBy)) {
                id = Integer.parseInt(filename.replaceAll("\\D+", ""));
                if (id >= start && id < end) {
                    listOfFiles.add(file);
                }
            }
        }
        Collections.sort(listOfFiles, new FileComparator());
        return listOfFiles;
    }

}
