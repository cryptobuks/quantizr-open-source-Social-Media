package quanta.util;

import org.springframework.stereotype.Component;
import quanta.config.ServiceBase;

@Component
public class Validator extends ServiceBase {

	/*
	 * UserName requirements, between 5 and 100 characters (inclusive) long, and only allowing digits,
	 * letters, underscore, dash, and space.
	 * 
	 * Note that part of our requirement is that it must also be a valid substring inside node path
	 * names, that are used or looking up things about this user.
	 * 
	 * todo-1: fix inconsistency here. Either always return an error string or always throw when error
	 */
	public String checkUserName(String userName) {
		if (!auth.isAllowedUserName(userName)) {
			return "Invalid or illegal user name.";
		}

		if (userName.contains("--")) {
			throw ExUtil.wrapEx("Username cannot contain '--'");
		}

		int len = userName.length();
		if (len < 3 || len > 100)
			throw ExUtil.wrapEx("Username must be between 3 and 100 characters long.");

		for (int i = 0; i < len; i++) {
			char c = userName.charAt(i);
			// WARNING: Never allow '.' in here because by convention we know names starting with '.' are nostr
			// users
			if (!(Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ' ')) {
				return "Username can contain only letters, digits, dashes, underscores, and spaces. invalid[" + userName + "]";
			}
		}
		return null;
	}

	/* passwords are only checked for length of 5 thru 100 */
	public String checkPassword(String password) {
		int len = password.length();
		if (len < 5 || len > 40)
			return "Password must be between 5 and 40 characters long.";
		return null;
	}

	public String checkEmail(String email) {
		int len = email.length();
		if (len < 5 || len > 100)
			return "Invalid email address";
		return null;
	}
}
