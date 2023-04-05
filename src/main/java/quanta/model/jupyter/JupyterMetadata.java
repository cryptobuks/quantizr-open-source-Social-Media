package quanta.model.jupyter;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JupyterMetadata {
    @JsonProperty("kernelspec")
    private JupyterKernelSpec kernelSpec;
    
    @JsonProperty("language_info")
    private JupyterLangInfo languageInfo;

    @JsonProperty("orig_nbformat")
    private Integer origNbFormat;
}
