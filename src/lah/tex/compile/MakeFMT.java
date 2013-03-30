package lah.tex.compile;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lah.tex.manage.MakeLanguageConfigurations;

public class MakeFMT extends CompileDocument {

	/**
	 * Pattern for TeX, MetaFont or MetaPost memory dump file
	 */
	public static final Pattern format_pattern = Pattern.compile("([a-z]*)\\.(fmt|base|mem)");

	private static MakeLanguageConfigurations make_lang_config_task;

	/**
	 * The format file to make must be of form *.fmt for TeX format, *.base for
	 * MetaFont format, *.mem for MetaPost format
	 */
	private String format;

	public MakeFMT(String format) {
		this.format = format;
		Matcher format_matcher = format_pattern.matcher(format);
		if (!format_matcher.find())
			return;

		String name = format_matcher.group(1);
		String type = format_matcher.group(2);
		String program;
		String default_ext;
		String[] options = null;

		if (type.equals("fmt")) {
			tex_engine = program = CompileDocument.getProgramFromFormat(format);
			default_ext = "tex";
			if (format.startsWith("pdf") || format.startsWith("xe"))
				options = new String[] { "-etex" };
		} else if (type.equals("base")) {
			tex_engine = "metafont";
			program = default_ext = "mf";
		} else { // type == "mem"
			tex_engine = "metapost";
			program = "mpost";
			default_ext = "mp";
		}

		if (name == null || tex_engine == null)
			return;

		command = new String[(options == null ? 0 : options.length) + 4];
		command[0] = program;
		command[1] = "-ini";
		command[2] = "-interaction=nonstopmode";
		if (options != null)
			System.arraycopy(options, 0, command, 3, options.length);
		command[command.length - 1] = name + ".ini"; // the *.ini input file
		setDefaultFileExtension(default_ext);
	}

	@Override
	public String getDescription() {
		return "Make format " + format;
	}

	@Override
	public void run() {
		reset();
		try {
			setState(State.STATE_EXECUTING);

			// Prepare the default language files if they do not exist
			// Regenerate path database to make sure that necessary input
			// files to make memory dumps (*.ini, *.mf, *.tex, ...) are
			// found
			if (!new File(environment.getTeXMFRootDirectory() + "/texmf-var/tex/generic/config/language.dat").exists()
					|| !new File(environment.getTeXMFRootDirectory() + "/texmf-var/tex/generic/config/language.def")
							.exists()) {
				if (make_lang_config_task == null)
					make_lang_config_task = new MakeLanguageConfigurations(null);
				make_lang_config_task.run();
				if (make_lang_config_task.hasException()) {
					setException(make_lang_config_task.getException());
					return;
				}
			}

			// Location of the memory dump file
			File fmt_loc = new File(environment.getTeXMFRootDirectory() + "/texmf-var/web2c/" + tex_engine);
			if (!fmt_loc.exists())
				fmt_loc.mkdirs();

			// Now create and run the process to generate the format file
			checkProgram(command[0]);
			shell.fork(command, fmt_loc, this, default_compilation_timeout);
			runFinalMakeLSR();
		} catch (Exception e) {
			setException(e);
			return;
		}
	}

}
