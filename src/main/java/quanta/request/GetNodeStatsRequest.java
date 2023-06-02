
package quanta.request;

import quanta.request.base.RequestBase;

public class GetNodeStatsRequest extends RequestBase {
    private String nodeId;
    private boolean trending;
    private boolean signatureVerify;
    /* True if this will be the trending button on the Feed tab running this for the Feed tab */
    private boolean feed;
    private boolean getWords;
    private boolean getMentions;
    private boolean getTags;

    
    public String getNodeId() {
        return this.nodeId;
    }

    
    public boolean isTrending() {
        return this.trending;
    }

    
    public boolean isSignatureVerify() {
        return this.signatureVerify;
    }

    
    public boolean isFeed() {
        return this.feed;
    }

    
    public boolean isGetWords() {
        return this.getWords;
    }

    
    public boolean isGetMentions() {
        return this.getMentions;
    }

    
    public boolean isGetTags() {
        return this.getTags;
    }

    
    public void setNodeId(final String nodeId) {
        this.nodeId = nodeId;
    }

    
    public void setTrending(final boolean trending) {
        this.trending = trending;
    }

    
    public void setSignatureVerify(final boolean signatureVerify) {
        this.signatureVerify = signatureVerify;
    }

    
    public void setFeed(final boolean feed) {
        this.feed = feed;
    }

    
    public void setGetWords(final boolean getWords) {
        this.getWords = getWords;
    }

    
    public void setGetMentions(final boolean getMentions) {
        this.getMentions = getMentions;
    }

    
    public void setGetTags(final boolean getTags) {
        this.getTags = getTags;
    }

    public GetNodeStatsRequest() {
    }
}
