package lah.tex.compile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lah.spectre.CommandLineArguments;
import lah.spectre.interfaces.IClient;
import lah.spectre.interfaces.IResult;
import lah.spectre.process.TimedShell;
import lah.spectre.stream.StreamRedirector;
import lah.spectre.stream.Streams;
import lah.tex.core.BaseResult;
import lah.tex.core.KpathseaException;
import lah.tex.core.TeXMFFileNotFoundException;
import lah.tex.interfaces.ICompilationCommand;
import lah.tex.interfaces.ICompilationResult;
import lah.tex.interfaces.ICompiler;
import lah.tex.interfaces.IEnvironment;
import lah.tex.interfaces.ISeeker;

public class Compiler implements ICompiler {

	/**
	 * Time out for compilation; set to 600000 milisec (i.e. 10 minutes)
	 */
	private static final int compilation_timeout = 600000;

	private static final String lsR_magic = "% ls-R -- filename database for kpathsea; do not change this line.\n";

	private static final int make_fmt_timeout = 120000;

	private static final int make_font_tfm_timeout = 120000;

	private static final int make_pk_font_timeout = make_font_tfm_timeout;

	private IEnvironment environment;

	private final TeXMFOutputAnalyzer output_analyzer;

	private ISeeker seeker;

	private TimedShell shell;

	private final String texmf_language_config;

	private final String texmf_var;

	public Compiler(IEnvironment environment, ISeeker seeker) {
		this.environment = environment;
		this.seeker = seeker;
		shell = new TimedShell();
		texmf_var = environment.getTeXMFRootDirectory() + "/texmf-var";
		texmf_language_config = texmf_var + "/tex/generic/config";
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
		String[] command = cmd.getCommand();
		output_analyzer.setDefaultFileExtension("tex");
		TeXMFResult result = executeTeXMF(client, command, cmd.getDirectory(),
				timeout == 0 ? compilation_timeout : timeout, true, false);
		result.setCompilationCommand(cmd);
		return result;
	}

