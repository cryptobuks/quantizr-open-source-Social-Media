package quanta.util.val;

public class IntVal extends Val<Integer> {
	public IntVal() {
		super(0);
	}

	public IntVal(Integer v) {
		super(v);
	}

	public void add(int v) {
		this.val += v;
	}

	public void inc() {
		this.val++;
	}

	public void dec() {
		this.val--;
	}
}
