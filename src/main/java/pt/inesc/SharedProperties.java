package pt.inesc;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Properties;

public class SharedProperties {
    public static final InetSocketAddress MANAGER_ADDRESS;
    public static final InetSocketAddress LOAD_BALANCER_ADDRESS;
    public static final String MY_HOST;

    public static final int REPLAY_PORT = 11500;
    public static final int PROXY_PORT = 11100;
    public static final int DATABASE_PORT = 11200;

    private static final int MANAGER_PORT = 11000;
    private static final String PROPERTIES_FILE = "undo.properties";

    static {
        Properties props;
        InputStream is;
        try {
            is = new FileInputStream(PROPERTIES_FILE);
            props = new Properties();
            props.load(is);
            is.close();
            MY_HOST = props.getProperty("myHost");
            MANAGER_ADDRESS = new InetSocketAddress(props.getProperty("Manager"), MANAGER_PORT);
            LOAD_BALANCER_ADDRESS = toAddress(props.getProperty("TargetLoadBalancer"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static InetSocketAddress toAddress(String host_port) {
        String[] p = host_port.split(":");
        return new InetSocketAddress(p[0], Integer.valueOf(p[1]));
    }
}
