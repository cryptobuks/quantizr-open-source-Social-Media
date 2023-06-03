
package quanta.response;

import quanta.response.base.ResponseBase;

public class FileSystemReindexResponse extends ResponseBase {
    private String report;

    public String getReport() {
        return this.report;
    }
    
    public void setReport(final String report) {
        this.report = report;
    }

    public FileSystemReindexResponse() {
    }
}
