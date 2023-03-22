package quanta.mongo.model;

import org.bson.types.ObjectId;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
 * userNodeId is required. userName is optional
 * 
 * accessLevels: w = read/write r = readonly
 */

@Data
@NoArgsConstructor
public class MongoPrincipal {
	private ObjectId userNodeId;
	private String userName;
	private String accessLevel;
}
