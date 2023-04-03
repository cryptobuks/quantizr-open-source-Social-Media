package quanta.service.exports;

public class ExportIpfsFile {
    private String fileName;
    private String cid;
    private String mime;

    public ExportIpfsFile(String cid, String fileName, String mime) {
        this.cid = cid;
        this.fileName = fileName;
        this.mime = mime;
    }

    public String getFileName() {
        return fileName;
    }
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    public String getCid() {
        return cid;
    }
    public void setCid(String cid) {
        this.cid = cid;
    }

    public String getMime() {
        return mime;
    }

    public void setMime(String mime) {
        this.mime = mime;
    }
}
