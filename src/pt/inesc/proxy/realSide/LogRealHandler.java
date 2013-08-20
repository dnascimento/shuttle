package pt.inesc.proxy.realSide;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class LogRealHandler extends
        ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
        System.out.println("Real Handler");
        ByteBuf buf = (ByteBuf) msg;
        System.out.println(buf.toString(io.netty.util.CharsetUtil.US_ASCII));
        ctx.fireChannelRead(msg);
    }
}
