package pt.inesc.proxy.clientSide;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;


/**
 * Init the Proxy Server. Create the pipes.
 */
public class ClientInit extends
        ChannelInitializer<SocketChannel> {

    private final String remoteHost;
    private final int remotePort;

    public ClientInit(String remoteHost, int remotePort) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
    }

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        // ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO),
        // new HexDumpProxyFrontendHandler(remoteHost, remotePort));
        ch.pipeline().addLast(new LogClientHandler(),
                              new ClientHandler(remoteHost, remotePort));
    }
}
