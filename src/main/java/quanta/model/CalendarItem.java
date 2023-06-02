
package quanta.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class CalendarItem {
    private String id;
    private String title;
    private long start;
    private long end;

    
    public String getId() {
        return this.id;
    }

    
    public String getTitle() {
        return this.title;
    }

    
    public long getStart() {
        return this.start;
    }

    
    public long getEnd() {
        return this.end;
    }

    
    public void setId(final String id) {
        this.id = id;
    }

    
    public void setTitle(final String title) {
        this.title = title;
    }

    
    public void setStart(final long start) {
        this.start = start;
    }

    
    public void setEnd(final long end) {
        this.end = end;
    }
    
    public CalendarItem() {
    }
}
