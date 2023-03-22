package quanta.model;

import java.util.LinkedList;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MetaDirInfo {
    @JsonProperty("files")
    private LinkedList<String> files;
}
