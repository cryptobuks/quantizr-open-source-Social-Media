package quanta.model.jupyter;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JupyterLangInfo {
    @JsonProperty("codemirror_mode")
    private JupyterCodeMirrorMode codeMirrorMode;
    
    @JsonProperty("file_extension")
    private String fileExtension;

    @JsonProperty("mimetype")
    private String mimeType;

    @JsonProperty("name")
    private String name;

    @JsonProperty("nbconvert_exporter")
    private String nbConvertExporter;

    @JsonProperty("pygments_lexer")
    private String pygmentsLexer;

    @JsonProperty("version")
    private String version;
}
