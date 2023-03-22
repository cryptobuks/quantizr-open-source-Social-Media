package quanta.mongo.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SubNodeIdentity {
	public static final String FIELD_ID = "_id";

	@JsonProperty(FIELD_ID)
	private String id;
}
