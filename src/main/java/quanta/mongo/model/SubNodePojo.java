
package quanta.mongo.model;

import java.util.Date;
import java.util.HashMap;
import org.bson.types.ObjectId;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pure Pojo equivalent of SubNode.java, so we can do serialization to/from JSON without MongoDB
 * trying to get involved (no PersistenceConstructor issues)
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({SubNode.PATH, SubNode.CONTENT, SubNode.NAME, SubNode.ID, SubNode.ORDINAL, SubNode.OWNER, SubNode.CREATE_TIME, SubNode.MODIFY_TIME, SubNode.AC, SubNode.PROPS})
public class SubNodePojo {
	
	private static Logger log = LoggerFactory.getLogger(SubNodePojo.class);
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
	
	public ObjectId getId() {
		return this.id;
	}
	
	public Long getOrdinal() {
		return this.ordinal;
	}
	
	public String getPath() {
		return this.path;
	}
	
	public String getType() {
		return this.type;
	}

	public String getContent() {
		return this.content;
	}

	public String getName() {
		return this.name;
	}
	
	public ObjectId getOwner() {
		return this.owner;
	}

	public Date getCreateTime() {
		return this.createTime;
	}

	public Date getModifyTime() {
		return this.modifyTime;
	}

	public HashMap<String, Object> getProps() {
		return this.props;
	}
	
	public HashMap<String, AccessControl> getAc() {
		return this.ac;
	}

	@JsonProperty(SubNode.ID)
	public void setId(final ObjectId id) {
		this.id = id;
	}

	@JsonProperty(SubNode.ORDINAL)
	public void setOrdinal(final Long ordinal) {
		this.ordinal = ordinal;
	}

	@JsonProperty(SubNode.PATH)
	public void setPath(final String path) {
		this.path = path;
	}

	@JsonProperty(SubNode.TYPE)
	public void setType(final String type) {
		this.type = type;
	}

	@JsonProperty(SubNode.CONTENT)
	public void setContent(final String content) {
		this.content = content;
	}

	@JsonProperty(SubNode.NAME)
	public void setName(final String name) {
		this.name = name;
	}

	@JsonProperty(SubNode.OWNER)
	public void setOwner(final ObjectId owner) {
		this.owner = owner;
	}

	@JsonProperty(SubNode.CREATE_TIME)
	public void setCreateTime(final Date createTime) {
		this.createTime = createTime;
	}

	@JsonProperty(SubNode.MODIFY_TIME)
	public void setModifyTime(final Date modifyTime) {
		this.modifyTime = modifyTime;
	}

	@JsonProperty(SubNode.PROPS)
	public void setProps(final HashMap<String, Object> props) {
		this.props = props;
	}

	@JsonProperty(SubNode.AC)
	public void setAc(final HashMap<String, AccessControl> ac) {
		this.ac = ac;
	}
	
	public SubNodePojo() {
	}
}
