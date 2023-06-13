package quanta.model.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MFSDirEntry {

    @JsonProperty("Name")
    private String name;

    @JsonProperty("Type")
    private Integer type;

    @JsonProperty("Size")
    private Integer size;

    @JsonProperty("Hash")
    private String hash;

    public boolean isDir() {
        return type != null && type.intValue() == 1;
    }

    public boolean isFile() {
        return type != null && type.intValue() == 0;
    }

    public String getName() {
        return this.name;
    }

    public Integer getType() {
        return this.type;
    }

    public Integer getSize() {
        return this.size;
    }

    public String getHash() {
        return this.hash;
    }

    @JsonProperty("Name")
    public void setName(final String name) {
        this.name = name;
    }

    @JsonProperty("Type")
    public void setType(final Integer type) {
        this.type = type;
    }

    @JsonProperty("Size")
    public void setSize(final Integer size) {
        this.size = size;
    }

    @JsonProperty("Hash")
    public void setHash(final String hash) {
        this.hash = hash;
    }

    public MFSDirEntry() {}
}
