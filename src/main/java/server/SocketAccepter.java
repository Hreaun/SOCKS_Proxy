package server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class SocketAccepter {
    private int port;
    private final ServerSocketChannel serverSocket;
    private final Selector selector;

    public SocketAccepter(int port, Selector selector) throws IOException {
        this.selector = selector;
        this.port = port;
        this.serverSocket = ServerSocketChannel.open();
        this.serverSocket.bind(new InetSocketAddress(port));
        serverSocket.configureBlocking(false);
        serverSocket.register(selector, SelectionKey.OP_ACCEPT);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                serverSocket.close();
                selector.close();
                System.out.println("Server is closed.");
            } catch (IOException e){
                System.err.println(e.getMessage());
            }
        }));
    }

    public void accept() throws IOException {
        SocketChannel client = serverSocket.accept();
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ);
        System.out.println("Socket accepted: " + client.socket());
    }
}
