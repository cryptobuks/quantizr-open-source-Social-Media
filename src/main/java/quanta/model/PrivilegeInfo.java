package quanta.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a privilege name
 */
@Data
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
public class PrivilegeInfo {
	private String privilegeName;

	public PrivilegeInfo(String privilegeName) {
		this.privilegeName = privilegeName;
	}
}
