package quanta.model.jupyter;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JupyterCodeMirrorMode {
    @JsonProperty("name")
    private String name;

    @JsonProperty("version")
    private Integer version;
}
