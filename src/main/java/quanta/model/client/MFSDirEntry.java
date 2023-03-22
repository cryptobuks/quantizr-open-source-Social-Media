package quanta.model.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MFSDirEntry {
    @JsonProperty("Name")
    private String name;

    @JsonProperty("Type")
    private Integer type;

    @JsonProperty("Size")
    private Integer size;

    @JsonProperty("Hash")
    private String hash;

    public boolean isDir() {
        return type != null && type.intValue() == 1;
    }

    public boolean isFile() {
        return type != null && type.intValue() == 0;
    }
}
