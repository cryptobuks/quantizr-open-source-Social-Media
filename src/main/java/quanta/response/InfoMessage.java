package quanta.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class InfoMessage {
    String message;

    // Types: note==null | inbox
    String type;

    public InfoMessage(String message, String type) {
        this.message = message;
        this.type = type;
    }
}
