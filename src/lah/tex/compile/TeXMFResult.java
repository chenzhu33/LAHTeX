package lah.tex.compile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Pattern;

import lah.tex.core.BaseResult;
import lah.tex.interfaces.ICompilationCommand;
import lah.tex.interfaces.ICompilationResult;

class TeXMFResult extends BaseResult implements ICompilationResult {

	private ICompilationCommand compilation_command;

	private int exit_value;

	@SuppressWarnings("unused")
	private File log_file;

	// final Matcher matcherBadbox = regexBadbox.matcher("");
	//
	// // And the corresponding Matcher's
	// final Matcher matcherError = regexError.matcher("");
	// final Matcher matcherLineNum = regexLineNum.matcher("");
	// final Matcher matcherWarning = regexWarning.matcher("");

	private List<LogLine> logs;
	final Pattern regexBadbox = Pattern
			.compile("(Over|Under)(full \\\\[hv]box .*)");
	final Pattern regexError = Pattern.compile("! (.*)");
	final Pattern regexLineNum = Pattern
			.compile("(l\\.|line |lines )\\s*(\\d+)[^\\d].*");

	final Pattern regexWarning = Pattern
			.compile("(((! )?(La|pdf)TeX)|Package) .*Warning.*:(.*)");

	private int state;

	public TeXMFResult() {
		super();
	}

	public TeXMFResult(Exception e) {
		super(e);
	}

	/**
	 * Analyze the log and returns an array of items
	 * 
	 * @param log
	 * @return
	 */
	@SuppressWarnings("unused")
	private LogLine[] analyzeTeXLog(String log) {
		final Pattern regexNewline = Pattern.compile("\\n|\\r\\n");

		// Patterns for file name matching, taken from TeXWork's logParser.js
		final Pattern regexFile = Pattern
				.compile("^\\(\"((?:\\./|/|\\.\\\\|[a-zA-Z]:\\\\|\\\\\\\\)(?:[^\"]|\n)+)\"|^\\(((?:\\./|/|\\.\\\\|[a-zA-Z]:\\\\|\\\\\\\\)[^ ()\n]+)");
		final Pattern fileContinuingRegexp = Pattern.compile("[/\\\\ ()\n]");
		final Pattern regexFileName = Pattern
				.compile("[^\\.]\\.[a-zA-Z0-9]{1,4}$");
		final Pattern regexParens = Pattern.compile("\\((?:[^()]|\n)*\\)");

		// Split the log into lines
		String[] log_lines = regexNewline.split(log);

		// The final result
		LogLine[] res = new LogLine[log_lines.length];

		// File stack to keep track of included files (for example, via
		// the command \input{})
		Stack<File> files = new Stack<File>();

		// Go through line-by-line, look for patterns
		for (int i = 0; i < log_lines.length; i++) {
			String line = log_lines[i];

			// if (line.charAt(0) == '(') {
			// // New file gets included
			// files.add(new File(line));
			// }
			// else if (line.charAt(0) == ')') {
			// // Included file is fully processed
			// // so we pop it out from the stack
			// files.pop();
			// }
			// else {
			// matcherError.reset(line);
			// matcherBadbox.reset(line);
			// matcherWarning.reset(line);
			// matcherLineNum.reset(line);
			// // System.out.print(line + " -[LogParser]-> ");
			// if (matcherError.matches()) {
			// // System.out.println("Error");
			// res[i] = new LogItem(LogSeverity.Error, line);
			// } else if (matcherBadbox.matches()) {
			// // System.out.println("BadBox");
			// res[i] = new LogItem(LogSeverity.BadBox, line);
			// } else if (matcherWarning.matches()) {
			// // System.out.println("Warning");
			// res[i] = new LogItem(LogSeverity.Warning, line);
			// } else {
			// // System.out.println("Normal");
			// res[i] = new LogItem(LogSeverity.Normal, line);
			// if (matcherLineNum.matches()) {
			// int line_num = Integer.parseInt(matcherLineNum.group(2));
			// // System.out.println("TeX.analyzeTeXLog : line number found "
			// // + line_num);
			//
			// // Find the nearest warning/error/bad-box item (if
			// // possible)
			// // and assign its concerning line number. This idea
			// // comes
			// // from Texmaker (source file logeditor.cpp).
			// int j = i - 1;
			// while (j >= 0
			// && (res[j].getSeverity() == LogSeverity.Normal || res[j]
			// .getDescription().equals(
			// "! Emergency stop.")))
			// j--;
			//
			// // Note that the actual line having the issue is
			// // sometimes
			// // the previous line, not the line reported. Should
			// // check
			// // the content of the line.
			// if (j >= 0)
			// res[j].setLineNumber(line_num);
			// }
			// }
			// // }
		}

		return res;
	}

	void appendLog(String line) {
		if (logs == null)
			logs = new ArrayList<LogLine>();
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
	public LogLine[] getLogLines(int start) {
		if (logs != null && start < logs.size()) {
			LogLine[] lines = new LogLine[logs.size() - start];
			for (int i = start; i < logs.size(); i++)
				lines[i - start] = logs.get(i);
			return lines;
		}
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
	public int getState() {
		return state;
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
