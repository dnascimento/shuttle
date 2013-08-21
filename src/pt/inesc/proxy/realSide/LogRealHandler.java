package pt.inesc.proxy.realSide;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import pt.inesc.proxy.DataSaver;

public class LogRealHandler extends
        ChannelInboundHandlerAdapter {

    public static AtomicInteger id = new AtomicInteger(0);

    public static List<String> log = Collections.synchronizedList(new ArrayList<String>());

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = (ByteBuf) msg;

        log.add(buf.toString(io.netty.util.CharsetUtil.US_ASCII));

        if (log.size() == 100) {
            List<String> logToSave = log;
            log = Collections.synchronizedList(new ArrayList<String>());
            DataSaver saver = new DataSaver("res", logToSave, id.incrementAndGet());
            saver.start();
        }

        // Invoke next handler
        ctx.fireChannelRead(msg);
    }
}
