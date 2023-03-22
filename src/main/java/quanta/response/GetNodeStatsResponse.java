package quanta.response;

import java.util.ArrayList;
import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GetNodeStatsResponse extends ResponseBase {
    private String stats;
    private ArrayList<String> topWords;
    private ArrayList<String> topTags;
    private ArrayList<String> topMentions;
    private ArrayList<String> topVotes;
}
