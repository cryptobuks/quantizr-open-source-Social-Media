package quanta.util.val;

/**
 * An output-parameter object.
 * 
 * Allows passing an object as a parameter to a method and then getting data back out of that
 * parameter. Like doing output parameters in Java. Ordinarily used when the more than just one
 * return value from a method call is needed.
 */
public class Val<T> {
	protected T val;

	public Val() {}

	public Val(T v) {
		this.val = v;
	}

	public T getVal() {
		return val;
	}

	public void setVal(T val) {
		this.val = val;
	}

	public boolean hasVal() {
		return val != null;
	}
}
