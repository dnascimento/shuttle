package pt.inesc;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.util.StringTokenizer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class WorkerRunnable
        implements Runnable {

    private static Logger log = LogManager.getLogger("UndoThread");

    public static int BUFFER_SIZE = 100000;
    protected Socket clientSocket = null;
    private String destinationHost;
    private int destinationPort;


    public WorkerRunnable(Socket clientSocket, String destinationHost, int destinationPort) {
        super();

        log.debug("new thread");
        this.clientSocket = clientSocket;
        this.destinationHost = destinationHost;
        this.destinationPort = destinationPort;
    }





    @Override
    public void run() {
        try {
            log.debug("new thread");
            DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    clientSocket.getInputStream()));

            String inputLine, outputLine;
            int cnt = 0;
            String urlToCall = "";
            String method = "";
            // read request
            while ((inputLine = in.readLine()) != null) {
                /*
                 * GET /agenda/login HTTP/1.1 Host: 127.0.0.1:9000 Connection: keep-alive
                 * Accept: text/html,application/xhtml+xml,application/xml;q=0.9,;q=0.8
                 * User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_8_4)
                 * AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.95
                 * Safari/537.36 Accept-Encoding: gzip,deflate,sdch Accept-Language:
                 * en-US,en;q=0.8 Cookie: JSESSIONID=D0C25BDA5F848AD17C62153958F82F60
                 */
                log.debug(inputLine);
                try {
                    StringTokenizer tok = new StringTokenizer(inputLine);
                    tok.nextToken();
                } catch (Exception e) {
                    break;
                }
                // parse the first line of the request to find the url
                if (cnt == 0) {
                    String[] tokens = inputLine.split(" ");
                    method = tokens[0];
                    urlToCall = tokens[1];
                }
                cnt++;
            }

            // send request to server
            BufferedReader rd = null;

            URL url = new URL("http://" + destinationHost + ":" + destinationPort
                    + urlToCall);
            log.info("sending request to real server for url: " + url);
            if (urlToCall == "") {
                log.error("EMPTY URL");
            }

            URLConnection conn = url.openConnection();
            conn.setDoInput(true);
            // get response
            InputStream is = null;
            HttpURLConnection huc = (HttpURLConnection) conn;
            if (conn.getContentLength() > 0) {
                try {
                    is = conn.getInputStream();
                    rd = new BufferedReader(new InputStreamReader(is));
                } catch (IOException ioe) {
                    log.error("********* IO EXCEPTION **********: " + ioe);
                }
            }
            // send response to client
            byte by[] = new byte[BUFFER_SIZE];
            try {
                int index = is.read(by, 0, BUFFER_SIZE);
                while (index != -1) {
                    out.write(by, 0, index);
                    index = is.read(by, 0, BUFFER_SIZE);
                }
                out.flush();
            } catch (NullPointerException e) {
                log.error("Destination host is not available");
            }

            // close out all resources
            if (rd != null) {
                rd.close();
            }
            if (out != null) {
                out.close();
            }
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
