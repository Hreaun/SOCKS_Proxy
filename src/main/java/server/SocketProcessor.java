package server;

import message.Message;
import socks.SocksConsts;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

public class SocketProcessor {

    public void read(SelectionKey key) throws IOException {
        SocketChannel channel = ((SocketChannel) key.channel());
        Message message = (Message) key.attachment();
        if (message == null) {
            key.attach(message = new Message());
            message.setIn(ByteBuffer.allocate(Message.BUF_SIZE));
            readGreeting(key, channel, message);
        }
        if (channel.read(message.getIn()) < 1) {
            close(key);
        } else if (message.getPeer() == null) {
            readConnection(key, message);
        }

    }

    public void write(SelectionKey key) throws IOException {
        Message message = (Message) key.attachment();

        if ((message.getStep() == Message.Step.GREETING) || (message.getStep() == Message.Step.ERROR)) {
            writeServerChoice(key, message);
        }
        if (message.getStep() == Message.Step.CONNECTION) {

        }
    }


    private void readConnection(SelectionKey key, Message message) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();

        channel.read(message.getIn());
        if (message.getIn().position() > 4) {
            byte[] connection = Arrays.copyOfRange(message.getIn().array(), 0, message.getIn().position());

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

                SocketChannel connectionSocket = SocketChannel.open();
                connectionSocket.configureBlocking(false);

                connectionSocket.connect(new InetSocketAddress(
                        InetAddress.getByAddress(Arrays.copyOfRange(connection, addrStartPosition, addrLength)),
                        port));

                SelectionKey connectionKey = connectionSocket.register(key.selector(), SelectionKey.OP_CONNECT);
                key.interestOps(0);
                message.setPeer(connectionKey);
                message.getIn().clear();

                Message connectionMessage = new Message();
                connectionMessage.setPeer(key);
                connectionKey.attach(connectionMessage);

                System.out.println("Got connection req: " + channel.socket());
            }
        }
    }

    private void writeServerChoice(SelectionKey key, Message message) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();

        if (message.getOut() == null) {
            byte[] choice = new byte[]{SocksConsts.VER, SocksConsts.NO_AUTH};
            message.setOut(ByteBuffer.wrap(choice));
        }
        if ((message.getStep() == Message.Step.GREETING)
                || (message.getStep() == Message.Step.ERROR)) {
            channel.write(message.getOut());
        }
        if (message.getOut().remaining() == 0) {
            System.out.println("Sent choice: " + channel.socket());
            message.setStep(Message.Step.CONNECTION);
            key.interestOps(SelectionKey.OP_READ);
            key.attach(message);
        }
    }


    private void readGreeting(SelectionKey key, SocketChannel channel, Message message) throws IOException {
        channel.read(message.getIn());
        byte[] msg = message.getIn().array();

        if ((msg[0] != SocksConsts.VER) || (msg.length < 3)) {
            //ошибка подключения
            close(key);
        }
        byte[] authMethods = Arrays.copyOfRange(msg, 2, 2 + msg[1]);
        boolean isAuthed = false;

        for (byte authMethod : authMethods) {
            if (authMethod == SocksConsts.NO_AUTH) {
                System.out.println("Got greeting: " + channel.socket());
                message.setStep(Message.Step.GREETING);
                message.getIn().clear();
                key.interestOps(SelectionKey.OP_WRITE);
                key.attach(message);
                isAuthed = true;
                break;
            }
        }
        if (!isAuthed) {
            message.setStep(Message.Step.ERROR);
            key.interestOps(SelectionKey.OP_WRITE);
            key.attach(message);
        }
    }

    private void close(SelectionKey key) {
    }

}