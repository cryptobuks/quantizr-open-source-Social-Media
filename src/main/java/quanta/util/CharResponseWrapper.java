package quanta.util;

import java.io.CharArrayWriter;
import java.io.PrintWriter;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * Wrapper for getting a PrintWriter from a CharArrayWriter.
 */
public class CharResponseWrapper extends HttpServletResponseWrapper {
	private CharArrayWriter output;

	public String toString() {
		return output.toString();
	}

	public CharResponseWrapper(HttpServletResponse response) {
		super(response);
		output = new CharArrayWriter();
	}

	public PrintWriter getWriter() {
		return new PrintWriter(output);
	}
}