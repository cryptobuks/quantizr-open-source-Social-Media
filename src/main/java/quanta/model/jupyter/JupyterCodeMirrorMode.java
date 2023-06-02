
package quanta.model.jupyter;

import com.fasterxml.jackson.annotation.JsonProperty;

public class JupyterCodeMirrorMode {
    @JsonProperty("name")
    private String name;
    @JsonProperty("version")
    private Integer version;

    
    public String getName() {
        return this.name;
    }

    
    public Integer getVersion() {
        return this.version;
    }

    @JsonProperty("name")
    
    public void setName(final String name) {
        this.name = name;
    }

    @JsonProperty("version")
    
    public void setVersion(final Integer version) {
        this.version = version;
    }
    
    public JupyterCodeMirrorMode() {
    }

    public JupyterCodeMirrorMode(final String name, final Integer version) {
        this.name = name;
        this.version = version;
    }
}