	private synchronized TeXMFResult executeTeXMF(
			IClient<ICompilationResult> client, String[] cmd, File dir,
			long timeout, boolean mklsr_pre, boolean mklsr_post) {
		TeXMFResult result;
		while (true) {
			try {
				if (mklsr_pre)
					makeLSR();
				cmd[0] = getTeXMFProgram(cmd[0]);
				output_analyzer.setClient(client);
				shell.fork(cmd, dir, output_analyzer, timeout);
				result = output_analyzer.getTeXMFResult();
				if (!result.hasException() && mklsr_post) {
					// Regenerate path database again for generated files dumps
					// to be found; this might be inefficient but it is safe ;
					// 'ls -R' is cheap anyway
					makeLSR();
				}
			} catch (Exception e) {
				result = output_analyzer.getTeXMFResult();
				if (result == null)
					result = new TeXMFResult(e);
				else
					result.setException(e);
			}

			postProcessResult(result);

			// resolve KpathseaException that does not require install new
			// package
			if (result.hasException()
					&& result.getException() instanceof KpathseaException
					&& ((KpathseaException) result.getException())
							.getMissingPackage() == null) {
				IResult temp_result = runKpathsea(((KpathseaException) result
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

	private TeXMFResult executeTeXMF(String[] cmd, File dir, long timeout) {
		return executeTeXMF(null, cmd, dir, timeout, true, true);
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
			makeKpathseaTEXMFCNF();
			return program_file.getAbsolutePath();
		} else {
			TeXMFFileNotFoundException exc = new TeXMFFileNotFoundException(
					program_file.getName(), null);
			exc.identifyMissingPackage(seeker);
			throw exc;
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
	 * Make a format file
	 * 
	 * @param output_analyzer
	 * 
	 * @param tex_fmt
	 * @return
	 * @throws Exception
	 */
	private IResult makeFMT(String format, TeXMFOutputAnalyzer output_analyzer) {
		String name = null;
		String engine = null;
		String program;
		String default_ext = null;
		String[] options = null;
		try {
			if (format.endsWith(".fmt")) {
				name = format.substring(0, format.length() - 4);
				engine = (format.startsWith("pdf") ? "pdftex" : "tex");
				program = engine;
				default_ext = "tex";
				options = (format.startsWith("pdf") ? new String[] { "-etex" }
						: null);
			} else if (format.endsWith(".base")) {
				name = format.substring(0, format.length() - 5);
				engine = "metafont";
				program = "mf";
				default_ext = "mf";
				options = null;
			} else {
				program = "mpost";
			}

			if (name != null && engine != null) {
				// Prepare the language files if they do not exist
				// Regenerate path database to make sure that necessary input
				// files
				// to make memory dumps (*.ini, *.mf, *.tex, ...) are found
				makeLanguageConfigs();
				// makeLSR();

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

				output_analyzer.setDefaultFileExtension(default_ext);
				return executeTeXMF(cmd, fmt_loc, make_fmt_timeout);
			}
			return null;
		} catch (Exception e) {
			// e.printStackTrace(System.out);
			return new BaseResult(e);
		}
	}

	private void makeKpathseaTEXMFCNF() throws Exception {
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
				else
					writer.write(line + "\n");
			}
			reader.close();
			writer.close();
		}
	}

	/**
	 * Write out language configurations
	 * 
	 * Writing language.dat to /texmf-var/tex/generic/config/language.dat
	 * Writing language.def to /texmf-var/tex/generic/config/language.def
	 * writing language.dat.lua to
	 * /texmf-var/tex/generic/config/language.dat.lua
	 * 
	 * @throws Exception
	 */
	private void makeLanguageConfigs() throws Exception {

		// language_configs[i] is a String array whose first element is the name
		// of the configuration file and the remaining is its content to write
		// to.
		String[][] language_configs = {
				{
						"language.dat",
						"english		hyphen.tex  % do not change!",
						"=usenglish",
						"=USenglish",
						"=american",
						"dumylang	dumyhyph.tex    %for testing a new language.",
						"nohyphenation	zerohyph.tex    %a language with no patterns at all." },
				{
						"language.def",
						"%% e-TeX V2.0;2",
						"\\addlanguage {USenglish}{hyphen}{}{2}{3} %%% This MUST be the first non-comment line of the file",
						"\\uselanguage {USenglish}             %%% This MUST be the last line of the file." }

		};
		new File(texmf_language_config).mkdirs();
		boolean is_modified = false;
		for (int i = 0; i < language_configs.length; i++) {
			if (!new File(texmf_language_config + "/" + language_configs[i][0])
					.exists()) {
				FileWriter fwr = new FileWriter(new File(texmf_language_config
						+ "/" + language_configs[i][0]));
				for (int j = 1; j < language_configs[i].length; j++)
					fwr.write(language_configs[i][j] + "\n");
				fwr.close();
				is_modified = true;
			}
		}
		if (is_modified)
			makeLSR();
	}

	/**
	 * Generate the path database files (ls-R) in all TeX directory trees
	 * (texmf*)
	 * 
	 * @throws Exception
	 */
	private void makeLSR() throws Exception {
		// The directories under tex_root to generate ls-R are:
		final String[] texmfdir_names = { "texmf", "texmf-dist", "texmf-var",
				"texmf-config" };

		for (int i = 0; i < texmfdir_names.length; i++) {
			File texmf_dir = new File(environment.getTeXMFRootDirectory() + "/"
					+ texmfdir_names[i]);

			// Skip non-existing texmf directory, is not a directory or
			// cannot
			// read/write/execute: skip it
			if (!texmf_dir.exists()
					|| (!(texmf_dir.isDirectory() && texmf_dir.canRead()
							&& texmf_dir.canWrite() && texmf_dir.canExecute())))
				continue;

			// Delete the ls-R before doing path generation
			File lsRfile = new File(texmf_dir + "/ls-R");
			if (lsRfile.exists() && lsRfile.isFile()) {
				// System.out.println("Delete " + lsRfile.getAbsolutePath());
				lsRfile.delete();
			}

			// Create a temporary ls-R file
			File temp_lsRfile = new File(environment.getTeXMFRootDirectory()
					+ "/ls-R");
			Streams.writeStringToFile(lsR_magic, temp_lsRfile, false);
			// Now do the "ls -R . >> ls-R" in the texmf root directory
			final FileOutputStream stream = new FileOutputStream(temp_lsRfile,
					true);
			shell.fork(new String[] { environment.getLS(), "-R", "." },
					texmf_dir, new StreamRedirector(stream), 600000);
			stream.close();
			// Move the temporary file to the intended location
			temp_lsRfile.renameTo(lsRfile);
		}
	}

	private IResult makeMF(String name) {
		Pattern rootname_pointsize_pattern = Pattern
				.compile("([a-z]+)([0-9]+)");
		Matcher rootname_pointsize_matcher = rootname_pointsize_pattern
				.matcher(name);
		String realsize, rootname;
		if (rootname_pointsize_matcher.matches()) {
			rootname = rootname_pointsize_matcher.group(1);
			String ptsizestr = rootname_pointsize_matcher.group(2);
			// System.out.println("Root name = " + rootname);
			// System.out.println("Point size = " + ptsizestr);
			if (ptsizestr.isEmpty())
				return new BaseResult(new Exception(
						"Invalid point size input for mktexmf"));
			else
				realsize = getRealSize(Integer.parseInt(ptsizestr));
		} else
			// Invalid name pattern
			return new BaseResult(new Exception(
					"Invalid font name pattern in mktexmf"));
		Pattern[] name_patterns = new Pattern[] {
				Pattern.compile("(ec|tc).*"),
				Pattern.compile("dc.*"),
				Pattern.compile("(cs|lcsss|icscsc|icstt|ilcsss).*"),
				Pattern.compile("(wn[bcdfirstuv]|rx[bcdfiorstuvx][bcfhilmostx]|l[abcdhl][bcdfiorstuvx]).*"),
				Pattern.compile("g[lmorst][bijmtwx][cilnoru].*"),
				Pattern.compile(".*") };
		String[] mf_content = new String[] {
				"if unknown exbase: input exbase fi;" + "gensize:=" + realsize
						+ ";" + "generate " + rootname + ";",
				"if unknown dxbase: input dxbase fi;" + "gensize:=" + realsize
						+ ";" + "generate " + rootname + ";",
				"input cscode; use_driver;", "input fikparm;",
				"input cbgreek;",
				"design_size := " + realsize + ";" + "input " + rootname + ";" };
		// Find the matching pattern
		int match = 0;
		for (; match < name_patterns.length; match++) {
			if (name_patterns[match].matcher(name).matches())
				break;
		}
		// Write the file
		try {
			String fontdir = texmf_var + "/fonts/source/";
			new File(fontdir).mkdirs();
			FileWriter mfoutput = new FileWriter(fontdir + name + ".mf");
			mfoutput.write(mf_content[match]);
			mfoutput.close();
			makeLSR();
			return null;
		} catch (Exception e) {
			return new BaseResult(e);
		}
	}

	/**
	 * Make PK font using arguments from a command line
	 * 
	 * @param cmd
	 * @param output_analyzer
	 * @return
	 * @throws Exception
	 */
	private IResult makePK(String cmd, TeXMFOutputAnalyzer output_analyzer) {
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
				make_pk_font_timeout);
		if (mfresult != null && mfresult.hasException())
			return mfresult;
		else
			return executeTeXMF(new String[] { "gftopk", gf_name, pk_name },
					pk_loc, make_pk_font_timeout);
	}

	/**
	 * Generate the necessary TeX Font Metric (TFM) files
	 */
	private IResult makeTFM(String name, TeXMFOutputAnalyzer output_analyzer) {
		int mag = 1;
		String mfmode = "ljfour";
		String arg = "\\mode:=" + mfmode + "; \\mag:=" + mag
				+ "; nonstopmode; input " + name;
		File tfm_loc = new File(texmf_var + "/fonts/tfm/");
		tfm_loc.mkdirs();
		return executeTeXMF(new String[] { "mf", arg }, tfm_loc,
				make_font_tfm_timeout);
	}

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

	private IResult runKpathsea(String kpsecmd) {
		// System.out.println("Execute Kpathsea command >> " + kpsecmd);
		if (kpsecmd.startsWith("mktexfmt"))
			return makeFMT(kpsecmd.substring("mktexfmt".length()).trim(),
					output_analyzer);
		else if (kpsecmd.startsWith("mktexpk"))
			return makePK(kpsecmd, output_analyzer);
		else if (kpsecmd.startsWith("mktextfm"))
			return makeTFM(kpsecmd.substring("mktextfm".length()).trim(),
					output_analyzer);
		else if (kpsecmd.startsWith("mktexmf"))
			return makeMF(kpsecmd.substring("mktexmf".length()).trim());
		else {
			// System.out.println("Invalid command!");
			return null;
		}
	}

}
