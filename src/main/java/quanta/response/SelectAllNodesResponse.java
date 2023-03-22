package quanta.response;

import java.util.List;

import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SelectAllNodesResponse extends ResponseBase {
    private List<String> nodeIds;
}
