package quanta.response;

import quanta.model.NodeInfo;
import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateSubNodeResponse extends ResponseBase {
	private NodeInfo newNode;

	/*
	 * This is set to true in the response, when the parent node is encrypted and so we default the new
	 * child to be encrypted also.
	 * 
	 * Mainly used in a 'reply' to an encrypted node.
	 */
	private boolean encrypt;
}
