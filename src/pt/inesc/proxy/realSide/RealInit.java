package pt.inesc.proxy.realSide;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;


/**
 * Init the Proxy Server. Create the pipes.
 */
public class RealInit extends
        ChannelInitializer<SocketChannel> {

    private Channel inboundChannel;
    private int id;


    public RealInit(Channel inboundChannel, int id) {
        this.inboundChannel = inboundChannel;
    }

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        // ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO),
        // new HexDumpProxyFrontendHandler(remoteHost, remotePort));
        ch.pipeline().addLast(new LogRealHandler());
        ch.pipeline().addLast(new RealHandler(inboundChannel, id));
    }
}
