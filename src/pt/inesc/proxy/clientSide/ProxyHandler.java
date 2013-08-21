package pt.inesc.proxy.clientSide;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handler for client requests (client side)
 */
public class ProxyHandler extends
        ChannelInboundHandlerAdapter {

    private InetSocketAddress remoteHost = null;
    private Socket clientSocket = null;
    private BufferedWriter out;
    private BufferedReader in;

    public static AtomicInteger id = new AtomicInteger(0);
    private static Logger logger = LogManager.getLogger("ProxyHandler");


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
        String req = ((ByteBuf) msg).toString(io.netty.util.CharsetUtil.US_ASCII);
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
                } else if (line.contains("Set-Cookie:")) {
                    // line = line.replace("Set-Cookie", "");
                    // TODO get old response
                    // TODO get old cookie
                    // TODO save to table
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

        ByteBuf data = Unpooled.copiedBuffer(sb.toString().getBytes());
        ctx.channel().write(data).addListener(listener)
        ctx.channel().flush();

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
}
