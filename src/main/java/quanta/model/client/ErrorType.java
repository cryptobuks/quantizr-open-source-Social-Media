package quanta.model.client;

public enum ErrorType {
	/* Can read the node and entire subgraph of nodes it contains */
	OUT_OF_SPACE("oos"), //

	// not used yet.
	TIMEOUT("timeout"), //
	
	AUTH("auth"); //

	public final String name;

	private ErrorType(String name) {
		this.name = name;
	}

	public String toString() {
		return name;
	}

	public String s() {
		return name;
	}
}
