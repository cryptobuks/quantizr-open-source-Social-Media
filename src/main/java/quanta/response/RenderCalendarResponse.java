package quanta.response;

import java.util.LinkedList;

import quanta.model.CalendarItem;
import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RenderCalendarResponse extends ResponseBase {
	private LinkedList<CalendarItem> items;
}
