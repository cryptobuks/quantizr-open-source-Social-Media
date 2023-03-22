package quanta.mongo.model;

import java.util.Date;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceConstructor;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.extern.slf4j.Slf4j;

/* We're going to be caching every Fediverse username we encounter for future unspecified purposes */
@Document(collection = "fediNames")
@TypeAlias("fn")
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({FediverseName.ID, FediverseName.NAME, FediverseName.CREATE_TIME})
@Slf4j 
public class FediverseName {
	public static final String ID = "_id";

	@Id
	@Field(ID)
	private ObjectId id;

	public static final String NAME = "name";
	@Field(NAME)
	private String name;

	public static final String CREATE_TIME = "ctm";
	@Field(CREATE_TIME)
	private Date createTime;

	@PersistenceConstructor
	public FediverseName() {
		/*
		 * WARNING: Do NOT initialize times (mod time or create time) in here this constructor gets called
		 * any time the persistence engine loads a node!!!!
		 */
	}

	// we don't annotate this because we have a custom getter.
	// @JsonProperty(FIELD_ID)
	public ObjectId getId() {
		return id;
	}

	@JsonProperty(ID)
	public void setId(ObjectId id) {
		this.id = id;
	}

	@JsonGetter(ID)
	public String jsonId() {
		return id != null ? id.toHexString() : null;
	}

	@JsonProperty(CREATE_TIME)
	public Date getCreateTime() {
		return createTime;
	}

	@JsonProperty(CREATE_TIME)
	public void setCreateTime(Date createTime) {
		this.createTime = createTime;
	}

	@JsonProperty(NAME)
	public String getName() {
		return name;
	}

	@JsonProperty(NAME)
	public void setName(String name) {
		this.name = name;
	}
}
