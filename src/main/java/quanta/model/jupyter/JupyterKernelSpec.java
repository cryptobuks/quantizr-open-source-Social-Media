
package quanta.model.jupyter;

import com.fasterxml.jackson.annotation.JsonProperty;

public class JupyterKernelSpec {
    @JsonProperty("display_name")
    private String displayName;
    @JsonProperty("language")
    private String language;
    @JsonProperty("name")
    private String name;

    public String getDisplayName() {
        return this.displayName;
    }
    
    public String getLanguage() {
        return this.language;
    }
    
    public String getName() {
        return this.name;
    }

    @JsonProperty("display_name")
    public void setDisplayName(final String displayName) {
        this.displayName = displayName;
    }

    @JsonProperty("language")
    public void setLanguage(final String language) {
        this.language = language;
    }

    @JsonProperty("name")
    public void setName(final String name) {
        this.name = name;
    }
    
    public JupyterKernelSpec() {
    }
    
    public JupyterKernelSpec(final String displayName, final String language, final String name) {
        this.displayName = displayName;
        this.language = language;
        this.name = name;
    }
}
