package lah.tex.compile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lah.tex.core.BaseResult;
import lah.tex.interfaces.ICompilationCommand;
import lah.tex.interfaces.ICompilationResult;

class TeXMFResult extends BaseResult implements ICompilationResult {

	static final Pattern badboxPattern = Pattern
			.compile("(Over|Under)(full \\\\[hv]box .*)");

	static final Pattern errorPattern = Pattern.compile("! (.*)");

	static final Pattern lineNumberPattern = Pattern
			.compile("(l\\.|line |lines )\\s*(\\d+)[^\\d].*");

	static final Pattern warningPattern = Pattern
			.compile("(((! )?(La|pdf)TeX)|Package) .*Warning.*:(.*)");

	final Matcher badboxMatcher = badboxPattern.matcher("");

	private ICompilationCommand compilation_command;

	final Matcher errorMatcher = errorPattern.matcher("");

	private int exit_value;

	final Matcher lineNumberMatcher = lineNumberPattern.matcher("");

	private List<LogLine> logs;

	private int state;

	final Matcher warningMatcher = warningPattern.matcher("");

	public TeXMFResult() {
		super();
	}

	public TeXMFResult(Exception e) {
		super(e);
	}

	void appendLog(String line) {
		if (line == null)
			return;
		if (logs == null)
			logs = new ArrayList<LogLine>();
		if (warningMatcher.reset(line).matches())
			logs.add(new LogLine(LogLine.LEVEL_WARNING, line));
		else if (badboxMatcher.reset(line).matches())
			logs.add(new LogLine(LogLine.LEVEL_WARNING, line));
		else if (errorMatcher.reset(line).matches())
			logs.add(new LogLine(LogLine.LEVEL_ERROR, line));
		else
			logs.add(new LogLine(LogLine.LEVEL_OK, line));
	}

	@Override
	public ICompilationCommand getCompilationCommand() {
		return compilation_command;
	}

	/**
	 * Get the exit value of the compilation process
	 * 
	 * @return
	 */
	public int getExitValue() {
		return exit_value;
	}

	@Override
	public LogLine getLogLine(int index) {
		if (logs != null && index < logs.size())
			return logs.get(index);
		return null;
	}

	/**
	 * Get the {@link File} links to the output PDF, DVI, ... of the compilation
	 * process
	 * 
	 * @return
	 */
	@Override
	public File getOutputFile() {
		ICompilationCommand cmd;
		if ((cmd = getCompilationCommand()) != null)
			return new File(cmd.getDirectory() + "/"
					+ cmd.getInputFileWithoutExt() + "." + getOutputFileType());
		return null;
	}

	@Override
	public String getOutputFileType() {
		return getCompilationCommand() == null ? null : getCompilationCommand()
				.getOutputType();
	}

	@Override
	public boolean isComplete() {
		return state == STATE_COMPLETE;
	}

	void setCompilationCommand(ICompilationCommand cmd) {
		compilation_command = cmd;
	}

	@Override
	public void setException(Exception e) {
		super.setException(e);
		setState(STATE_COMPLETE);
	}

	void setState(int s) {
		state = s;
	}

}
