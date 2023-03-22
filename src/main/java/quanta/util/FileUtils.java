package quanta.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.comparator.NameFileComparator;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.exception.base.RuntimeEx;

@Component
@Slf4j 
public class FileUtils extends ServiceBase {
	/*
	 * Creates the set of file extensions that we allow user to edit
	 */
	private static HashSet<String> editableExtensions = new HashSet<>();
	static {
		editableExtensions.add("md");
		editableExtensions.add("txt");
		editableExtensions.add("sh");
	}

	private static HashSet<String> imageExtensions = new HashSet<>();
	static {
		imageExtensions.add("jpg");
		imageExtensions.add("png");
		imageExtensions.add("gif");
		imageExtensions.add("bmp");
	}

	public String genHashOfClasspathResource(String resourceName) {
		InputStream is = null;
		try {
			Resource resource = context.getResource("classpath:" + resourceName);
			is = resource.getInputStream();
			return DigestUtils.md5Hex(is);
		} catch (Exception e) {
			throw new RuntimeEx("Unable to hash resource: " + resourceName, e);
		} finally {
			StreamUtil.close(is);
		}
	}

	public long getFileCreateTime(File file) {
		try {
			BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
			return attr.creationTime().toMillis();
			// System.out.println("lastAccessTime: " + attr.lastAccessTime());
		} catch (Exception e) {
			return -1;
		}
	}

	public boolean isEditableFile(String fileName) {
		String ext = FilenameUtils.getExtension(fileName);
		boolean ret = editableExtensions.contains(ext.toLowerCase());
		// log.debug("EDITABLE: " + fileName + " -> " + ret + " EXT: " +
		// ext.toLowerCase());
		return ret;
	}

	public boolean isImageFile(String fileName) {
		String ext = FilenameUtils.getExtension(fileName);
		boolean ret = imageExtensions.contains(ext.toLowerCase());
		// log.debug("EDITABLE: " + fileName + " -> " + ret + " EXT: " +
		// ext.toLowerCase());
		return ret;
	}

	/**
	 * input: /home/clay/path/file.txt
	 * 
	 * output: file.txt
	 */
	public String getShortFileName(String fileName) {
		if (fileName == null)
			return null;

		String shortName = null;
		int idx = fileName.lastIndexOf(File.separatorChar);
		if (idx != -1) {
			shortName = fileName.substring(idx + 1);
		} else {
			shortName = fileName;
		}
		// log.debug("Short name of [" + fileName + "] is [" + shortName + "]");
		return shortName;
	}

	/**
	 * input: /home/clay/path/file.txt output: txt
	 * 
	 * If no extension exists empty string is returned
	 */
	public String getFileNameExtension(String fileName) {
		if (fileName == null)
			return null;

		String ext = null;
		int idx = fileName.lastIndexOf(".");
		if (idx != -1) {
			ext = fileName.substring(idx + 1);
		} else {
			ext = "";
		}
		return ext;
	}

	public String stripExtension(String fileName) {
		if (fileName == null)
			return null;

		String ret = null;
		int idx = fileName.lastIndexOf(".");
		if (idx != -1) {
			ret = fileName.substring(0, idx);
		} else {
			ret = fileName;
		}
		return ret;
	}

	public static String readFile(File file) {
		try {
			return readFile(file.getCanonicalPath());
		} catch (Exception e) {
			throw new RuntimeEx("unable to read fil.", e);
		}
	}

