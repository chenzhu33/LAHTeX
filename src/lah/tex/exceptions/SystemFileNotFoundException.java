package lah.tex.exceptions;

/**
 * This exception should be raised when the some core file (xz, busybox, cp,
 * tar, rm, ...) is missing.
 * 
 * @author L.A.H.
 * 
 */
public class SystemFileNotFoundException extends Exception {

	private static final long serialVersionUID = 1L;

	private final String missing_system_file;

	public SystemFileNotFoundException(String file_name) {
		missing_system_file = file_name;
	}

	@Override
	public String getMessage() {
		return "Missing system file " + missing_system_file;
	}

	public String getMissingSystemFile() {
		return missing_system_file;
	}

}
