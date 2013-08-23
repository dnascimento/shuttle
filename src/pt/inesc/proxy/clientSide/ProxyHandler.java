package pt.inesc.proxy.clientSide;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;

import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pt.inesc.proxy.real.RealInit;

/**
 * Handler for client requests (client side)
 */
@Sharable
public class ProxyHandler extends
        ChannelInboundHandlerAdapter {

    private static final int MAX_MESSAGES_PER_FILE = 10;
    private InetSocketAddress remoteHost = null;
    private Channel realOUTChannel;

    public static int id = 0;
    private static Logger logger = LogManager.getLogger("ProxyHandler");
    private static LinkedList<String> requests = new LinkedList<String>();
    private static Map<Integer, String> responses = new TreeMap<Integer, String>();
    private static Lock requestsMutex = new ReentrantLock();



    public ProxyHandler(InetSocketAddress remoteHost) {
        this.remoteHost = remoteHost;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        final Channel inboundChannel = ctx.channel();
        Bootstrap b = new Bootstrap();
        b.group(inboundChannel.eventLoop())
         .channel(ctx.channel().getClass())
         .handler(new RealInit(inboundChannel))
         .option(ChannelOption.AUTO_READ, false);

        ChannelFuture f = b.connect(remoteHost.getHostName(), remoteHost.getPort());

        realOUTChannel = f.channel();
        f.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    inboundChannel.read();
                } else {
                    inboundChannel.close();
                }
            }
        });

    }

    /**
     * Read request from Client and write to real
     */
    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws InterruptedException {
        if (realOUTChannel.isActive()) {





            realOUTChannel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        ctx.channel().read();
                    } else {
                        future.channel().close();
                    }
                }
            });
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (realOUTChannel != null) {
            closeOnFlush(realOUTChannel);
        }
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
}
