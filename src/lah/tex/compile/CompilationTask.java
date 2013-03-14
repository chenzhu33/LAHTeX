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

import lah.spectre.FileName;
import lah.spectre.interfaces.IClient;
import lah.spectre.process.TimedShell;
import lah.spectre.stream.IBufferProcessor;
import lah.tex.Task;
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
public class CompilationTask extends Task implements ICompilationResult,
		IBufferProcessor, ICompilationCommand {

	static final Pattern badboxPattern = Pattern
			.compile("(Over|Under)(full \\\\[hv]box .*)");

	/**
	 * Default time out for compilation; set to 600000 milisec (i.e. 10 minutes)
	 */
	private static final int default_compilation_timeout = 600000;

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

	// public Compiler(IEnvironment environment, ISeeker seeker,
	// IInstaller installer) {
	// this.environment = environment;
	// this.seeker = seeker;
	// this.installer = installer;
	// shell = new TimedShell();
	// texmf_var = environment.getTeXMFRootDirectory() + "/texmf-var";
	// }

	private StringBuilder accumulated_error_message;

	final Matcher badboxMatcher = badboxPattern.matcher("");

	/**
	 * Execute the translated method which corresponds to a mktex(fmt|mf|pk|tfm)
	 * script in Kpathsea package
	 * 
	 * @param command
	 *            The command to execute
	 * @return
	 */
	// private IResult executeKpathseaScript(String command) {
	// if (command.startsWith("mktexfmt"))
	// return makeFMT(command.substring("mktexfmt".length()).trim());
	// else if (command.startsWith("mktexpk"))
	// return makePK(command);
	// else if (command.startsWith("mktextfm"))
	// return makeTFM(command.substring("mktextfm".length()).trim());
	// else if (command.startsWith("mktexmf"))
	// return makeMF(command.substring("mktexmf".length()).trim());
	// else
	// return null; // should it be a new BaseResult
	// }

	private ICompilationCommand compilation_command;

	String default_file_extension = "tex";

	private File directory;

	private String engine;

	final Matcher errorMatcher = errorPattern.matcher("");

	private int exit_value;

	private File input_file;

	private String input_file_no_ext;

	/**
	 * Installer to update the environment
	 */
	// private IInstaller installer;

	private final Matcher kpathsea_matcher = Pattern.compile(
			"kpathsea: Running (.+)\\s*").matcher("");

	final Matcher lineNumberMatcher = lineNumberPattern.matcher("");

	// private IClient<ICompilationResult> client;

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

	private String[] tex_extra_environment;

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

	private String texmf_var;

	final Matcher warningMatcher = warningPattern.matcher("");

	public CompilationTask() {
		super();
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

	public ICompilationResult compile(IClient<ICompilationResult> client,
			ICompilationCommand cmd, long timeout) {
		// output_analyzer.setDefaultFileExtension("tex");
		// output_analyzer.setClient(client); // set the client to report to
		CompilationTask result = executeTeXMF(cmd.getCommand(),
				cmd.getDirectory(), timeout <= 0 ? default_compilation_timeout
						: timeout);
		result.setCompilationCommand(cmd); // pass the command to get result
		// output_analyzer.setClient(null);
		return result;
	}

	/**
	 * Execute TeX or MF or a distributed command, resolve Kpathsea problems and
	 * retry the operation as necessary
	 * 
	 * @param cmd
	 *            The command to execute
	 * @param dir
	 *            The directory to execute command at
	 * @param timeout
	 *            Time allowance for command execution
	 * @return
	 */
	@SuppressWarnings("null")
	private synchronized CompilationTask executeTeXMF(String[] cmd, File dir,
			long timeout) {
		CompilationTask result = null;
		while (true) {
			try {
				cmd[0] = getTeXMFProgram(cmd[0]);
				// output_analyzer.reset(); // generate a new result
				shell.fork(cmd, dir, getExtraEnvironment(), null, // output_analyzer,
						timeout);
				// result = output_analyzer.getTeXMFResult();
			} catch (Exception e) {
				// result = output_analyzer.getTeXMFResult();
				// if (result == null)
				// result = new CompilationTask(e);
				// else
				// result.setException(e);
			}

			// postProcessResult(result);

			// resolve KpathseaException that does not require installation
			// of new packages
			if (result.hasException()
					&& result.getException() instanceof KpathseaException
					&& ((KpathseaException) result.getException())
							.getMissingPackage() == null) {
				// IResult temp_result =
				// executeKpathseaScript(((KpathseaException) result
				// .getException()).getCommand());
				// if (temp_result != null && temp_result.hasException()) {
				// result.setException(temp_result.getException());
				// break;
				// }
			} else {
				result.setState(ICompilationResult.STATE_COMPLETE);
				break;
			}
		}
		return result;
	}

	/**
	 * Execute internal commands to generate formats, fonts, ...
	 * 
	 * @param cmd
	 * @param dir
	 * @param timeout
	 * @return
	 */
	protected CompilationTask executeTeXMF(String[] cmd, File dir,
			String default_ext) {
		// Execute the command and restore extension
		// final String old_default_ext =
		// output_analyzer.default_file_extension;
		// output_analyzer.setDefaultFileExtension(default_ext);
		CompilationTask result = executeTeXMF(cmd, dir,
				default_compilation_timeout);
		// output_analyzer.setDefaultFileExtension(old_default_ext);

		// Regenerate path database again for generated files to be found
		if (result != null && !result.hasException()) {
			// try {
			// installer.makeLSR(null);
			// } catch (Exception e) {
			// result.setException(e);
			// }
		}
		return result;
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

	// Pattern for missing fonts, probably not necessary
	// Pattern.compile("! Font \\\\[^=]*=([^\\s]*)\\s"),
	// Pattern.compile("! Font [^\\n]*file\\:([^\\:\\n]*)\\:"),
	// Pattern.compile("! Font \\\\[^/]*/([^/]*)/")

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

	/**
	 * Get the array containing necessary extra environment variables such as
	 * PATH, TMPDIR, FONTCONFIG (for XeTeX to work)
	 * 
	 * @return The array containing environment variables ready for used with
	 *         {@link TimedShell#fork}.
	 */
	private String[] getExtraEnvironment() {
		if (tex_extra_environment == null) {
			String path = environment.getTeXMFBinaryDirectory() + ":"
					+ System.getenv("PATH");
			String tmpdir = texmf_var + "/tmp";
			String fontconfig_path = texmf_var + "/fonts/conf";
			new File(tmpdir + "/").mkdirs();
			tex_extra_environment = new String[] { "PATH", path, "TMPDIR",
					tmpdir, "FONTCONFIG_PATH", fontconfig_path, "OSFONTDIR",
					environment.getOSFontsDir() };
		}
		return tex_extra_environment;
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

	/**
	 * Get the absolute path to a TeX program, calling the object that resolve
	 * the case when the binary is missing whenever necessary.
	 * 
	 * @param program
	 * @return full path to the program or {@literal null} if the program is not
	 *         installed and the listener cannot resolve it (for e.g., by
	 *         installing the containing package)
	 * @throws Exception
	 */
	String getTeXMFProgram(String program) throws Exception {
		chmodAllEngines();
		File program_file = program.startsWith("/") ? new File(program)
				: new File(environment.getTeXMFBinaryDirectory() + "/"
						+ program);
		if (program_file.exists()) {
			makeTEXMFCNF();
			return program_file.getAbsolutePath();
		} else {
			TeXMFFileNotFoundException e = new TeXMFFileNotFoundException(
					program_file.getName(), null);
			//e.identifyMissingPackage(seeker);
			throw e;
		}
	}

	@Override
	public boolean isComplete() {
		return state == STATE_COMPLETE;
	}

	/**
	 * Generate configuration file texmf.cnf in the TeX binary directory
	 * reflecting the installation
	 * 
	 * @throws Exception
	 */
	private void makeTEXMFCNF() throws Exception {
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
