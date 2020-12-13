package message;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

public class Message {
    public enum Step {GREETING, CONNECTION, ERROR}

    public static int BUF_SIZE = 8 * 1024;

    private Step step = null;
    private ByteBuffer in = null;
    private ByteBuffer out = null;
    private SelectionKey peer;

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
