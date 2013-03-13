package lah.tex.task;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lah.spectre.FileName;
import lah.spectre.stream.IBufferProcessor;
import lah.tex.exceptions.KpathseaException;
import lah.tex.exceptions.TeXMFFileNotFoundException;
import lah.tex.interfaces.ICompilationCommand;
import lah.tex.interfaces.ICompilationResult;

/**
 * Class for an compilation task which encapsulates not only the specific
 * command to be executed but also the result and perform the log analysis.
 * 
 * @author L.A.H.
 * 
 */
public class CompilationTask extends BaseTask implements ICompilationResult,
		IBufferProcessor, ICompilationCommand {

	static final Pattern badboxPattern = Pattern
			.compile("(Over|Under)(full \\\\[hv]box .*)");

	static final Pattern errorPattern = Pattern.compile("! (.*)");

	static final Pattern lineNumberPattern = Pattern
			.compile("(l\\.|line |lines )\\s*(\\d+)[^\\d].*");

	@SuppressWarnings("unused")
	static private Pattern[] other_issue_patterns = {
			Pattern.compile("! (.*)"),
			Pattern.compile("(l\\.|line |lines )\\s*(\\d+)[^\\d].*"),
			Pattern.compile("(((! )?(La|pdf)TeX)|Package) .*Warning.*:(.*)"),
			Pattern.compile("(Over|Under)(full \\\\[hv]box .*)") };

	static final Pattern warningPattern = Pattern
			.compile("(((! )?(La|pdf)TeX)|Package) .*Warning.*:(.*)");

	public static String getProgramFromFormat(String format) {
		if (format.startsWith("pdf"))
			return "pdftex";
		else if (format.startsWith("xe"))
			return "xetex";
		else if (format.startsWith("lua"))
			return "luatex";
		else
			return "tex";
	}

	private StringBuilder accumulated_error_message;

	// private IClient<ICompilationResult> client;

	final Matcher badboxMatcher = badboxPattern.matcher("");

	private ICompilationCommand compilation_command;

	String default_file_extension = "tex";

	private File directory;

	private String engine;

	final Matcher errorMatcher = errorPattern.matcher("");

	private int exit_value;

	private File input_file;

	private String input_file_no_ext;

	private final Matcher kpathsea_matcher = Pattern.compile(
			"kpathsea: Running (.+)\\s*").matcher("");

	final Matcher lineNumberMatcher = lineNumberPattern.matcher("");

	private List<LogLine> logs;

	private StringBuilder output_buffer = new StringBuilder();

	private boolean pdftex_error = false;

	private final Matcher pdftex_error_end = Pattern.compile(
			" ==> Fatal error occurred, no output PDF file produced!").matcher(
			"");

	private final Matcher pdftex_error_start = Pattern.compile(
			"!pdfTeX error: .+").matcher("");

	private final Matcher pdftex_missing_file_matcher = Pattern.compile(
			"!pdfTeX error: .+ \\(file (.+)\\): .+").matcher("");

	private final Matcher single_line_matcher = Pattern.compile("(.+)\n")
			.matcher("");

	private int state;

	private String tex_engine, tex_src;

	// for XeTeX
	private final Matcher[] tex_missing_file_matchers = {
			Pattern.compile("! LaTeX Error: File `([^`']*)' not found.*")
					.matcher(""),
			Pattern.compile("! I can't find file `([^`']*)'.*").matcher(""),
			Pattern.compile(
					"! Package fontenc Error: Encoding file `([^`']*)' not found.")
					.matcher(""),
			Pattern.compile(
					"Could not open config file \"(dvipdfmx\\.cfg)\"\\.")
					.matcher(""),
			Pattern.compile(
					"! OOPS! I can't find any hyphenation patterns for US english.")
					.matcher(""), };

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

	// Pattern for missing fonts, probably not necessary
	// Pattern.compile("! Font \\\\[^=]*=([^\\s]*)\\s"),
	// Pattern.compile("! Font [^\\n]*file\\:([^\\:\\n]*)\\:"),
	// Pattern.compile("! Font \\\\[^/]*/([^/]*)/")

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
	public String[] getCommand() {
		input_file_no_ext = FileName.removeFileExtension(input_file.getName());
		if (engine.equals("bibtex") || engine.equals("makeindex"))
			return new String[] { engine, input_file_no_ext };
		else {
			String tex_fmt = engine.equals("pdftex") ? "pdfetex" : engine;
			return new String[] { getProgramFromFormat(engine),
					"-interaction=nonstopmode", "-fmt=" + tex_fmt,
					input_file.getName() };
		}
	}

	@Override
	public ICompilationCommand getCompilationCommand() {
		return compilation_command;
	}

	@Override
	public CharSequence getDescription() {
		return tex_engine + " " + tex_src;
	}

	@Override
	public File getDirectory() {
		return directory;
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
	public String getInputFileWithoutExt() {
		return input_file_no_ext;
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
	public String getOutputType() {
		if (engine.equals("pdftex") || engine.equals("pdflatex")
				|| engine.equals("xetex") || engine.equals("xelatex")
				|| engine.equals("luatex") || engine.equals("lualatex"))
			return "pdf";
		else if (engine.equals("tex") || engine.equals("latex"))
			return "dvi";
		else if (engine.equals("bibtex"))
			return "bbl";
		else if (engine.equals("makeindex"))
			return "idx";
		return null;
	}

	@Override
	public boolean isComplete() {
		return state == STATE_COMPLETE;
	}

	@Override
	public void processBuffer(byte[] buffer, int count) throws Exception {
		// reset if there is no result available
		// if (texmf_result == null)
		// reset();
		output_buffer.append(new String(buffer, 0, count));
		single_line_matcher.reset(output_buffer);
		String line;
		while (single_line_matcher.find()) {
			line = single_line_matcher.group(1);

			// Always append the log
			// texmf_result.appendLog(line);
			output_buffer.delete(0, single_line_matcher.end());

			// Get pdfTeX error: go to accumulation state
			if (pdftex_error_start.reset(line).matches()) {
				pdftex_error = true;
				accumulated_error_message = new StringBuilder(line);
			}
			if (pdftex_error) {
				// End of pdfTeX error: identify the missing file
				if (pdftex_error_end.reset(line).matches()) {
					pdftex_error = false;
					// now we parse the error for missing file
					if (pdftex_missing_file_matcher.reset(
							accumulated_error_message).matches()) {
						throw new TeXMFFileNotFoundException(
								pdftex_missing_file_matcher.group(1),
								default_file_extension);
					}
				} else {
					// continue accumulating the error
					accumulated_error_message.append(line);
					continue;
				}
			}

			// Get Kpathsea error if any
			if (kpathsea_matcher.reset(line).matches())
				throw new KpathseaException(kpathsea_matcher.group(1));

			// Looking for missing TeX|MF files
			for (int i = 0; i < tex_missing_file_matchers.length; i++) {
				if (tex_missing_file_matchers[i].reset(line).find()) {
					if (i == tex_missing_file_matchers.length - 1)
						throw new TeXMFFileNotFoundException("hyphen.tex",
								default_file_extension);
					else
						throw new TeXMFFileNotFoundException(
								tex_missing_file_matchers[i].group(1),
								default_file_extension);
				}
			}
		}
	}

	public void reset() {
		output_buffer.delete(0, output_buffer.length());
		setState(ICompilationResult.STATE_INIT);
	}

	@Override
	public void run() {
		status = "Executing";
		File tex_src_file = new File(tex_src);
		if (tex_src_file.exists()) {
			// compile the input file using the engine
			System.out.println(tex_engine + " " + tex_src_file.getName());
			status = "Complete";
		} else {
			setException(new FileNotFoundException("ERROR: " + tex_src
					+ " does not exist!"));
		}
	}

	public void setCompilationCommand(ICompilationCommand cmd) {
		compilation_command = cmd;
	}

	void setDefaultFileExtension(String ext) {
		default_file_extension = ext;
	}

	@Override
	public void setException(Exception e) {
		super.setException(e);
		setState(STATE_COMPLETE);
	}

	public void setState(int s) {
		state = s;
	}

}
