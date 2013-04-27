package lah.tex.compile;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lah.spectre.Collections;
import lah.spectre.FileName;
import lah.spectre.multitask.TaskState;
import lah.spectre.stream.IBufferProcessor;
import lah.tex.Task;
import lah.tex.exceptions.KpathseaException;
import lah.tex.exceptions.TeXMFFileNotFoundException;

/**
 * Class for an compilation task which encapsulates not only the specific command to be executed but also the result and
 * perform the log analysis.
 * 
 * @author L.A.H.
 * 
 */
public class CompileDocument extends Task implements IBufferProcessor {

	private static final Pattern badbox_pattern = Pattern.compile("(Over|Under)(full \\\\[hv]box .*)");

	/**
	 * Default time out for compilation; set to 600000 miliseconds (i.e. 10 minutes)
	 */
	protected static final int default_compilation_timeout = 600000;

	private static final Pattern error_pattern = Pattern.compile("! (.*)");

	private static final Pattern kpathsea_pattern = Pattern.compile("kpathsea: Running (.+)\\s*");

	@SuppressWarnings("unused")
	private static final Pattern lineNumberPattern = Pattern.compile("(l\\.|line |lines )\\s*(\\d+)[^\\d].*");

	private static final Pattern[] missing_file_patterns = {
			// special cases, design for ease of switch
			Pattern.compile("! OOPS! I can't find any hyphenation patterns for US english."),
			Pattern.compile("! LuaTeX error .*: module '([^']*)' not found"),
			Pattern.compile("! Font \\\\[^=]*=file:([^\\s:]*):.*metric data not found or bad"),
			// the following must be such that invocation of group(1) gives the missing file
			Pattern.compile("! LaTeX Error: File `([^`']*)' not found"),
			Pattern.compile("! I can't find file `([^`']*)'"),
			Pattern.compile("I can't find the format file `([^`']*)'!"),
			Pattern.compile("!pdfTeX error: .+ \\(file (.+)\\)"),
			Pattern.compile("! Package fontenc Error: Encoding file `([^`']*)' not found"),
			Pattern.compile("Could not open config file \"([^\"]*)\"") };

	private static final Pattern single_line_pattern = Pattern.compile("(.+)\n");

	private static final Pattern warning_pattern = Pattern.compile("(((! )?(La|pdf)TeX)|Package) .*Warning.*:(.*)");

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

	protected String[] command;

	protected String default_file_extension = "tex";

	private List<LogLine> logs;

	private StringBuilder nonewline_output_buffer = new StringBuilder(1024);

	private StringBuilder output_buffer = new StringBuilder(1024);

	protected String tex_engine;

	private File tex_src_file;

	protected long timeout;

	/**
	 * Protected constructor for subclasses
	 */
	protected CompileDocument() {
	}

	public CompileDocument(String tex_engine, String tex_src) {
		this.tex_engine = tex_engine;
		this.tex_src_file = new File(tex_src);
		String input_file_no_ext = FileName.removeFileExtension(tex_src_file.getName());
		if (tex_engine.equals("bibtex") || tex_engine.equals("makeindex"))
			this.command = new String[] { tex_engine, input_file_no_ext };
		else if (tex_engine.equals("metapost")) {
			this.command = new String[] { "mpost", "-tex=tex", "-interaction=nonstopmode", tex_src_file.getName() };
		} else {
			String tex_fmt = tex_engine.equals("pdftex") ? "pdfetex" : tex_engine;
			this.command = new String[] { getProgramFromFormat(tex_engine), "-interaction=nonstopmode",
					"-fmt=" + tex_fmt, tex_src_file.getName() };
		}
	}

	public void appendLog(String line) {
		if (line == null)
			return;
		if (logs == null)
			logs = new ArrayList<LogLine>();
		if (warning_pattern.matcher(line).matches())
			logs.add(new LogLine(LogLine.LEVEL_WARNING, line));
		else if (badbox_pattern.matcher(line).matches())
			logs.add(new LogLine(LogLine.LEVEL_WARNING, line));
		else if (error_pattern.matcher(line).matches())
			logs.add(new LogLine(LogLine.LEVEL_ERROR, line));
		else
			logs.add(new LogLine(LogLine.LEVEL_OK, line));
	}

