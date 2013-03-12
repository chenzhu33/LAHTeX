package lah.tex.compile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lah.spectre.CommandLineArguments;
import lah.spectre.interfaces.IClient;
import lah.spectre.interfaces.IResult;
import lah.spectre.process.TimedShell;
import lah.tex.core.BaseTask;
import lah.tex.core.CompilationCommand;
import lah.tex.core.KpathseaException;
import lah.tex.core.TeXMFFileNotFoundException;
import lah.tex.interfaces.ICompilationCommand;
import lah.tex.interfaces.ICompilationResult;
import lah.tex.interfaces.ICompiler;
import lah.tex.interfaces.IEnvironment;
import lah.tex.interfaces.IInstaller;
import lah.tex.interfaces.ISeeker;

/**
 * This class handles the actual execution of TeX programs, analyzes the
 * standard outputs to give suggestions and to fix issues automatically.
 * 
 * @author L.A.H.
 * 
 */
public class Compiler implements ICompiler {

	/**
	 * Default time out for compilation; set to 600000 milisec (i.e. 10 minutes)
	 */
	private static final int default_compilation_timeout = 600000;

	/**
	 * Pattern for TeX, MetaFont or MetaPost memory dump file
	 */
	static final Pattern format_pattern = Pattern
			.compile("([a-z]*)\\.(fmt|base|mem)");

	/**
	 * Pattern for MetaFont sources
	 */
	private static final Pattern[] mf_font_name_patterns = new Pattern[] {
			Pattern.compile("(ec|tc).*"),
			Pattern.compile("dc.*"),
			Pattern.compile("(cs|lcsss|icscsc|icstt|ilcsss).*"),
			Pattern.compile("(wn[bcdfirstuv]|rx[bcdfiorstuvx][bcfhilmostx]|l[abcdhl][bcdfiorstuvx]).*"),
			Pattern.compile("g[lmorst][bijmtwx][cilnoru].*"),
			Pattern.compile(".*") };

	/**
	 * Pattern for a MetaFont font name
	 */
	private static final Pattern mf_font_rootname_pointsize_pattern = Pattern
			.compile("([a-z]+)([0-9]+)");

	/**
	 * System/installation specific environment
	 */
	private IEnvironment environment;

	/**
	 * Installer to update the environment
	 */
	private IInstaller installer;

	/**
	 * TeX and MetaFont standard output analyzer
	 */
	private final TeXMFOutputAnalyzer output_analyzer;

	/**
	 * Seeker to identify the missing package
	 */
	private ISeeker seeker;

	/**
	 * Shell for execute tex commands
	 */
	private TimedShell shell;

	private String[] tex_extra_environment;

	private final String texmf_var;

