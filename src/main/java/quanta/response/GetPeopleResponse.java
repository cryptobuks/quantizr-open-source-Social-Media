package quanta.response;

import java.util.LinkedList;
import java.util.List;

import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GetPeopleResponse extends ResponseBase {
    private FriendInfo nodeOwner;
    private List<FriendInfo> people;
    private LinkedList<String> friendHashTags;
}
