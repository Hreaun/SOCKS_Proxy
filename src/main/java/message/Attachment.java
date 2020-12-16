package message;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

public class Attachment {
    public enum Role {CLIENT, DNS_RESOLVER}
    public enum Step {GREETING, CONNECTION, CONNECTED , ERROR}

    public static int BUF_SIZE = 64 * 1024;

    private final int clientId;
    private Role role = null;
    private Step step = null;
    private ByteBuffer in = null;
    private ByteBuffer out = null;
    private SelectionKey peer = null;
    private InetAddress requestAddr = null;

    public InetAddress getRequestAddr() {
        return requestAddr;
    }

    public void setRequestAddr(InetAddress requestAddr) {
        this.requestAddr = requestAddr;
    }

    public Attachment(int clientId) {
        this.clientId = clientId;
    }

    public int getClientId() {
        return clientId;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public ByteBuffer getIn() {
        return in;
    }

    public void setIn(ByteBuffer in) {
        this.in = in;
    }

    public ByteBuffer getOut() {
        return out;
    }

    public void setOut(ByteBuffer out) {
        this.out = out;
    }

    public SelectionKey getPeer() {
        return peer;
    }

    public void setPeer(SelectionKey peer) {
        this.peer = peer;
    }

    public Step getStep() {
        return step;
    }

    public void setStep(Step step) {
        this.step = step;
    }
}
