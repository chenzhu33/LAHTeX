package lah.tex.compile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lah.tex.core.BaseTask;
import lah.tex.interfaces.ICompilationCommand;
import lah.tex.interfaces.ICompilationResult;

public class CompilationTask extends BaseTask implements ICompilationResult {

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

	private String tex_engine, tex_src;

	final Matcher warningMatcher = warningPattern.matcher("");

	public CompilationTask() {
		super();
	}

	public CompilationTask(Exception e) {
		super(e);
	}

	public CompilationTask(String tex_engine, String tex_src) {
		this.tex_engine = tex_engine;
		this.tex_src = tex_src;
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

	@Override
	public CharSequence getDescription() {
		return tex_engine + " " + tex_src;
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

	@Override
	public void run() {
		status = "Executing";
		File tex_src_file = new File(tex_src);
		if (tex_src_file.exists()) {
			// compile the input file using the engine
			System.out.println(tex_engine + " " + tex_src_file.getName());
			// portal.setLatestInputFile(tex_src);
			// portal.getTeXMF().compile(this,
			// new CompilationCommand(tex_engine, tex_src_file), 100000);
			status = "Complete";
		} else {
			System.out.println("ERROR: " + tex_src + " does not exist!");
			// set the error to FileNotFoundException
		}
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
