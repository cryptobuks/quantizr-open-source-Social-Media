package quanta.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model representing a filename
 */
@Data
@NoArgsConstructor
public class FileSearchResult {
	private String fileName;

	public FileSearchResult(String fileName) {
		this.fileName = fileName;
	}
}
