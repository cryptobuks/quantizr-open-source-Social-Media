
package quanta.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Represents a privilege name
 */
@JsonInclude(Include.NON_NULL)
public class PrivilegeInfo {
	private String privilegeName;

	public PrivilegeInfo(String privilegeName) {
		this.privilegeName = privilegeName;
	}

	public String getPrivilegeName() {
		return this.privilegeName;
	}

	public void setPrivilegeName(final String privilegeName) {
		this.privilegeName = privilegeName;
	}
	
	public PrivilegeInfo() {
	}
}