	public static String readFile(String path) {
		try {
			byte[] encoded = Files.readAllBytes(Paths.get(path));
			return new String(encoded, StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new RuntimeEx("unable to read file: " + path, e);
		}
	}

	public static String getResourceFileString(String fileName) {
		try {
			ClassLoader classLoader = FileUtils.class.getClassLoader();
			InputStream inputStream = classLoader.getResourceAsStream(fileName);
			String val = XString.getStringFromStream(inputStream);
			return val;
		} catch (Exception e) {
			throw new RuntimeEx("unable to read resource file: " + fileName, e);
		}
	}

	public File[] getSortedListOfFolders(String folder, Set<String> exclusions) {
		File directory = new File(folder);
		if (!directory.isDirectory()) {
			throw new RuntimeEx("Folder doesn't exist: " + folder);
		}

		/* First read folders and sort them */
		File[] folders = directory.listFiles(new FileFilter() {
			@Override
			public boolean accept(File file) {
				if (exclusions != null && exclusions.contains(file.getAbsolutePath())) {
					return false;
				}
				return file.isDirectory() && !file.getName().startsWith(".");
			}
		});
		if (folders != null) {
			Arrays.sort(folders, NameFileComparator.NAME_COMPARATOR);
		}

		// log.debug("folderCount=" + (ok(folders) ? folders.length : 0));
		return folders;
	}

	public File[] getSortedListOfFiles(String folder, Set<String> exclusions) {
		File directory = new File(folder);
		if (!directory.isDirectory()) {
			throw new RuntimeEx("Folder doesn't exist: " + folder);
		}
		/* Then read files and sort them */
		File[] files = directory.listFiles(new FileFilter() {
			@Override
			public boolean accept(File file) {
				if (exclusions != null && exclusions.contains(file.getAbsolutePath())) {
					return false;
				}
				return file.isFile() && (file.getName().equals(".meta-fs") || !file.getName().startsWith("."));
			}
		});
		if (files != null) {
			Arrays.sort(files, NameFileComparator.NAME_COMPARATOR);
		}
		// log.debug("fileCount=" + (ok(files) ? files.length : 0));
		return files;
	}

	public boolean fileOrFolderExists(String fileName) {
		if (fileName == null || fileName.trim().length() == 0)
			return false;
		return new File(fileName).exists();
	}

	public static boolean fileExists(String fileName) {
		if (fileName == null || fileName.equals(""))
			return false;

		return new File(fileName).isFile();
	}

	public static boolean dirExists(String fileName) {
		if (fileName == null || fileName.equals(""))
			return false;

		return new File(fileName).isDirectory();
	}

	public static boolean deleteFile(String fileName) {
		File f = new File(fileName);
		boolean exists = f.exists();
		if (!exists)
			return true;
		return f.delete();
	}

	public static boolean createDirectory(String dir) {
		File file = new File(dir);
		if (file.isDirectory())
			return true;
		boolean success = file.mkdirs();
		log.debug("Created folder: " + dir + ". success=" + success);
		return success;
	}

	public static void makeFileRunnable(String fileName) throws Exception {
		Set<PosixFilePermission> perms = new HashSet<>();
		perms.add(PosixFilePermission.OWNER_READ);
		perms.add(PosixFilePermission.OWNER_WRITE);
		perms.add(PosixFilePermission.OWNER_EXECUTE);
		perms.add(PosixFilePermission.GROUP_READ);
		perms.add(PosixFilePermission.GROUP_WRITE);
		perms.add(PosixFilePermission.GROUP_EXECUTE);
		perms.add(PosixFilePermission.OTHERS_READ);
		perms.add(PosixFilePermission.OTHERS_WRITE);
		perms.add(PosixFilePermission.OTHERS_EXECUTE);
		Files.setPosixFilePermissions(Paths.get(fileName), perms);
	}

	public static void writeEntireFile(String fileName, String content) {
		try {
			BufferedWriter out = new BufferedWriter(new FileWriter(fileName));
			try {
				out.write(content);
				out.flush();
			} finally {
				StreamUtil.close(out);
			}
		} catch (Exception ex) {
			throw ExUtil.wrapEx(ex);
		}
	}

	public static String ensureValidFileNameChars(String text) {
		if (text == null)
			return null;

		int length = text.length();
		StringBuilder ret = new StringBuilder();
		char c;
		boolean lastWasDash = false;

		for (int i = 0; i < length; i++) {
			c = text.charAt(i);

			if (Character.isLetter(c) || Character.isDigit(c) || c == '.' || c == '-' || c == '_') {
				ret.append(c);
				lastWasDash = false;
			} else {
				if (!lastWasDash) {
					ret.append('-');
				}
				lastWasDash = true;
			}
		}
		return ret.toString();
	}

}
