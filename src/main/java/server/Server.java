package server;

import message.Attachment;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class Server {
    private SocketAccepter socketAccepter = null;
    private SocketProcessor socketProcessor = null;
    private DNSResolver dnsResolver = null;
    private final Selector selector;
    private final Map<Integer, SelectionKey> clients = new HashMap<>();

    private final int port;

    public Server(int port) throws IOException {
        selector = Selector.open();
        this.port = port;
    }

    public void start() throws IOException {

        //socketAccepter регистрируется в selector в конструкторе
        socketAccepter = new SocketAccepter(port, selector);

        //dnsResolver регистрируется в selector в конструкторе
        dnsResolver = new DNSResolver(selector);
        socketProcessor = new SocketProcessor(dnsResolver);

        while (selector.select() > -1) {
            try {
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iter = selectedKeys.iterator();
                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    if (!key.isValid()) {
                        continue;
                    }
                    if (key.isAcceptable()) {
                        socketAccepter.accept();
                    } else if (key.isConnectable()) {
                        socketProcessor.connect(key);
                    } else if (key.isReadable()) {
                        Attachment attachment = (Attachment) key.attachment();
                        if ((attachment != null) && (attachment.getRole() == Attachment.Role.DNS_RESOLVER)) {
                            dnsResolver.readDNSMessage(key);
                        } else {
                            socketProcessor.read(key, clients);
                        }
                    } else if (key.isWritable()) {
                        Attachment attachment = (Attachment) key.attachment();
                        if ((attachment != null) && (attachment.getRole() == Attachment.Role.DNS_RESOLVER)) {
                            dnsResolver.sendDNSRequest(key);
                        } else {
                            socketProcessor.write(key);
                        }
                    }
                    iter.remove();
                }
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
        }
    }


}
