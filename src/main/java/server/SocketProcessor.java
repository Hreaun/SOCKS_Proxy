package server;

import message.Attachment;
import socks.SocksConsts;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

public class SocketProcessor {
    private int idCounter = 0;
    DNSResolver dnsResolver;

    private int getNextId() {
        return ++idCounter;
    }

    public SocketProcessor(DNSResolver dnsResolver) {
        this.dnsResolver = dnsResolver;
    }

    public void read(SelectionKey key, Map<Integer, SelectionKey> clients) throws IOException {
        SocketChannel channel = ((SocketChannel) key.channel());
        Attachment attachment = (Attachment) key.attachment();
        if (attachment == null) {
            key.attach(attachment = new Attachment(getNextId()));
            clients.put(attachment.getClientId(), key);
            attachment.setRole(Attachment.Role.CLIENT);
            attachment.setIn(ByteBuffer.allocate(Attachment.BUF_SIZE));
            readGreeting(key, channel, attachment);
        } else if (attachment.getStep() == Attachment.Step.CONNECTION) {
            channel.read(attachment.getIn());
            readConnection(key, attachment);
        } else {
            if (channel.read(attachment.getIn()) < 1) {
                close(key);
                return;
            }
            if (attachment.getPeer() == null) {
                close(key);
                return;
            }
            attachment.getPeer().interestOps(attachment.getPeer().interestOps() | SelectionKey.OP_WRITE);
            key.interestOps(key.interestOps() ^ SelectionKey.OP_READ);
            attachment.getIn().flip();
        }
    }

    public void write(SelectionKey key) throws IOException {
        Attachment attachment = (Attachment) key.attachment();

        if ((attachment.getStep() == Attachment.Step.GREETING) || (attachment.getStep() == Attachment.Step.ERROR)) {
            writeServerChoice(key, attachment);
        } else if (attachment.getStep() == Attachment.Step.CONNECTION) {
            readConnection(key, attachment);
        } else if (attachment.getStep() == Attachment.Step.CONNECTED) {
            writeData(key, attachment);
        }
    }

    private void writeData(SelectionKey key, Attachment attachment) throws IOException {
        SocketChannel channel = ((SocketChannel) key.channel());
        if (channel.write(attachment.getOut()) == -1) {
            close(key);
        } else if (attachment.getOut().remaining() == 0) {
            if (attachment.getPeer() == null) {
                close(key);
            } else {
                attachment.getOut().clear();
                attachment.getPeer().interestOps(attachment.getPeer().interestOps() | SelectionKey.OP_READ);
                key.interestOps(key.interestOps() ^ SelectionKey.OP_WRITE);
            }
        }
    }

    private void writeServerChoice(SelectionKey key, Attachment attachment) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();

