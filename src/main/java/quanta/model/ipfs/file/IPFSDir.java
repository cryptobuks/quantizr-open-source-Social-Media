package quanta.model.ipfs.file;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class IPFSDir {
    @JsonProperty("Entries")
    private List<IPFSDirEntry> entries;
}
