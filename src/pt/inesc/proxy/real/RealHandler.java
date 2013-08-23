package pt.inesc.proxy.real;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pt.inesc.proxy.clientSide.DataSaver;
import pt.inesc.proxy.clientSide.ProxyHandler;

/**
 * Handler for client requests (client side)
 */
public class RealHandler extends
        ChannelDuplexHandler {

    private static final int MAX_MESSAGES_PER_FILE = 10;


    public static int id = 0;
    private static Logger logger = LogManager.getLogger("RealHandler");
    private static LinkedList<String> requests = new LinkedList<String>();
    private static Map<Integer, String> responses = new TreeMap<Integer, String>();
    private static Lock requestsMutex = new ReentrantLock();
    private Channel inboundChannel;


    public RealHandler(Channel inboundChannel) {
        this.inboundChannel = inboundChannel;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.read();
        ctx.write(Unpooled.EMPTY_BUFFER);
    }


    /**
     * Read request from Client and write to real
     */
    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
        inboundChannel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    ctx.channel().read();
                } else {
                    future.channel().close();
                }
            }
        });
        // Response is here
        // addResponse(msg, id);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        ctx.write(msg, promise);
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ProxyHandler.closeOnFlush(inboundChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ProxyHandler.closeOnFlush(ctx.channel());
    }


    public synchronized static void addResponse(String response, int id) {
        responses.put(id, response);
        if (responses.size() > MAX_MESSAGES_PER_FILE) {
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
