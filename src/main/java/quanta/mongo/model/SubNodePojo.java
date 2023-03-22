package quanta.mongo.model;

import java.util.Date;
import java.util.HashMap;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pure Pojo equivalent of SubNode.java, so we can do serialization to/from JSON without MongoDB
 * trying to get involved (no PersistenceConstructor issues)
 */
@Data
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({SubNode.PATH, SubNode.CONTENT, SubNode.NAME, SubNode.ID, SubNode.ORDINAL, SubNode.OWNER, SubNode.CREATE_TIME,
		SubNode.MODIFY_TIME, SubNode.AC, SubNode.PROPS})
public class SubNodePojo {
	private static final Logger log = LoggerFactory.getLogger(SubNodePojo.class);

	@JsonProperty(SubNode.ID)
	private ObjectId id;

	@JsonProperty(SubNode.ORDINAL)
	private Long ordinal;

	@JsonProperty(SubNode.PATH)
	private String path;

	@JsonProperty(SubNode.TYPE)
	private String type;

	@JsonProperty(SubNode.CONTENT)
	private String content;

	@JsonProperty(SubNode.NAME)
	private String name;

	@JsonProperty(SubNode.OWNER)
	private ObjectId owner;

	@JsonProperty(SubNode.CREATE_TIME)
	private Date createTime;

	@JsonProperty(SubNode.MODIFY_TIME)
	private Date modifyTime;

	@JsonProperty(SubNode.PROPS)
	private HashMap<String, Object> props;

	@JsonProperty(SubNode.AC)
	private HashMap<String, AccessControl> ac;
}
