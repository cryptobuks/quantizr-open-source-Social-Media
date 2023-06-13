package quanta.model.ipfs.file;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class IPFSDir {

    @JsonProperty("Entries")
    private List<IPFSDirEntry> entries;

    public List<IPFSDirEntry> getEntries() {
        return this.entries;
    }

    @JsonProperty("Entries")
    public void setEntries(final List<IPFSDirEntry> entries) {
        this.entries = entries;
    }

    public IPFSDir() {}
}
