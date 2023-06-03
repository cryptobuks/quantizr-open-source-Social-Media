
package quanta.model.jupyter;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public class JupyterCell {
    @JsonProperty("cell_type")
    private String cellType;
    @JsonProperty("source")
    private List<String> source;

    public String getCellType() {
        return this.cellType;
    }
    
    public List<String> getSource() {
        return this.source;
    }

    @JsonProperty("cell_type")
    public void setCellType(final String cellType) {
        this.cellType = cellType;
    }

    @JsonProperty("source")
    public void setSource(final List<String> source) {
        this.source = source;
    }
    
    public JupyterCell() {
    }
    
    public JupyterCell(final String cellType, final List<String> source) {
        this.cellType = cellType;
        this.source = source;
    }
}
