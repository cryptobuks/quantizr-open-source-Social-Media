package quanta.model.client;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Transient;
import quanta.mongo.model.SubNode;
import quanta.util.ThreadLocals;

@JsonInclude(Include.NON_DEFAULT)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Attachment {

    private Integer ordinal = 0;
    private Integer width = 0;
    private Integer height = 0;
    private String position;
    private String mime;
    private String fileName;
    private String cssSize;
    private Long size = 0L;
    private String bin;
    private String binData;
    private String url;
    private String ipfsLink;
    private String ipfsRef;

    private SubNode ownerNode;

    // key in the SubNode hashmap that points to this. May be null unless retrieved
    // thru certain API cals
    private String key;

    public Attachment() {}

    public Attachment(SubNode ownerNode) {
        this.ownerNode = ownerNode;
    }

    @JsonProperty("w")
    public Integer getWidth() {
        return width;
    }

    @JsonProperty("w")
    public void setWidth(Integer width) {
        ThreadLocals.dirty(ownerNode);
        this.width = width;
    }

    @JsonProperty("h")
    public Integer getHeight() {
        return height;
    }

    @JsonProperty("h")
    public void setHeight(Integer height) {
        ThreadLocals.dirty(ownerNode);
        this.height = height;
    }

    @JsonProperty("p")
    public String getPosition() {
        return position;
    }

    @JsonProperty("p")
    public void setPosition(String position) {
        this.position = position;
    }

    @JsonProperty("m")
    public String getMime() {
        return mime;
    }

    @JsonProperty("m")
    public void setMime(String mime) {
        ThreadLocals.dirty(ownerNode);
        this.mime = mime;
    }

    @JsonProperty("f")
    public String getFileName() {
        return fileName;
    }

    @JsonProperty("f")
    public void setFileName(String fileName) {
        ThreadLocals.dirty(ownerNode);
        this.fileName = fileName;
    }

    @JsonProperty("s")
    public Long getSize() {
        return size;
    }

    @JsonProperty("s")
    public void setSize(Long size) {
        ThreadLocals.dirty(ownerNode);
        this.size = size;
    }

    @JsonProperty("b")
    public String getBin() {
        return bin;
    }

    @JsonProperty("b")
    public void setBin(String bin) {
        ThreadLocals.dirty(ownerNode);
        this.bin = bin;
    }

    @JsonProperty("d")
    public String getBinData() {
        return binData;
    }

    @JsonProperty("d")
    public void setBinData(String binData) {
        ThreadLocals.dirty(ownerNode);
        this.binData = binData;
    }

    @JsonProperty("u")
    public String getUrl() {
        return url;
    }

    @JsonProperty("u")
    public void setUrl(String url) {
        ThreadLocals.dirty(ownerNode);
        this.url = url;
    }

    @JsonProperty("il")
    public String getIpfsLink() {
        return ipfsLink;
    }

    @JsonProperty("il")
    public void setIpfsLink(String ipfsLink) {
        ThreadLocals.dirty(ownerNode);
        this.ipfsLink = ipfsLink;
    }

    @JsonProperty("ir")
    public String getIpfsRef() {
        return ipfsRef;
    }

    @JsonProperty("ir")
    public void setIpfsRef(String ipfsRef) {
        ThreadLocals.dirty(ownerNode);
        this.ipfsRef = ipfsRef;
    }

    @JsonProperty("c")
    public String getCssSize() {
        return cssSize;
    }

    @JsonProperty("c")
    public void setCssSize(String cssSize) {
        ThreadLocals.dirty(ownerNode);
        this.cssSize = cssSize;
    }

    @JsonProperty("o")
    public Integer getOrdinal() {
        return ordinal;
    }

    @JsonProperty("o")
    public void setOrdinal(Integer ordinal) {
        this.ordinal = ordinal;
    }

    @Transient
    @JsonIgnore
    public SubNode getOwnerNode() {
        return ownerNode;
    }

    @Transient
    @JsonIgnore
    public void setOwnerNode(SubNode ownerNode) {
        this.ownerNode = ownerNode;
    }

    @Transient
    @JsonIgnore
    public String getKey() {
        return key;
    }

    @Transient
    @JsonIgnore
    public void setKey(String key) {
        this.key = key;
    }
}
