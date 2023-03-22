package quanta.response;

import java.util.List;
import quanta.model.client.Bookmark;
import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GetBookmarksResponse extends ResponseBase {
    private List<Bookmark> bookmarks;
}
