
package quanta.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class BreadcrumbInfo {
	private String id;
	private String name;
	private String type;
	
	public String getId() {
		return this.id;
	}
	
	public String getName() {
		return this.name;
	}
	
	public String getType() {
		return this.type;
	}
	
	public void setId(final String id) {
		this.id = id;
	}
	
	public void setName(final String name) {
		this.name = name;
	}

	public void setType(final String type) {
		this.type = type;
	}
	
	public BreadcrumbInfo() {
	}
}
