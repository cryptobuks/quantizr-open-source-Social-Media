package quanta.model.client;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class APObjUrl {
    private String type;
    private String mediaType;
    private String href;
}
