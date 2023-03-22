package quanta.response;

import org.springframework.data.annotation.Transient;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ServerPushInfo {
	/**
	 * Examples: type=='newNode' nodeId=[id of the node]
	 */
	@Transient
	@JsonIgnore
	private String type;

	public ServerPushInfo(String type) {
		this.type = type;
	}
}
