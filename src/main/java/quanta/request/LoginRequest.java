
package quanta.request;

import javax.annotation.Nullable;
import quanta.request.base.RequestBase;

public class LoginRequest extends RequestBase {
    private String userName;
    private String password;
    private String asymEncKey;
    private String sigKey;
    private String nostrNpub;
    private String nostrPubKey;
    /* timezone offset */
    @Nullable
    private Integer tzOffset;
    /* daylight savings time */
    @Nullable
    private Boolean dst;

    
    public String getUserName() {
        return this.userName;
    }

    
    public String getPassword() {
        return this.password;
    }

    
    public String getAsymEncKey() {
        return this.asymEncKey;
    }

    
    public String getSigKey() {
        return this.sigKey;
    }

    
    public String getNostrNpub() {
        return this.nostrNpub;
    }

    
    public String getNostrPubKey() {
        return this.nostrPubKey;
    }

    @Nullable
    
    public Integer getTzOffset() {
        return this.tzOffset;
    }

    @Nullable
    
    public Boolean getDst() {
        return this.dst;
    }

    
    public void setUserName(final String userName) {
        this.userName = userName;
    }

    
    public void setPassword(final String password) {
        this.password = password;
    }

    
    public void setAsymEncKey(final String asymEncKey) {
        this.asymEncKey = asymEncKey;
    }

    
    public void setSigKey(final String sigKey) {
        this.sigKey = sigKey;
    }

    
    public void setNostrNpub(final String nostrNpub) {
        this.nostrNpub = nostrNpub;
    }

    
    public void setNostrPubKey(final String nostrPubKey) {
        this.nostrPubKey = nostrPubKey;
    }

    
    public void setTzOffset(@Nullable final Integer tzOffset) {
        this.tzOffset = tzOffset;
    }

    
    public void setDst(@Nullable final Boolean dst) {
        this.dst = dst;
    }

    public LoginRequest() {
    }
}
