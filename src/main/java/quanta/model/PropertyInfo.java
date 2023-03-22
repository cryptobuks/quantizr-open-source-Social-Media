package quanta.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Holds the value of a single property (i.e. a property 'value' on a Node)
 */
@Data
@NoArgsConstructor
public class PropertyInfo {
	private String name;
	private Object value;

	public PropertyInfo(String name, Object value) {
		this.name = name;
		this.value = value;
	}
}
