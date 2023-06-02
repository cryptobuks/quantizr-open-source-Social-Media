
package quanta.response;

public class InfoMessage {
    String message;
    // Types: note==null | inbox
    String type;

    public InfoMessage(String message, String type) {
        this.message = message;
        this.type = type;
    }

    
    public String getMessage() {
        return this.message;
    }

    
    public String getType() {
        return this.type;
    }

    
    public void setMessage(final String message) {
        this.message = message;
    }

    
    public void setType(final String type) {
        this.type = type;
    }

    public InfoMessage() {
    }
}
