package pt.inesc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketAddress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class UndoHandler
        implements HttpHandler {
    private static Logger log = LogManager.getLogger("UndoHandler");

    private SocketAddress destinationAddress;


    public UndoHandler(SocketAddress destinationAddress) {
        this.destinationAddress = destinationAddress;
    }

    @Override
    public void handle(HttpExchange t) throws IOException {
        log.info("new request");
        try {
            Socket realSocket = new Socket();
            // Timeout 10sec
            realSocket.connect(destinationAddress, 10000);
            BufferedWriter outReal = new BufferedWriter(new OutputStreamWriter(
                    realSocket.getOutputStream()));
            BufferedReader inReal = new BufferedReader(new InputStreamReader(
                    realSocket.getInputStream()));

            String inputLine;
            System.out.println(t.);
            outReal.write(t.toString());
            outReal.flush();

            System.out.println("Send response back");
            OutputStream os = t.getResponseBody();


            while ((inputLine = inReal.readLine()) != null) {
                os.write(inputLine.getBytes());
                os.flush();
            }
            // close out all resources

            os.close();

            if (outReal != null) {
                outReal.close();
            }
            if (inReal != null) {
                inReal.close();
            }
        } catch (IOException e) {
            System.out.println("Closed");
            e.printStackTrace();
        }
    }
}
