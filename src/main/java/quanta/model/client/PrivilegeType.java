package quanta.model.client;

public enum PrivilegeType {
	/* Can read the node and entire subgraph of nodes it contains */
	READ("rd"), //

	/* Can read and create children under this node. Write to subnodes is not granted by this. */
	WRITE("wr"); //

	public final String name;

	private PrivilegeType(String name) {
		this.name = name;
	}

	public String toString() {
		return name;
	}

	public String s() {
		return name;
	}
}
