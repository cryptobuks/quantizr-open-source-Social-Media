package quanta.response;

import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GetUserAccountInfoResponse extends ResponseBase {
    private Integer binTotal;
    private Integer binQuota;
}