	/**
	 * Make native TeX binaries executable.
	 * 
	 * @return {@literal true} if the method complete successfully; {@literal false} otherwise
	 * @throws Exception
	 */
	private boolean chmodAllEngines() throws Exception {
		File bindir = new File(environment.getTeXMFBinaryDirectory() + "/../../");
		if (bindir.exists() && bindir.isDirectory()) {
			shell.fork(new String[] { environment.getBusyBox(), "chmod", "-R", "700", "." }, bindir);
			return true;
		}
		return true;
	}

	@Override
	public String getDescription() {
		if (tex_src_file != null)
			return tex_engine + " " + tex_src_file.getName();
		else if (command != null)
			return Collections.stringOfArray(command, " ", null, null);
		else
			return null;
	}

	public LogLine getLogLine(int index) {
		if (logs != null && index < logs.size())
			return logs.get(index);
		return null;
	}

	/**
	 * Get the {@link File} links to the output PDF, DVI, ... of the compilation process
	 * 
	 * @return
	 */
	public File getOutputFile() {
		// TODO implement properly in subclasses
		return tex_src_file == null ? null : new File(tex_src_file.getParentFile(), FileName.replaceFileExt(
				tex_src_file.getName(), getOutputType()));
	}

	/**
	 * Get the file type of the expected output file
	 * 
	 * @return Type of output file (if this task completes successfully)
	 */
	public String getOutputType() {
		if (tex_engine == null)
			return null;
		if (tex_engine.equals("pdftex") || tex_engine.equals("pdflatex") || tex_engine.equals("xetex")
				|| tex_engine.equals("xelatex") || tex_engine.equals("luatex") || tex_engine.equals("lualatex"))
			return "pdf";
		else if (tex_engine.equals("tex") || tex_engine.equals("latex"))
			return "dvi";
		else if (tex_engine.equals("bibtex"))
			return "bbl";
		else if (tex_engine.equals("makeindex"))
			return "idx";
		return null;
	}

	@Override
	public boolean isSuccessful() {
		// TODO return false in case if there is TeX error as well
		// Errors are lines with a bank i.e. character !
		return super.isSuccessful();
	}

	/**
	 * Process *TeX, MetaFont and MetaPost standard output
	 */
	@Override
	public void processBuffer(byte[] buffer, int count) throws Exception {
		// Collect the log
		output_buffer.append(new String(buffer, 0, count));
		Matcher matcher = single_line_pattern.matcher(output_buffer);
		Matcher kpsematcher;
		while (matcher.find()) {
			String line = matcher.group(1);
			if ((kpsematcher = kpathsea_pattern.matcher(line)).find())
				throw new KpathseaException(kpsematcher.group(1));
			// System.out.println(line);
			appendLog(line); // Always append the log
			output_buffer.delete(0, matcher.end());
			nonewline_output_buffer.append(line);
		}

		// Check for missing file error
		for (int i = 0; i < missing_file_patterns.length; i++) {
			if ((matcher = missing_file_patterns[i].matcher(nonewline_output_buffer)).find()) {
				switch (i) {
				case 0:
					throw new TeXMFFileNotFoundException("hyphen.tex");
				case 1:
					throw new TeXMFFileNotFoundException(matcher.group(1) + ".lua");
				case 2:
					throw new TeXMFFileNotFoundException(matcher.group(1) + ".tfm");
				default:
					throw new TeXMFFileNotFoundException(matcher.group(1), default_file_extension);
				}
			}
		}
	}

	@Override
	public void reset() {
		super.reset();
		output_buffer.delete(0, output_buffer.length());
		nonewline_output_buffer.delete(0, nonewline_output_buffer.length());
		if (logs != null)
			logs.clear();
	}

	@Override
	public void run() {
		reset();
		setState(TaskState.EXECUTING);
		if (tex_src_file.exists()) {
			try {
				chmodAllEngines();
				// check the existence of the engine
				checkProgram(command[0]);
				// for METAPOST, tex might also be required for labels
				if (command[0].equals("mpost"))
					checkProgram("tex");
				// compile the input file using the engine
				shell.fork(command, tex_src_file.getParentFile(), null, this,
						timeout <= 0 ? default_compilation_timeout : timeout);
				setState(TaskState.COMPLETE);
			} catch (Exception e) {
				setException(e);
				return;
			}
		} else {
			setException(new FileNotFoundException(MessageFormat.format(
					strings.getString("exception_input_file_not_exist"), tex_src_file)));
			return;
		}
	}

	protected void setDefaultFileExtension(String ext) {
		default_file_extension = ext;
	}

}
