
package quanta.model.ipfs.file;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

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
    
    public IPFSDir() {
    }
}
