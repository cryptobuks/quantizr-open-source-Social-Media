
package quanta.response;

import quanta.response.base.ResponseBase;

public class MoveNodesResponse extends ResponseBase {
    private boolean signaturesRemoved;

    
    public boolean isSignaturesRemoved() {
        return this.signaturesRemoved;
    }

    
    public void setSignaturesRemoved(final boolean signaturesRemoved) {
        this.signaturesRemoved = signaturesRemoved;
    }

    public MoveNodesResponse() {
    }
}
