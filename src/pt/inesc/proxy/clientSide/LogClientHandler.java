package pt.inesc.proxy.clientSide;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class LogClientHandler extends
        ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
        System.out.println("Client Handler");
        ByteBuf buf = (ByteBuf) msg;
        System.out.println(buf.toString(io.netty.util.CharsetUtil.US_ASCII));
        // Invoke next handler
        ctx.fireChannelRead(msg);
    }
}
