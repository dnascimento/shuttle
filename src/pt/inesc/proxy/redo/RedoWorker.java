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
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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

    private static final String DIRECTOY = "./requests/";

    private static Logger logger = LogManager.getLogger("RedoWorker");

    public static Map<String, String> cookiesMap = new HashMap<String, String>();

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
        // Read requests from directory
        File folder = new File(DIRECTOY);
        File[] listOfFiles = folder.listFiles();
        String filename;
        int id;
        for (File file : listOfFiles) {
            filename = file.getName();
            if (filename.startsWith("req")) {
                id = Integer.parseInt(filename.replaceAll("\\D+", ""));
                if (id >= start && id < end) {
                    try {
                        processFile(file);
                    } catch (IOException e) {
                        logger.error(e.getStackTrace());
                    }
                }
            }
        }
        System.out.println("time:" + new Date().getTime());

    }

    private void processFile(File file) throws FileNotFoundException {
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line = null;
        StringBuilder sb = new StringBuilder();
        Boolean responseReceived = false;
        try {
            while ((line = reader.readLine()) != null) {
                if (line.equals("================================")) {
                    String request = sb.toString();
                    // Full Request done
                    // TODO Cookies converter



                    // Send to server
                    // System.out.println(request);
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

                    responseReceived = false;
                    int contentLenght = 0;
                    // Get response
                    try {
                        while (!responseReceived && ((line = in.readLine()) != null)) {
                            // System.out.println(line);
                            // TODO Response Filters
                            // Translate response
                            if (line.startsWith("Content-Length:")) {
                                contentLenght = Integer.parseInt(line.replaceAll("\\D+",
                                                                                 ""));
                            } else if (line.contains("Set-Cookie:")) {
                                line = line.replace("Set-Cookie", "");
                                // TODO get old response
                                // TODO get old cookie
                                // TODO save to table

                            } else if (line.equals("")) {
                                responseReceived = true;
                            }
                        }
                    } catch (NumberFormatException e) {
                        logger.error("Wrong File number: " + file.getAbsolutePath());
                    } catch (IOException e) {
                        logger.error("Error Reading remote socket: ");
                    }
                    // header is done
                    if (contentLenght != 0) {
                        char[] buffer = new char[contentLenght];
                        try {
                            if (in.read(buffer, 0, contentLenght) != contentLenght) {
                                logger.error("ERROR: It must read all content");
                            }
                        } catch (IOException e) {
                            logger.error("Content Reading error:" + e.getMessage());
                        }
                        // System.out.println(buffer);
                    }

                    if (request.contains("Connection: close")) {
                        // Open the connection again
                        connect();
                    }

                    sb = new StringBuilder();
                } else {
                    sb.append(line + "\n");
                }
            }
            reader.close();
        } catch (IOException e) {
            logger.error("File Reading error:" + e.getMessage());
        }
    }
}
