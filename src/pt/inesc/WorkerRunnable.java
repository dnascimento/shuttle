package pt.inesc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.StringTokenizer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class WorkerRunnable
        implements Runnable {

    private static Logger log = LogManager.getLogger("UndoThread");

    public static int BUFFER_SIZE = 100000;
    protected Socket clientSocket = null;
    private SocketAddress destinationAddress;


    public WorkerRunnable(Socket clientSocket, SocketAddress destinationAddress) {
        super();

        log.debug("new thread");
        this.clientSocket = clientSocket;
        this.destinationAddress = destinationAddress;
    }





    @Override
    public void run() {
        try {

            log.debug("new thread");
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                    clientSocket.getOutputStream()));
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    clientSocket.getInputStream()));


            Socket realSocket = new Socket();
            // Timeout 10sec
            realSocket.connect(destinationAddress, 10000);

            BufferedWriter outReal = new BufferedWriter(new OutputStreamWriter(
                    realSocket.getOutputStream()));
            BufferedReader inReal = new BufferedReader(new InputStreamReader(
                    realSocket.getInputStream()));


            String inputLine;
            int cnt = 0;
            Boolean alive = true;

            // HEADER


            StringBuilder sb = new StringBuilder();
            System.out.println(clientSocket.isConnected());
            System.out.println(clientSocket.isClosed());
            // body data
            while ((inputLine = in.readLine()) != null) {
                /*
                 * GET /agenda/login HTTP/1.1 Host: 127.0.0.1:9000 Connection: keep-alive
                 * Accept: text/html,application/xhtml+xml,application/xml;q=0.9,;q=0.8
                 * User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_8_4)
                 * AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.95
                 * Safari/537.36 Accept-Encoding: gzip,deflate,sdch Accept-Language:
                 * en-US,en;q=0.8 Cookie: JSESSIONID=D0C25BDA5F848AD17C62153958F82F60
                 */
                sb.append(inputLine + "\n");
                try {
                    StringTokenizer tok = new StringTokenizer(inputLine);
                    tok.nextToken();
                } catch (Exception e) {
                    break;
                }
                // parse the first line of the request to find the url
                if (cnt == 0) {
                    String[] tokens = inputLine.split(" ");
                    // method = tokens[0];
                    // urlToCall = tokens[1];
                }
                cnt++;
            }
            outReal.write(sb.toString());
            outReal.flush();

            System.out.println("Send response back");
            while ((inputLine = inReal.readLine()) != null) {
                out.write(inputLine + "\n\r");
                out.flush();
            }

            out.flush();
            // close out all resources
            if (outReal != null) {
                outReal.close();
            }
            if (inReal != null) {
                inReal.close();
            }
            if (out != null) {
                out.close();
            }
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
            System.out.println("Closed");
            e.printStackTrace();
        }
    }
}
