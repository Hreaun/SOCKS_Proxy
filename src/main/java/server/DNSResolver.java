package server;

import message.Attachment;
import org.xbill.DNS.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DNSResolver {
    private Attachment attachment;
    private SelectionKey dnsKey;
    private final int MESSAGE_LENGTH = 512;
    private final DatagramChannel datagramChannel;
    private final List<InetSocketAddress> dnsServers;
    private final Map<String, InetAddress> dnsCache = new HashMap<>();
    private final Map<String, List<Integer>> domainNameClientMap = new HashMap<>();

    public DNSResolver(Selector selector) throws IOException {
        dnsServers = ResolverConfig.getCurrentConfig().servers();
        this.datagramChannel = DatagramChannel.open();
        datagramChannel.configureBlocking(false);
        Attachment dnsResolverAttachment = new Attachment(0);
        attachment = dnsResolverAttachment;
        dnsResolverAttachment.setRole(Attachment.Role.DNS_RESOLVER);
        dnsKey = datagramChannel.register(selector, SelectionKey.OP_READ, dnsResolverAttachment);
    }

    public void readDNSMessage(SelectionKey key, Map<Integer, SelectionKey> clients) throws IOException {
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
        try {
            Message dnsMessage = parseDNSMessage(attachment.getIn().array());

            List<Record> answers = dnsMessage.getSection(Section.ANSWER);
            ARecord answer = null;

            for (Record record : answers) {
                if (record.getType() == Type.A) {
                    answer = (ARecord) record;
                    break;
                }
            }

            if (answer != null) {
                addNameToCache(answer);
                notifyClients(answer, clients);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void notifyClients(ARecord answer, Map<Integer, SelectionKey> clients) {
        List<Integer> clientsIds = domainNameClientMap.get(answer.getName().toString());
        if ((clientsIds == null) || (clients == null)) {
            return;
        }

        clientsIds.forEach(id -> {
            Attachment clientAttachment = (Attachment) clients.get(id).attachment();
            clientAttachment.setStep(Attachment.Step.CONNECTION);
            if (clients.get(id).isValid()) {
                clients.get(id).interestOps(SelectionKey.OP_WRITE);
            } else {
                clients.remove(id);
            }
        });
    }

    private void addNameToCache(ARecord answer) {
        System.out.println("got dns response " + answer.getName().toString());
        dnsCache.put(answer.getName().toString(), answer.getAddress());
    }

    public Message parseDNSMessage(byte[] dnsMessage) throws IOException {
        Message msg = new Message(dnsMessage);
        return msg;
    }

    private byte[] makeDNSMessage(String name) throws TextParseException {
        Message dnsMessage = new Message();
        Header header = dnsMessage.getHeader();
        header.setOpcode(Opcode.QUERY);
        header.setFlag(Flags.RD);
        dnsMessage.addRecord(Record.newRecord(new Name(name), Type.A, DClass.IN), Section.QUESTION);
        return dnsMessage.toWire(MESSAGE_LENGTH);
    }

    public void makeDNSRequest(String name, Integer clientId) throws TextParseException {
        System.out.println("made dns req: " + name);
        byte[] dnsMessage = makeDNSMessage(name);
        if (!domainNameClientMap.containsKey(name)) {
            domainNameClientMap.put(name, new ArrayList<>());
        }
        domainNameClientMap.get(name).add(clientId);

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

    public InetAddress getAddress(String name) {
        return dnsCache.get(name);
    }
}
