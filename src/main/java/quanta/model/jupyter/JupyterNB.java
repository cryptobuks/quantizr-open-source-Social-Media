package quanta.model.jupyter;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JupyterNB {
    @JsonProperty("cells")
    private List<JupyterCell> cells;

    @JsonProperty("metadata")
    private JupyterMetadata metadata;

    @JsonProperty("nbformat")
    private Integer nbFormat;

    @JsonProperty("nbformat_minor")
    private Integer nbFormatMinor;
}
