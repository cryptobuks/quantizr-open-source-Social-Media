package quanta.mongo.model;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Field;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;

/**
 * Models access privileges of a single 'share', along with the encrypted symetric key to the data,
 * for the user it represents.
 */
@TypeAlias("ac")
@JsonInclude(Include.NON_NULL)
@Slf4j 
public class AccessControl {
	public static final String FIELD_PRVS = "prvs";
	@Field(FIELD_PRVS)
	private String prvs;

	// This is Encryption Cypher key for this ACL entry.
	public static final String FIELD_KEY = "key";
	@Field(FIELD_KEY)
	private String key;

	public AccessControl() {
	}

	public AccessControl(String key, String prvs) {
		this.key = key;
		this.prvs = prvs;
	}

	@JsonProperty(FIELD_PRVS)
	public String getPrvs() {
		return this.prvs;
	}

	@JsonProperty(FIELD_PRVS)
	public void setPrvs(String prvs) {
		this.prvs = prvs;
	}

	@JsonProperty(FIELD_KEY)
	public String getKey() {
		return this.key;
	}

	@JsonProperty(FIELD_KEY)
	public void setKey(String key) {
		this.key = key;
	}

	public boolean eq(AccessControl ac) {
		return StringUtils.equals(key, ac.key) && StringUtils.equals(prvs, ac.prvs);
	}
}
