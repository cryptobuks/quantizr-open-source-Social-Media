package quanta.util;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.InputStream;
import javax.imageio.ImageReader;
import lombok.extern.slf4j.Slf4j;

@Slf4j 
public class StreamUtil {
	public static void close(Object... objects) {
		for (Object obj : objects) {
			if (obj instanceof Closeable) {
				try {
					((Closeable) obj).close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else if (obj instanceof ImageReader) {
				try {
					((ImageReader) obj).dispose();
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				if (obj != null) {
					log.warn("Object to close was of unsupported type: " + obj.getClass().getName());
				}
			}
		}
	}

	public static boolean streamsIdentical(InputStream a, InputStream b) {

		/* wrap in Buffered streams only if not currently buffered */
		BufferedInputStream aBuffered = (a instanceof BufferedInputStream) ? (BufferedInputStream) a : new BufferedInputStream(a);
		BufferedInputStream bBuffered = (b instanceof BufferedInputStream) ? (BufferedInputStream) b : new BufferedInputStream(b);

		try {
			int aByte, bByte;

			/* read a byte from "a" */
			while ((aByte = aBuffered.read()) != -1) {

				/*
				 * if got an "a" but can't get a 'b' then streams are not same length, and this is the case where
				 * stream "a" was longer
				 */
				if ((bByte = bBuffered.read()) == -1) {
					return false;
				}

				/* if we got both bytes, compare them */
				if (aByte != bByte) {
					return false;
				}
			}

			/*
			 * once we ran to end of stream "a" make sure stream 'b' is also ended (checking that 'b' isn't
			 * longer than "a")
			 */
			if (bBuffered.read() != -1) {
				return false;
			}
		} catch (Exception ex) {
			throw ExUtil.wrapEx(ex);
		} finally {
			close(aBuffered, bBuffered);
		}
		return true;
	}
}
