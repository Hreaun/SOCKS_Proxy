package server;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;

public class Server {
    private SocketAccepter socketAccepter = null;
    private SocketProcessor socketProcessor = null;
    private final Selector selector;

    private final int port;

    public Server(int port) throws IOException {
        selector = Selector.open();
        this.port = port;
    }

    public void start() throws IOException {

        //socketAccepter регистрируется в selector в конструкторе
        socketAccepter = new SocketAccepter(port, selector);
        socketProcessor = new SocketProcessor();

        while (true) {
            try {
                selector.select();

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iter = selectedKeys.iterator();
                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    if (key.isAcceptable()) {
                        socketAccepter.accept();
                    }
                    if (key.isReadable()) {
                        socketProcessor.read(key);
                    }
                    if (key.isWritable()) {
                        socketProcessor.write(key);
                    }
                    iter.remove();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


}
