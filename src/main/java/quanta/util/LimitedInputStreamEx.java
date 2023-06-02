package quanta.util;

import java.io.IOException;
import java.io.InputStream;
import quanta.exception.OutOfSpaceException;

/**
 * Wraps a stream to a certain limited size so that it will simply throw an exception if the stream
 * it is processing ends up being too large. This is required because many times when processing a
 * stream you can't tell in advance how big the stream is going to be.
 */
public class LimitedInputStreamEx extends LimitedInputStream {

	public LimitedInputStreamEx(InputStream pIn, long pSizeMax) {
		super(pIn, pSizeMax);
	}

	@Override
	protected void raiseError(long pSizeMax, long pCount) throws IOException {
		throw new OutOfSpaceException();
	}
}