	public Compiler(IEnvironment environment, ISeeker seeker,
			IInstaller installer) {
		this.environment = environment;
		this.seeker = seeker;
		this.installer = installer;
		shell = new TimedShell();
		texmf_var = environment.getTeXMFRootDirectory() + "/texmf-var";
		output_analyzer = new TeXMFOutputAnalyzer();
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
	public ICompilationResult compile(IClient<ICompilationResult> client,
			ICompilationCommand cmd, long timeout) {
		output_analyzer.setDefaultFileExtension("tex");
		output_analyzer.setClient(client); // set the client to report to
		CompilationTask result = executeTeXMF(cmd.getCommand(),
				cmd.getDirectory(), timeout <= 0 ? default_compilation_timeout
						: timeout);
		result.setCompilationCommand(cmd); // pass the command to get result
		output_analyzer.setClient(null);
		return result;
	}

	/**
	 * Execute the translated method which corresponds to a mktex(fmt|mf|pk|tfm)
	 * script in Kpathsea package
	 * 
	 * @param command
	 *            The command to execute
	 * @return
	 */
	private IResult executeKpathseaScript(String command) {
		if (command.startsWith("mktexfmt"))
			return makeFMT(command.substring("mktexfmt".length()).trim());
		else if (command.startsWith("mktexpk"))
			return makePK(command);
		else if (command.startsWith("mktextfm"))
			return makeTFM(command.substring("mktextfm".length()).trim());
		else if (command.startsWith("mktexmf"))
			return makeMF(command.substring("mktexmf".length()).trim());
		else
			return null; // should it be a new BaseResult
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
	private synchronized CompilationTask executeTeXMF(String[] cmd, File dir,
			long timeout) {
		CompilationTask result;
		while (true) {
			try {
				cmd[0] = getTeXMFProgram(cmd[0]);
				output_analyzer.reset(); // generate a new result
				shell.fork(cmd, dir, getExtraEnvironment(), output_analyzer,
						timeout);
				result = output_analyzer.getTeXMFResult();
			} catch (Exception e) {
				result = output_analyzer.getTeXMFResult();
				if (result == null)
					result = new CompilationTask(e);
				else
					result.setException(e);
			}

			postProcessResult(result);

			// resolve KpathseaException that does not require installation
			// of new packages
			if (result.hasException()
					&& result.getException() instanceof KpathseaException
					&& ((KpathseaException) result.getException())
							.getMissingPackage() == null) {
				IResult temp_result = executeKpathseaScript(((KpathseaException) result
						.getException()).getCommand());
				if (temp_result != null && temp_result.hasException()) {
					result.setException(temp_result.getException());
					break;
				}
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
	private CompilationTask executeTeXMF(String[] cmd, File dir,
			String default_ext) {
		// Execute the command and restore extension
		final String old_default_ext = output_analyzer.default_file_extension;
		output_analyzer.setDefaultFileExtension(default_ext);
		CompilationTask result = executeTeXMF(cmd, dir,
				default_compilation_timeout);
		output_analyzer.setDefaultFileExtension(old_default_ext);

		// Regenerate path database again for generated files to be found
		if (result != null && !result.hasException()) {
			try {
				installer.makeLSR(null);
			} catch (Exception e) {
				result.setException(e);
			}
		}
		return result;
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

	private String getRealSize(int pointsize) {
		switch (pointsize) {
		case 11:
			return "10.95"; // # \magstephalf
		case 14:
			return "14.4"; // # \magstep2
		case 17:
			return "17.28"; // # \magstep3
		case 20:
			return "20.74"; // # \magstep4
		case 25:
			return "24.88"; // # \magstep5
		case 30:
			return "29.86";// # \magstep6
		case 36:
			return "35.83"; // # \magstep7
		default:
			if (1000 <= pointsize && pointsize <= 99999)
				return Integer.toString(pointsize / 100);
			return Integer.toString(pointsize);
		}
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
			e.identifyMissingPackage(seeker);
			throw e;
		}
	}

	/**
	 * Guess the MetaFont mode from the based device DPI
	 * 
	 * @param bdpi
	 * @return
	 */
	private String guessModeFromBDPI(int bdpi) {
		switch (bdpi) {
		case 85:
			return "sun";
		case 100:
			return "nextscrn";
		case 180:
			return "toshiba";
		case 300:
			return "cx";
		case 360:
			return "epstylus";
		case 400:
			return "nexthi";
		case 600:
			return "ljfour";
		case 720:
			return "epscszz";
		case 1200:
			return "ultre";
		case 1270:
			return "linoone";
		case 8000:
			return "dpdfezzz";
		default:
			return null;
		}
	}

	/**
	 * Make a memory dump file (i.e. a format file)
	 * 
	 * @param format
	 *            Name of a format file (must be of form *.fmt for TeX format,
	 *            *.base for MetaFont format, *.mem for MetaPost format)
	 * @return IResult
	 */
	private IResult makeFMT(String format) {
		Matcher format_matcher = format_pattern.matcher(format);
		if (!format_matcher.find())
			return null;

		String name = format_matcher.group(1);
		String type = format_matcher.group(2);
		String engine;
		String program;
		String default_ext;
		String[] options = null;

		if (type.equals("fmt")) {
			engine = program = CompilationCommand.getProgramFromFormat(format);
			default_ext = "tex";
			if (format.startsWith("pdf") || format.startsWith("xe"))
				options = new String[] { "-etex" };
		} else if (type.equals("base")) {
			engine = "metafont";
			program = default_ext = "mf";
		} else { // type == "mem";
			engine = "metapost";
			program = "mpost";
			default_ext = "mp";
		}

		if (name == null || engine == null)
			return null;

		// Prepare the default language files if they do not exist
		// Regenerate path database to make sure that necessary input
		// files to make memory dumps (*.ini, *.mf, *.tex, ...) are
		// found
		if (!new File(environment.getTeXMFRootDirectory()
				+ "/texmf-var/tex/generic/config/language.dat").exists()
				|| !new File(environment.getTeXMFRootDirectory()
						+ "/texmf-var/tex/generic/config/language.def")
						.exists()) {
			try {
				installer.makeLanguageConfiguration(null);
			} catch (Exception e) {
				return new BaseTask(e);
			}
		}

		// Location of the memory dump file
		File fmt_loc = new File(texmf_var + "/web2c/" + engine);
		if (!fmt_loc.exists())
			fmt_loc.mkdirs();

		// Now create and run the process to generate the format file
		String[] cmd = new String[(options == null ? 0 : options.length) + 4];
		cmd[0] = program;
		cmd[1] = "-ini";
		cmd[2] = "-interaction=nonstopmode";
		if (options != null)
			System.arraycopy(options, 0, cmd, 3, options.length);
		cmd[cmd.length - 1] = name + ".ini"; // the *.ini input file

		return executeTeXMF(cmd, fmt_loc, default_ext);
	}

	private IResult makeMF(String name) {
		Matcher rootname_pointsize_matcher = mf_font_rootname_pointsize_pattern
				.matcher(name);
		String realsize, rootname;
		if (rootname_pointsize_matcher.matches()) {
			rootname = rootname_pointsize_matcher.group(1);
			String ptsizestr = rootname_pointsize_matcher.group(2);
			// System.out.println("Root name = " + rootname);
			// System.out.println("Point size = " + ptsizestr);
			if (ptsizestr.isEmpty())
				return new BaseTask(new Exception(
						"Invalid point size input for mktexmf"));
			else
				realsize = getRealSize(Integer.parseInt(ptsizestr));
		} else
			// Invalid name pattern
			return new BaseTask(new Exception(
					"Invalid font name pattern in mktexmf"));

		// The content of MF source for the font name matching the pattern
		String[] mf_content = new String[] {
				"if unknown exbase: input exbase fi;" + "gensize:=" + realsize
						+ ";" + "generate " + rootname + ";",
				"if unknown dxbase: input dxbase fi;" + "gensize:=" + realsize
						+ ";" + "generate " + rootname + ";",
				"input cscode; use_driver;", "input fikparm;",
				"input cbgreek;",
				"design_size := " + realsize + ";" + "input " + rootname + ";" };

		// Find the pattern that matches the font name
		int match = 0;
		for (; match < mf_font_name_patterns.length; match++) {
			if (mf_font_name_patterns[match].matcher(name).matches())
				break;
		}

		// Generate the MF file
		try {
			String mf_font_directory = texmf_var + "/fonts/source/";
			new File(mf_font_directory).mkdirs();
			FileWriter mf_output = new FileWriter(mf_font_directory + name
					+ ".mf");
			mf_output.write(mf_content[match]);
			mf_output.close();
			installer.makeLSR(null);
			return null;
		} catch (Exception e) {
			return new BaseTask(e);
		}
	}

	/**
	 * Make PK font using arguments from a command line
	 * 
	 * @param cmd
	 * @return
	 * @throws Exception
	 */
	private IResult makePK(String cmd) {
		String[] args = cmd.split("\\s+");
		Map<String, String> arg_map = CommandLineArguments
				.parseCommandLineArguments(args);
		String name = arg_map.get("$ARG");
		String dpi_str = arg_map.get("--dpi");
		int dpi = (dpi_str != null ? Integer.parseInt(dpi_str) : 600);
		String bdpi_str = arg_map.get("--bdpi");
		int bdpi = (bdpi_str != null ? Integer.parseInt(bdpi_str) : 600);
		String mag = arg_map.get("--mag");
		String mfmode = arg_map.get("--mfmode");

		if (mfmode == null || mfmode.equals("/"))
			mfmode = guessModeFromBDPI(bdpi);
		String arg = "\\mode:=" + mfmode + "; \\mag:=" + mag
				+ "; nonstopmode; input " + name;
		String gf_name = name + "." + dpi + "gf";
		String pk_name = name + "." + dpi + "pk";
		File pk_loc = new File(texmf_var + "/fonts/pk/" + mfmode + "/tmp/dpi"
				+ dpi);
		pk_loc.mkdirs();
		IResult mfresult = executeTeXMF(new String[] { "mf", arg }, pk_loc,
				"mf");
		if (mfresult != null && mfresult.hasException())
			return mfresult;
		else
			return executeTeXMF(new String[] { "gftopk", gf_name, pk_name },
					pk_loc, null);
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

	/**
	 * Generate the necessary TeX Font Metric (TFM) files
	 */
	private CompilationTask makeTFM(String name) {
		int mag = 1;
		String mfmode = "ljfour";
		String arg = "\\mode:=" + mfmode + "; \\mag:=" + mag
				+ "; nonstopmode; input " + name;
		File tfm_loc = new File(texmf_var + "/fonts/tfm/");
		tfm_loc.mkdirs();
		return executeTeXMF(new String[] { "mf", arg }, tfm_loc, "mf");
	}

	/**
	 * Identify the missing package from the exception (if any) raised in a
	 * result
	 * 
	 * @param result
	 */
	private void postProcessResult(IResult result) {
		if (result != null && result.hasException()
				&& result.getException() instanceof TeXMFFileNotFoundException) {
			try {
				((TeXMFFileNotFoundException) result.getException())
						.identifyMissingPackage(seeker);
			} catch (Exception e) {
				result.setException(e);
			}
		}
	}

}
