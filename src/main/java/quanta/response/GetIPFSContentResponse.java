
package quanta.response;

import quanta.response.base.ResponseBase;

public class GetIPFSContentResponse extends ResponseBase {
    private String content;

    
    public String getContent() {
        return this.content;
    }

    
    public void setContent(final String content) {
        this.content = content;
    }

    public GetIPFSContentResponse() {
    }
}
