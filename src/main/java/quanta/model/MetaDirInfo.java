package quanta.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedList;

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

    public MetaDirInfo() {}
}
