
package quanta.request;

import quanta.request.base.RequestBase;

public class SavePublicKeyRequest extends RequestBase {
	private String asymEncKey;
	private String sigKey;
	private String nostrNpub;
	private String nostrPubKey;

	public String getAsymEncKey() {
		return this.asymEncKey;
	}
	
	public String getSigKey() {
		return this.sigKey;
	}
	
	public String getNostrNpub() {
		return this.nostrNpub;
	}
	
	public String getNostrPubKey() {
		return this.nostrPubKey;
	}
	
	public void setAsymEncKey(final String asymEncKey) {
		this.asymEncKey = asymEncKey;
	}
	
	public void setSigKey(final String sigKey) {
		this.sigKey = sigKey;
	}
	
	public void setNostrNpub(final String nostrNpub) {
		this.nostrNpub = nostrNpub;
	}
	
	public void setNostrPubKey(final String nostrPubKey) {
		this.nostrPubKey = nostrPubKey;
	}

	public SavePublicKeyRequest() {
	}
}
