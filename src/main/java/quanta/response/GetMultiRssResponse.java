package quanta.response;

import quanta.model.client.RssFeed;
import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GetMultiRssResponse extends ResponseBase {
    // JSON of the feed as a string.
    private RssFeed feed;
}
