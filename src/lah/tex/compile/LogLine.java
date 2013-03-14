package lah.tex.compile;

/**
 * Output log item, can be a warning, an error, a bad-box warning of TeX
 */
public class LogLine {

	public static final byte LEVEL_ERROR = 0, LEVEL_WARNING = 1, LEVEL_OK = 2;

	public final String description;

	int line_number;

	public final byte severity_level;

	public LogLine(byte level, String desc) {
		severity_level = level;
		description = desc;
	}

	public String getDescription() {
		return description;
	}

	public int getLineNumber() {
		return line_number;
	}

	public byte getSeverity() {
		return severity_level;
	}

	protected void setLineNumber(int line_num) {
		line_number = line_num;
	}

	@Override
	public String toString() {
		return description;
	}

}