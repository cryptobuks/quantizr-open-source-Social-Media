package quanta.model.ipfs.dag;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MerkleCid {

    @JsonProperty("/")
    private String path;

    public String getPath() {
        return this.path;
    }

    @JsonProperty("/")
    public void setPath(final String path) {
        this.path = path;
    }

    public MerkleCid() {}
}
