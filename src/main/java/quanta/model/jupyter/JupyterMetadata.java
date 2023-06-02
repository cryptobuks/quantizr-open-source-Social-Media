
package quanta.model.jupyter;

import com.fasterxml.jackson.annotation.JsonProperty;

public class JupyterMetadata {
    @JsonProperty("kernelspec")
    private JupyterKernelSpec kernelSpec;
    @JsonProperty("language_info")
    private JupyterLangInfo languageInfo;
    @JsonProperty("orig_nbformat")
    private Integer origNbFormat;

    
    public JupyterKernelSpec getKernelSpec() {
        return this.kernelSpec;
    }

    
    public JupyterLangInfo getLanguageInfo() {
        return this.languageInfo;
    }

    
    public Integer getOrigNbFormat() {
        return this.origNbFormat;
    }

    @JsonProperty("kernelspec")
    
    public void setKernelSpec(final JupyterKernelSpec kernelSpec) {
        this.kernelSpec = kernelSpec;
    }

    @JsonProperty("language_info")
    
    public void setLanguageInfo(final JupyterLangInfo languageInfo) {
        this.languageInfo = languageInfo;
    }

    @JsonProperty("orig_nbformat")
    
    public void setOrigNbFormat(final Integer origNbFormat) {
        this.origNbFormat = origNbFormat;
    }
    
    public JupyterMetadata() {
    }
    
    public JupyterMetadata(final JupyterKernelSpec kernelSpec, final JupyterLangInfo languageInfo, final Integer origNbFormat) {
        this.kernelSpec = kernelSpec;
        this.languageInfo = languageInfo;
        this.origNbFormat = origNbFormat;
    }
}
