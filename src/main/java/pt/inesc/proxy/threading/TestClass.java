package pt.inesc.proxy.threading;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TestClass {

    public static void main(String[] args) throws InterruptedException, IOException {
        LinkedBlockingDeque<String> elements = new LinkedBlockingDeque<String>(5);
        for (int i = 0; i < 5; i++) {
            elements.push("thread " + i);
        }
        AsynchronousServerSocketChannel asynchronousServerSocketChannel;
        ThreadPoolExecutor pool = new ThreadPoolExecutor(10, 10, 999999999999L, TimeUnit.MILLISECONDS, new LinkedBlockingDeque<Runnable>());
        AsynchronousChannelGroup group = AsynchronousChannelGroup.withThreadPool(pool);

        asynchronousServerSocketChannel = AsynchronousServerSocketChannel.open(group);
        asynchronousServerSocketChannel.setOption(StandardSocketOptions.SO_RCVBUF, 1024 * 4);



        final AsynchronousServerSocketChannel listener = asynchronousServerSocketChannel.bind(new InetSocketAddress(9000));
        listener.accept(elements, new CompletionHandler<AsynchronousSocketChannel, LinkedBlockingDeque<String>>() {
            @Override
            public void completed(AsynchronousSocketChannel ch, LinkedBlockingDeque<String> att) {
                System.out.println("Completed");
                // accept the next connection
                listener.accept(att, this);
                String code;
                try {
                    code = att.poll(10, TimeUnit.SECONDS);
                    System.out.println(code);
                } catch (InterruptedException e1) {
                    System.out.println("Not found");
                    e1.printStackTrace();
                }
                // att.add(code);
                ByteBuffer buffer = ByteBuffer.allocateDirect(1024 * 4);
                try {
                    while (ch.read(buffer).get() != -1) {
                        buffer.flip();
                        System.out.println(buffer);

                        if (buffer.hasRemaining()) {
                            buffer.compact();
                        } else {
                            buffer.clear();
                        }
                    }
                } catch (InterruptedException | ExecutionException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                System.out.println("over");

                // handle this connection
                // handle(ch);
            }

            @Override
            public void failed(Throwable exc, LinkedBlockingDeque<String> att) {
                // todo
            }
        });
        group.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
    }
}

class MyRunable
        implements Callable {
    int index = -1;

    public MyRunable(int index) {
        this.index = index;
    }

    @Override
    public Integer call() throws Exception {
        return index;
    }

}
