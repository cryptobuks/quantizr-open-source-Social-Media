package quanta.exception;

import quanta.exception.base.RuntimeEx;

public class HashVerifyFailedException extends RuntimeEx {

	public HashVerifyFailedException(String msg) {
		super(msg);
	}
}
