package quanta.model.client;

import java.util.List;

public class IPSMMessage {

    private String from;
    private String sig;
    private List<IPSMData> content;
    private long ts;

    public String getFrom() {
        return this.from;
    }

    public String getSig() {
        return this.sig;
    }

    public List<IPSMData> getContent() {
        return this.content;
    }

    public long getTs() {
        return this.ts;
    }

    public void setFrom(final String from) {
        this.from = from;
    }

    public void setSig(final String sig) {
        this.sig = sig;
    }

    public void setContent(final List<IPSMData> content) {
        this.content = content;
    }

    public void setTs(final long ts) {
        this.ts = ts;
    }

    public IPSMMessage() {}
}
