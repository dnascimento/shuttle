package pt.inesc.proxy.clientSide;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handler for client requests (client side)
 */
public class ProxyHandler extends
        ChannelInboundHandlerAdapter {

    private static final int MAX_REQUEST_PER_FILE = 10;
    private static final int MAX_RESPONSES_PER_FILE = 10;
    private InetSocketAddress remoteHost = null;
    private Socket clientSocket = null;
    private BufferedWriter out;
    private BufferedReader in;

    public static int id = 0;
    private static Logger logger = LogManager.getLogger("ProxyHandler");
    private static LinkedList<String> requests = new LinkedList<String>();
    private static Map<Integer, String> responses = new TreeMap<Integer, String>();
    private static Lock requestsMutex = new ReentrantLock();


    public ProxyHandler(String remoteHostname, int remotePort) {
        try {
            remoteHost = new InetSocketAddress(InetAddress.getByName(remoteHostname),
                    remotePort);
            connect();
            // logger.info("New Handler");
        } catch (UnknownHostException e) {
            logger.error("handler: " + e.getStackTrace());
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
    }

    private void connect() {
        // Open socket to server and hold it
        try {
            // logger.info("new connection");
            clientSocket = new Socket(remoteHost.getAddress(), remoteHost.getPort());
            clientSocket.setKeepAlive(true);
            clientSocket.setSoTimeout(0);
            out = new BufferedWriter(new OutputStreamWriter(
                    clientSocket.getOutputStream()));
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        } catch (IOException e) {
            logger.error("connect: " + e.getStackTrace());
        }
    }


    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }


    /**
     * Read request from Client and write to real
     */
    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
        ByteBuf reqBuf = (ByteBuf) msg;

        String req = reqBuf.toString(io.netty.util.CharsetUtil.US_ASCII);
        System.out.println("new request");
        int id = addRequest(req);
        // TODO Optimizar tornando o envio assincrono
        try {
            out.write(req);
            out.flush();
        } catch (IOException e) {
            logger.warn("ChannelReader: send to real fail. Reconnect");
            connect();
            try {
                out.write(req);
                out.flush();
            } catch (IOException e1) {
                logger.error("Giveup");
                return;
            }
        }


        Boolean responseReceived = false;
        String line;
        int contentLenght = 0;
        StringBuilder sb = new StringBuilder();

        // Get response
        try {
            while (!responseReceived && ((line = in.readLine()) != null)) {
                // System.out.println(line);
                // TODO Response Filters
                // Translate response
                if (line.startsWith("Content-Length:")) {
                    contentLenght = Integer.parseInt(line.replaceAll("\\D+", ""));
                } else if (line.equals("")) {
                    responseReceived = true;
                }
                sb.append(line + "\r\n");
            }

        } catch (NumberFormatException e) {
            logger.error("Wrong Content-Lenght");
        } catch (IOException e) {
            logger.error("Error Reading remote socket: ");
        }
        // header is done
        if (contentLenght != 0) {
            char[] content = new char[contentLenght];
            try {
                if (in.read(content, 0, contentLenght) != contentLenght) {
                    logger.error("ERROR: It must read all content");
                }
            } catch (IOException e) {
                logger.error("Content Reading error:" + e.getMessage());
            }
            sb.append(content);
        }
        if (msg.toString().contains("Connection: close")) {
            // Open the connection again
            connect();
        }

        String response = sb.toString();
        addResponse(response, id);


        ByteBuf data = Unpooled.copiedBuffer(response.getBytes());
        ctx.channel().writeAndFlush(data).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    ctx.channel().read();
                } else {
                    future.channel().close();
                }
            }
        });
        reqBuf.release();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        closeOnFlush(ctx.channel());
    }

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    public static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER)
              .addListener(ChannelFutureListener.CLOSE);
        }
    }



    /**
     * Add new request to Queue
     * 
     * @param request
     * @return the ID (number in queue)
     */
    public static int addRequest(String request) {
        requestsMutex.lock();
        // Exclusive zone
        int id = ProxyHandler.id++;
        requests.add(request);
        requestsMutex.unlock();
        return id;
    }


    public synchronized static void addResponse(String response, int id) {
        responses.put(id, response);
        if (responses.size() > MAX_RESPONSES_PER_FILE) {
            Map<Integer, String> responsesToSave = responses;
            responses = new HashMap<Integer, String>();

            requestsMutex.lock();
            LinkedList<String> requestsToSave = requests;
            requests = new LinkedList<String>();
            requestsMutex.unlock();

            new DataSaver(responsesToSave, id).start();
            new DataSaver(requestsToSave, id).start();
        }
    }






}
