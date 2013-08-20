package pt.inesc;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

public class Proxy {
    private static Logger log = LogManager.getLogger("UndoServer");


    public static void main(String[] args) {
        int localPort = 9000;
        String remoteHost = "localhost";
        int remotePort = 8080;

        // Configure the bootstrap
        Executor executor = Executors.newCachedThreadPool();
        ServerBootstrap sb = new ServerBootstrap(new NioServerSocketChannelFactory(
                executor, executor));

        // Set up the event pipeline factory.
        ClientSocketChannelFactory cf = new NioClientSocketChannelFactory(executor,
                executor);

        // Create a pipeline
        sb.setPipelineFactory(new HexDumpProxyPipelineFactory(cf, remoteHost, remotePort));

        // Start up server (bind server to IP)
        sb.bind(new InetSocketAddress(localPort));

    }
}
