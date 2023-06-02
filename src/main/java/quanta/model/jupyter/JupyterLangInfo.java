
package quanta.model.jupyter;

import com.fasterxml.jackson.annotation.JsonProperty;

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

    
    public JupyterCodeMirrorMode getCodeMirrorMode() {
        return this.codeMirrorMode;
    }

    
    public String getFileExtension() {
        return this.fileExtension;
    }

    
    public String getMimeType() {
        return this.mimeType;
    }

    
    public String getName() {
        return this.name;
    }

    
    public String getNbConvertExporter() {
        return this.nbConvertExporter;
    }

    
    public String getPygmentsLexer() {
        return this.pygmentsLexer;
    }

    
    public String getVersion() {
        return this.version;
    }

    @JsonProperty("codemirror_mode")
    
    public void setCodeMirrorMode(final JupyterCodeMirrorMode codeMirrorMode) {
        this.codeMirrorMode = codeMirrorMode;
    }

    @JsonProperty("file_extension")
    
    public void setFileExtension(final String fileExtension) {
        this.fileExtension = fileExtension;
    }

    @JsonProperty("mimetype")
    
    public void setMimeType(final String mimeType) {
        this.mimeType = mimeType;
    }

    @JsonProperty("name")
    
    public void setName(final String name) {
        this.name = name;
    }

    @JsonProperty("nbconvert_exporter")
    
    public void setNbConvertExporter(final String nbConvertExporter) {
        this.nbConvertExporter = nbConvertExporter;
    }

    @JsonProperty("pygments_lexer")
    
    public void setPygmentsLexer(final String pygmentsLexer) {
        this.pygmentsLexer = pygmentsLexer;
    }

    @JsonProperty("version")
    
    public void setVersion(final String version) {
        this.version = version;
    }
    
    public JupyterLangInfo() {
    }
    
    public JupyterLangInfo(final JupyterCodeMirrorMode codeMirrorMode, final String fileExtension, final String mimeType, final String name, final String nbConvertExporter, final String pygmentsLexer, final String version) {
        this.codeMirrorMode = codeMirrorMode;
        this.fileExtension = fileExtension;
        this.mimeType = mimeType;
        this.name = name;
        this.nbConvertExporter = nbConvertExporter;
        this.pygmentsLexer = pygmentsLexer;
        this.version = version;
    }
}
