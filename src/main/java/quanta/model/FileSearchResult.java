package quanta.model;

/**
 * Model representing a filename
 */
public class FileSearchResult {

    private String fileName;

    public FileSearchResult(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return this.fileName;
    }

    public void setFileName(final String fileName) {
        this.fileName = fileName;
    }

    public FileSearchResult() {}
}
