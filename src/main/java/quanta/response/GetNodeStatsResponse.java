
package quanta.response;

import java.util.ArrayList;
import quanta.response.base.ResponseBase;

public class GetNodeStatsResponse extends ResponseBase {
    private String stats;
    private ArrayList<String> topWords;
    private ArrayList<String> topTags;
    private ArrayList<String> topMentions;
    private ArrayList<String> topVotes;

    
    public String getStats() {
        return this.stats;
    }

    
    public ArrayList<String> getTopWords() {
        return this.topWords;
    }

    
    public ArrayList<String> getTopTags() {
        return this.topTags;
    }

    
    public ArrayList<String> getTopMentions() {
        return this.topMentions;
    }

    
    public ArrayList<String> getTopVotes() {
        return this.topVotes;
    }

    
    public void setStats(final String stats) {
        this.stats = stats;
    }

    
    public void setTopWords(final ArrayList<String> topWords) {
        this.topWords = topWords;
    }

    
    public void setTopTags(final ArrayList<String> topTags) {
        this.topTags = topTags;
    }

    
    public void setTopMentions(final ArrayList<String> topMentions) {
        this.topMentions = topMentions;
    }

    
    public void setTopVotes(final ArrayList<String> topVotes) {
        this.topVotes = topVotes;
    }

    public GetNodeStatsResponse() {
    }
}
