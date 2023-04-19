package quanta.request;

import javax.annotation.Nullable;
import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LoginRequest extends RequestBase {
    private String userName;
    private String password;
    private String asymEncKey;
    private String sigKey;
    private String nostrNpub;

    /* timezone offset */
    @Nullable
    private Integer tzOffset;

    /* daylight savings time */
    @Nullable
    private Boolean dst;
}
