package pt.inesc.proxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import pt.inesc.proxy.clientSide.ClientInit;

public class UndoProxy {

    private final int localPort;
    private final String remoteHost;
    private final int remotePort;

    public UndoProxy(int localPort, String remoteHost, int remotePort) {
        this.localPort = localPort;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
    }

    public void run() throws Exception {
        System.err.println("Proxying *:" + localPort + " to " + remoteHost + ':'
                + remotePort + " ...");

        // Configure the bootstrap.
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .childHandler(new ClientInit(remoteHost, remotePort))
             .childOption(ChannelOption.AUTO_READ, false)
             .bind(localPort)
             .sync()
             .channel()
             .closeFuture()
             .sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {


        // Parse command line options.
        int localPort = 9000;
        String remoteHost = "localhost";
        int remotePort = 8080;

        new UndoProxy(localPort, remoteHost, remotePort).run();
    }
}
