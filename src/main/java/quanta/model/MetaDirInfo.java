
package quanta.model;

import java.util.LinkedList;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MetaDirInfo {
    @JsonProperty("files")
    private LinkedList<String> files;
    
    public LinkedList<String> getFiles() {
        return this.files;
    }

    @JsonProperty("files")
    public void setFiles(final LinkedList<String> files) {
        this.files = files;
    }
    
    public MetaDirInfo() {
    }
}