        if (attachment.getOut() == null) {
            byte[] choice = new byte[]{SocksConsts.VER, SocksConsts.NO_AUTH};
            attachment.setOut(ByteBuffer.wrap(choice));
        }
        if ((attachment.getStep() == Attachment.Step.GREETING)
                || (attachment.getStep() == Attachment.Step.ERROR)) {
            channel.write(attachment.getOut());
        }
        if (attachment.getOut().remaining() == 0) {
            attachment.setStep(Attachment.Step.CONNECTION);
            key.interestOps(SelectionKey.OP_READ);
            key.attach(attachment);
        }
    }

    private void makeResponseMessage(Attachment attachment, InetSocketAddress address) {
        byte[] ipBytes = address.getAddress().getAddress();
        ByteBuffer responseMsg = ByteBuffer.allocate(10);
        if (address.getAddress() instanceof Inet6Address) {
            responseMsg = ByteBuffer.allocate(22).put(new byte[]{SocksConsts.VER, 0x00, 0x00, SocksConsts.IP_V6});
        } else {
            responseMsg.put(new byte[]{SocksConsts.VER, 0x00, 0x00, SocksConsts.IP_V4});
        }
        byte[] port = Arrays.copyOfRange(ByteBuffer.allocate(4).putInt(address.getPort()).array(), 2, 4);
        responseMsg.put(ipBytes).put(port);
        attachment.setIn(responseMsg);
    }

    public void connect(SelectionKey key) throws IOException {
        SocketChannel channel = ((SocketChannel) key.channel());
        Attachment attachment = ((Attachment) key.attachment());
        if (!channel.finishConnect()) {
            return;
        }
        InetSocketAddress socketAddress = (InetSocketAddress) ((SocketChannel) attachment.getPeer().channel()).getLocalAddress();

        makeResponseMessage(attachment, socketAddress);
        attachment.getIn().flip();

        attachment.setOut(((Attachment) attachment.getPeer().attachment()).getIn());
        ((Attachment) attachment.getPeer().attachment()).setOut(attachment.getIn());

        attachment.getPeer().interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
        key.interestOps(0);
    }

    private void makeNewProxyConnection(InetAddress addr, int port, SelectionKey key, Attachment attachment) throws IOException {
        SocketChannel connectionSocket = SocketChannel.open();
        connectionSocket.configureBlocking(false);

        connectionSocket.connect(new InetSocketAddress(addr, port));
        SelectionKey connectionKey = connectionSocket.register(key.selector(), SelectionKey.OP_CONNECT);

        key.interestOps(0);
        attachment.setPeer(connectionKey);
        attachment.getIn().clear();
        attachment.setStep(Attachment.Step.CONNECTED);

        Attachment connectionAttachment = new Attachment(getNextId());
        connectionAttachment.setRole(Attachment.Role.CLIENT);
        connectionAttachment.setStep(Attachment.Step.CONNECTED);
        connectionAttachment.setOut(ByteBuffer.allocate(Attachment.BUF_SIZE));
        connectionAttachment.setIn(ByteBuffer.allocate(Attachment.BUF_SIZE));
        connectionAttachment.setPeer(key);
        connectionKey.attach(connectionAttachment);
    }


    private void readConnection(SelectionKey key, Attachment attachment) throws IOException {

        if (attachment.getIn().position() > 4) {
            byte[] connection = Arrays.copyOfRange(attachment.getIn().array(), 0, attachment.getIn().position());

            if ((connection[0] != SocksConsts.VER)
                    || (connection[1] != SocksConsts.EST_CONNECTION)
                    || (connection[3] == SocksConsts.IP_V6)) {
                close(key);
                return;
            }
            int addrLength = 0;
            int addrStartPosition = 0;
            if (connection[3] == SocksConsts.IP_V4) {
                addrStartPosition = 4;
                addrLength = 4;
            } else if (connection[3] == SocksConsts.DOMAIN_NAME) {
                addrStartPosition = 5;
                addrLength = Byte.toUnsignedInt(connection[4]);
            }

            // 2 байта: PORT
            if (connection.length == addrLength + addrStartPosition + 2) {
                byte[] portBytes = new byte[]{0, 0, connection[connection.length - 2], connection[connection.length - 1]};
                int port = ByteBuffer.wrap(portBytes).getInt();

                InetAddress addr;
                if (connection[3] == SocksConsts.DOMAIN_NAME) {
                    if (attachment.getRequestAddr() != null) {
                        makeNewProxyConnection(attachment.getRequestAddr(), port, key, attachment);
                    } else {
                        String name = new String(Arrays.copyOfRange(connection, addrStartPosition,
                                addrStartPosition + addrLength), StandardCharsets.UTF_8) + ".";
                        dnsResolver.makeDNSRequest(name, attachment.getClientId(), key);
                        key.interestOps(0);
                    }
                } else {
                    addr = InetAddress.getByAddress(Arrays.copyOfRange(connection, addrStartPosition,
                            addrStartPosition + addrLength));
                    makeNewProxyConnection(addr, port, key, attachment);
                }
            }
        }
    }

    private void readGreeting(SelectionKey key, SocketChannel channel, Attachment attachment) throws IOException {
        channel.read(attachment.getIn());
        byte[] msg = attachment.getIn().array();

        if ((msg[0] != SocksConsts.VER) || (msg.length < 3)) {
            //ошибка подключения
            close(key);
        }
        byte[] authMethods = Arrays.copyOfRange(msg, 2, 2 + msg[1]);
        boolean isAuthed = false;

        for (byte authMethod : authMethods) {
            if (authMethod == SocksConsts.NO_AUTH) {
                attachment.setStep(Attachment.Step.GREETING);
                attachment.getIn().clear();
                key.interestOps(SelectionKey.OP_WRITE);
                key.attach(attachment);
                isAuthed = true;
                break;
            }
        }
        if (!isAuthed) {
            attachment.setStep(Attachment.Step.ERROR);
            key.interestOps(SelectionKey.OP_WRITE);
            key.attach(attachment);
        }
    }

    private void close(SelectionKey key) throws IOException {
        key.cancel();
        key.channel().close();
        SelectionKey peerKey = ((Attachment) key.attachment()).getPeer();
        if (peerKey != null) {
            ((Attachment) peerKey.attachment()).setPeer(null);
            if ((peerKey.interestOps() & SelectionKey.OP_WRITE) == 0) {
                ((Attachment) peerKey.attachment()).getOut().flip();
            }
            peerKey.interestOps(SelectionKey.OP_WRITE);
        }
    }

}