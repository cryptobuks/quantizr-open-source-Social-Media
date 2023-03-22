package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GetMultiRssRequest extends RequestBase {
    private String urls;
    private Integer page;
}
