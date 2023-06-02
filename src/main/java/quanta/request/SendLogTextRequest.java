
package quanta.request;

import quanta.request.base.RequestBase;

public class SendLogTextRequest extends RequestBase {
    private String text;

    
    public String getText() {
        return this.text;
    }

    
    public void setText(final String text) {
        this.text = text;
    }

    public SendLogTextRequest() {
    }
}
