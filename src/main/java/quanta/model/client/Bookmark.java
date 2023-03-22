package quanta.model.client;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Bookmark {
    private String name;
    private String id;
    private String selfId;
}
