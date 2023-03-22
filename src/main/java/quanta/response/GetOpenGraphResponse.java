package quanta.response;

import quanta.model.client.OpenGraph;
import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GetOpenGraphResponse extends ResponseBase {
    private OpenGraph openGraph;
}
