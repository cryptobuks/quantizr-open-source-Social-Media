package quanta.model.jupyter;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class JupyterNB {

    @JsonProperty("cells")
    private List<JupyterCell> cells;

    @JsonProperty("metadata")
    private JupyterMetadata metadata;

    @JsonProperty("nbformat")
    private Integer nbFormat;

    @JsonProperty("nbformat_minor")
    private Integer nbFormatMinor;

    public List<JupyterCell> getCells() {
        return this.cells;
    }

    public JupyterMetadata getMetadata() {
        return this.metadata;
    }

    public Integer getNbFormat() {
        return this.nbFormat;
    }

    public Integer getNbFormatMinor() {
        return this.nbFormatMinor;
    }

    @JsonProperty("cells")
    public void setCells(final List<JupyterCell> cells) {
        this.cells = cells;
    }

    @JsonProperty("metadata")
    public void setMetadata(final JupyterMetadata metadata) {
        this.metadata = metadata;
    }

    @JsonProperty("nbformat")
    public void setNbFormat(final Integer nbFormat) {
        this.nbFormat = nbFormat;
    }

    @JsonProperty("nbformat_minor")
    public void setNbFormatMinor(final Integer nbFormatMinor) {
        this.nbFormatMinor = nbFormatMinor;
    }

    public JupyterNB() {}

    public JupyterNB(
        final List<JupyterCell> cells,
        final JupyterMetadata metadata,
        final Integer nbFormat,
        final Integer nbFormatMinor
    ) {
        this.cells = cells;
        this.metadata = metadata;
        this.nbFormat = nbFormat;
        this.nbFormatMinor = nbFormatMinor;
    }
}
