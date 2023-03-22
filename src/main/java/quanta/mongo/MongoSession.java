package quanta.mongo;

import org.bson.types.ObjectId;
import lombok.extern.slf4j.Slf4j;
import quanta.model.client.PrincipalName;

/**
 * Wraps the identity information about a specific user for access privileges to MongoDb
 */
@Slf4j 
public class MongoSession {
	private String userName;
	private ObjectId userNodeId;

	public MongoSession() {
		userName = PrincipalName.ANON.s();
	}

	public MongoSession(String userName, ObjectId userNodeId) {
		this.userName = userName;
		this.userNodeId = userNodeId;
	}

	public MongoSession clone() {
		return new MongoSession(userName, userNodeId);
	}

	public boolean isAdmin() {
		return PrincipalName.ADMIN.s().equals(userName);
	}

	public boolean isAnon() {
		return userName == null || PrincipalName.ANON.s().equals(userName);
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public ObjectId getUserNodeId() {
		return userNodeId;
	}

	public void setUserNodeId(ObjectId userNodeId) {
		this.userNodeId = userNodeId;
	}
}
