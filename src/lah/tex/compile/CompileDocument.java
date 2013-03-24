package lah.tex.compile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lah.spectre.Collections;
import lah.spectre.FileName;
import lah.spectre.stream.IBufferProcessor;
import lah.tex.Task;
import lah.tex.exceptions.KpathseaException;
import lah.tex.exceptions.TeXMFFileNotFoundException;
import lah.tex.interfaces.ICompilationResult;

/**
 * Class for an compilation task which encapsulates not only the specific
 * command to be executed but also the result and perform the log analysis.
 * 
 * @author L.A.H.
 * 
 */
public class CompileDocument extends Task implements ICompilationResult,
		IBufferProcessor {

	// Pattern for missing fonts, probably not necessary
	// Pattern.compile("! Font \\\\[^=]*=([^\\s]*)\\s"),
	// Pattern.compile("! Font [^\\n]*file\\:([^\\:\\n]*)\\:"),
	// Pattern.compile("! Font \\\\[^/]*/([^/]*)/")

	/**
	 * Standard output patterns
	 */
	private static final Pattern badboxPattern = Pattern
			.compile("(Over|Under)(full \\\\[hv]box .*)"),
			errorPattern = Pattern.compile("! (.*)"),
			lineNumberPattern = Pattern
					.compile("(l\\.|line |lines )\\s*(\\d+)[^\\d].*"),
			warningPattern = Pattern
					.compile("(((! )?(La|pdf)TeX)|Package) .*Warning.*:(.*)");

	/**
	 * Default time out for compilation; set to 600000 milisec (i.e. 10 minutes)
	 */
	protected static final int default_compilation_timeout = 600000;

	// static private Pattern[] other_issue_patterns = {
	// Pattern.compile("! (.*)"),
	// Pattern.compile("(l\\.|line |lines )\\s*(\\d+)[^\\d].*"),
	// Pattern.compile("(((! )?(La|pdf)TeX)|Package) .*Warning.*:(.*)"),
	// Pattern.compile("(Over|Under)(full \\\\[hv]box .*)") };

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

	final Matcher badboxMatcher = badboxPattern.matcher(""),
			errorMatcher = errorPattern.matcher(""),
			lineNumberMatcher = lineNumberPattern.matcher(""),
			warningMatcher = warningPattern.matcher("");

	protected String[] command;

	protected String default_file_extension = "tex";

	private final Matcher kpathsea_matcher = Pattern.compile(
			"kpathsea: Running (.+)\\s*").matcher("");

	private List<LogLine> logs;

	private StringBuilder output_buffer = new StringBuilder();

	private boolean pdftex_error = false;

	private final Matcher pdftex_error_end = Pattern.compile(
			" ==> Fatal error occurred, no output PDF file produced!").matcher(
			""), pdftex_error_start = Pattern.compile("!pdfTeX error: .+")
			.matcher(""), pdftex_missing_file_matcher = Pattern.compile(
			"!pdfTeX error: .+ \\(file (.+)\\): .+").matcher("");

	private final Matcher single_line_matcher = Pattern.compile("(.+)\n")
			.matcher("");

	protected String tex_engine;

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
		String input_file_no_ext = FileName.removeFileExtension(tex_src_file
				.getName());
		if (tex_engine.equals("bibtex") || tex_engine.equals("makeindex"))
			this.command = new String[] { tex_engine, input_file_no_ext };
		else {
			String tex_fmt = tex_engine.equals("pdftex") ? "pdfetex"
					: tex_engine;
			this.command = new String[] { getProgramFromFormat(tex_engine),
					"-interaction=nonstopmode", "-fmt=" + tex_fmt,
					tex_src_file.getName() };
		}
	}

	public void appendLog(String line) {
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

	/**
	 * Make native TeX binaries executable.
	 * 
	 * @return {@literal true} if the method complete successfully;
	 *         {@literal false} otherwise
	 * @throws Exception
	 */
	private boolean chmodAllEngines() throws Exception {
		File bindir = new File(environment.getTeXMFBinaryDirectory()
				+ "/../../");
		if (bindir.exists() && bindir.isDirectory()) {
			shell.fork(
					new String[] { environment.getCHMOD(), "-R", "700", "." },
					bindir);
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
		// ICompilationCommand cmd;
		// if ((cmd = getCompilationCommand()) != null)
		// return new File(cmd.getDirectory() + "/"
		// + cmd.getInputFileWithoutExt() + "." + getOutputFileType());
		return null;
	}

	public String getOutputType() {
		if (tex_engine.equals("pdftex") || tex_engine.equals("pdflatex")
				|| tex_engine.equals("xetex") || tex_engine.equals("xelatex")
				|| tex_engine.equals("luatex") || tex_engine.equals("lualatex"))
			return "pdf";
		else if (tex_engine.equals("tex") || tex_engine.equals("latex"))
			return "dvi";
		else if (tex_engine.equals("bibtex"))
			return "bbl";
		else if (tex_engine.equals("makeindex"))
			return "idx";
		return null;
	}

	/**
	 * Generate configuration file texmf.cnf in the TeX binary directory
	 * reflecting the installation
	 * 
	 * @throws Exception
	 */
	public void makeTEXMFCNF() throws Exception {
		File texmfcnf_file = new File(environment.getTeXMFBinaryDirectory()
				+ "/texmf.cnf");
		if (!texmfcnf_file.exists()) {
			File texmfcnf_src = new File(environment.getTeXMFRootDirectory()
					+ "/texmf/web2c/texmf.cnf");
			if (!texmfcnf_src.exists()) {
				throw new TeXMFFileNotFoundException("texmf.cnf", null);
			}
			BufferedReader reader = new BufferedReader(new FileReader(
					texmfcnf_src));
			FileWriter writer = new FileWriter(texmfcnf_file);
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.startsWith("TEXMFROOT"))
					writer.write("TEXMFROOT = "
							+ environment.getTeXMFRootDirectory() + "\n");
				else if (line.startsWith("TEXMFVAR")
						&& environment.isPortable())
					writer.write("TEXMFVAR = $TEXMFSYSVAR\n");
				else
					writer.write(line + "\n");
			}
			reader.close();
			writer.close();
		}
	}

	@Override
	public void processBuffer(byte[] buffer, int count) throws Exception {
		// reset if there is no result available
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

	@Override
	public void reset() {
		super.reset();
		output_buffer.delete(0, output_buffer.length());
	}

	@Override
	public void run() {
		setState(State.STATE_EXECUTING);
		reset();
		if (tex_src_file.exists()) {
			try {
				chmodAllEngines();
				File program_file = new File(
						environment.getTeXMFBinaryDirectory() + "/"
								+ command[0]);
				if (program_file.exists()) {
					makeTEXMFCNF();
				} else {
					throw new TeXMFFileNotFoundException(
							program_file.getName(), null);
				}
				// compile the input file using the engine
				shell.fork(command, tex_src_file.getParentFile(), null, this,
						timeout <= 0 ? default_compilation_timeout : timeout);
				setState(State.STATE_COMPLETE);
			} catch (Exception e) {
				setException(e);
			} finally {
				setState(State.STATE_COMPLETE);
			}
		} else {
			setException(new FileNotFoundException(tex_src_file
					+ " does not exist!"));
			setState(State.STATE_COMPLETE);
		}
	}

	protected void setDefaultFileExtension(String ext) {
		default_file_extension = ext;
	}

}
