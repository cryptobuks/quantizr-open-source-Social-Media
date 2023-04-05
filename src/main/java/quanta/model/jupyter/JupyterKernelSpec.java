package quanta.model.jupyter;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JupyterKernelSpec {
    @JsonProperty("display_name")
    private String displayName;
    
    @JsonProperty("language")
    private String language;

    @JsonProperty("name")
    private String name;
}
