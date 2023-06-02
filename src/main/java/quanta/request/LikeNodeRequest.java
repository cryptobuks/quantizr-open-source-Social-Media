
package quanta.request;

import quanta.request.base.RequestBase;

public class LikeNodeRequest extends RequestBase {
	private String id;
	private boolean like;

	
	public String getId() {
		return this.id;
	}

	
	public boolean isLike() {
		return this.like;
	}

	
	public void setId(final String id) {
		this.id = id;
	}

	
	public void setLike(final boolean like) {
		this.like = like;
	}

	public LikeNodeRequest() {
	}
}
