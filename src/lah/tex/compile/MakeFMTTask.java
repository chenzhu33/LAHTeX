package lah.tex.compile;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lah.spectre.interfaces.IResult;
import lah.tex.interfaces.IEnvironment;

public class MakeFMTTask extends CompilationTask {

	/**
	 * Pattern for TeX, MetaFont or MetaPost memory dump file
	 */
	private static final Pattern format_pattern = Pattern
			.compile("([a-z]*)\\.(fmt|base|mem)");

	/**
	 * The format file to make
	 */
	private String format;

	public MakeFMTTask(String format) {
		this.format = format;
	}

	/**
	 * Make a memory dump file (i.e. a format file)
	 * 
	 * @param format
	 *            Name of a format file (must be of form *.fmt for TeX format,
	 *            *.base for MetaFont format, *.mem for MetaPost format)
	 * @return IResult
	 */
	@SuppressWarnings("unused")
	private IResult makeFMT(IEnvironment environment) {
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
			engine = program = CompilationTask.getProgramFromFormat(format);
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
			// try {
			// installer.makeLanguageConfiguration(null);
			// } catch (Exception e) {
			// return new BaseTask(e);
			// }
		}

		// Location of the memory dump file
		File fmt_loc = new File(environment.getTeXMFRootDirectory()
				+ "/texmf-var/web2c/" + engine);
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
}
