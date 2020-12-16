package server;

import message.Attachment;
import org.xbill.DNS.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DNSResolver {
    private final Attachment attachment;
    private final SelectionKey dnsKey;
    private final int MESSAGE_LENGTH = 512;
    private final DatagramChannel datagramChannel;
    private final List<InetSocketAddress> dnsServers;
    private final Map<Integer, SelectionKey> domainNameClientMap = new HashMap<>();

    public DNSResolver(Selector selector) throws IOException {
        dnsServers = ResolverConfig.getCurrentConfig().servers();
        this.datagramChannel = DatagramChannel.open();
        datagramChannel.configureBlocking(false);
        Attachment dnsResolverAttachment = new Attachment(0);
        attachment = dnsResolverAttachment;
        dnsResolverAttachment.setRole(Attachment.Role.DNS_RESOLVER);
        dnsKey = datagramChannel.register(selector, SelectionKey.OP_READ, dnsResolverAttachment);
    }

    public void readDNSMessage(SelectionKey key) throws IOException {
        DatagramChannel channel = ((DatagramChannel) key.channel());
        Attachment attachment = (Attachment) key.attachment();
        if (attachment.getIn() == null) {
            attachment.setIn(ByteBuffer.allocate(MESSAGE_LENGTH));
        } else {
            attachment.getIn().clear();
        }
        if (channel.receive(attachment.getIn()) == null) {
            return;
        }

        Message dnsMessage = parseDNSMessage(attachment.getIn().array());
        int id = dnsMessage.getHeader().getID();

        List<Record> answers = dnsMessage.getSection(Section.ANSWER);
        ARecord answer = null;

        for (Record record : answers) {
            if (record.getType() == Type.A) {
                answer = (ARecord) record;
                break;
            }
        }

        if (answer != null) {
            notifyClient(answer, id);
        }
    }

    private void notifyClient(ARecord answer, int clientId) {
        Attachment clientAttachment = (Attachment) domainNameClientMap.get(clientId).attachment();
        clientAttachment.setRequestAddr(answer.getAddress());
        domainNameClientMap.get(clientId).interestOps(SelectionKey.OP_WRITE);
        domainNameClientMap.remove(clientId);
    }

    public Message parseDNSMessage(byte[] dnsMessage) throws IOException {
        return new Message(dnsMessage);
    }

    private byte[] makeDNSMessage(String name, Integer clientId) throws TextParseException {
        Message dnsMessage = new Message(clientId);
        Header header = dnsMessage.getHeader();
        header.setOpcode(Opcode.QUERY);
        header.setFlag(Flags.RD);
        dnsMessage.addRecord(Record.newRecord(new Name(name), Type.A, DClass.IN), Section.QUESTION);
        return dnsMessage.toWire(MESSAGE_LENGTH);
    }

    public void makeDNSRequest(String name, Integer clientId, SelectionKey key) throws TextParseException {
        byte[] dnsMessage = makeDNSMessage(name, clientId);
        domainNameClientMap.put(clientId, key);

        ByteBuffer dnsBuf = attachment.getOut();
        if (dnsBuf != null) {
            attachment.setOut(ByteBuffer.allocate(dnsBuf.capacity() + dnsMessage.length)
                    .put(dnsBuf)
                    .put(dnsMessage));
        } else {
            attachment.setOut(ByteBuffer.wrap(dnsMessage));
        }

        dnsKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
    }


    public void sendDNSRequest(SelectionKey key) throws IOException {
        datagramChannel.send(attachment.getOut(), dnsServers.get(0));
        attachment.getOut().compact();
        if (attachment.getOut().position() == 0) {
            attachment.setOut(null);
            key.interestOps(SelectionKey.OP_READ);
        }
    }
}
