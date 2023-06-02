
package quanta.response;

import java.util.List;
import quanta.response.base.ResponseBase;

public class SaveNostrEventResponse extends ResponseBase {
    // Returns a list of all matching nodes (SubNode nodeIds) for all the events being persisted,
    // but omitting the metadata events.
    List<String> eventNodeIds;
    // any accounts related to the request will be sent back as the MongoDB of the node created node
    private List<String> accntNodeIds;
    private Integer saveCount;

    
    public List<String> getEventNodeIds() {
        return this.eventNodeIds;
    }

    
    public List<String> getAccntNodeIds() {
        return this.accntNodeIds;
    }

    
    public Integer getSaveCount() {
        return this.saveCount;
    }

    
    public void setEventNodeIds(final List<String> eventNodeIds) {
        this.eventNodeIds = eventNodeIds;
    }

    
    public void setAccntNodeIds(final List<String> accntNodeIds) {
        this.accntNodeIds = accntNodeIds;
    }

    
    public void setSaveCount(final Integer saveCount) {
        this.saveCount = saveCount;
    }

    public SaveNostrEventResponse() {
    }
}
