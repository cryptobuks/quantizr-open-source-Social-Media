package quanta.exception.base;

public class RuntimeEx extends RuntimeException {

	public RuntimeEx() {
		super();
	}

	public RuntimeEx(String msg) {
		super(msg);
	}

	public RuntimeEx(String msg, Throwable ex) {
		super(msg, ex);
	}

	public RuntimeEx(Throwable ex) {
		super(ex);
	}
}
