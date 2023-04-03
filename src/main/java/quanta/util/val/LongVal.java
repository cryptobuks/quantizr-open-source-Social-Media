package quanta.util.val;

public class LongVal extends Val<Long> {
	public LongVal() {
		super(0L);
	}
	

	public LongVal(Long v) {
		this.val = v;
	}

	public void add(long v) {
		this.val += v;
	}

	public void inc() {
		this.val++;
	}

	public void dec() {
		this.val--;
	}
}
