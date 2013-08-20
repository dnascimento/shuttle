package pt.inesc.proxy.clientSide;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.io.BufferedWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

public class LogClientHandler extends
        ChannelInboundHandlerAdapter {

    public static AtomicInteger id = new AtomicInteger(0);

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
        id.incrementAndGet();
        ByteBuf buf = (ByteBuf) msg;

        Charset charset = Charset.forName("US-ASCII");
        Path path = Paths.get("requests/req" + id + ".txt");
        BufferedWriter writer = Files.newBufferedWriter(path, charset);
        writer.write(buf.toString(io.netty.util.CharsetUtil.US_ASCII));
        writer.flush();
        writer.close();


        // Invoke next handler
        ctx.fireChannelRead(msg);
    }
}
