package lah.tex.task;

import java.io.File;

import lah.spectre.process.TimedShell;
import lah.spectre.stream.Streams;
import lah.tex.interfaces.IEnvironment;

public class MakeFontConfigurations {

	private IEnvironment environment;

	private TimedShell shell;

	public void makeFontConfiguration() throws Exception {
		// Prepare the font configuration file (if necessary)
		File configfile = new File(environment.getTeXMFRootDirectory()
				+ "/texmf-var/fonts/conf/fonts.conf");
		File configdir = new File(environment.getTeXMFRootDirectory()
				+ "/texmf-var/fonts/conf/");
		if (!configdir.exists())
			configdir.mkdirs();
		File cachedir = new File(environment.getTeXMFRootDirectory()
				+ "/texmf-var/fonts/cache/");
		if (!cachedir.exists())
			cachedir.mkdirs();
		if (!configfile.exists()) {
			String tl_fonts = environment.getTeXMFRootDirectory()
					+ "/texmf-dist/fonts";
			String config = "<?xml version=\"1.0\"?>"
					+ "<!DOCTYPE fontconfig SYSTEM \"fonts.dtd\"><fontconfig>"
					+ "<cachedir>" + cachedir.getAbsolutePath()
					+ "</cachedir>\n" + "<dir>" + environment.getOSFontsDir()
					+ "</dir>\n" + "<dir>" + tl_fonts + "/opentype</dir>\n"
					+ "<dir>" + tl_fonts + "/truetype</dir>\n" + "<dir>"
					+ tl_fonts + "/type1</dir>\n" + "</fontconfig>\n";
			Streams.writeStringToFile(config, configfile, false);
		}
		// Execute command to re-generate the font cache
		shell.fork(new String[] { environment.getTeXMFBinaryDirectory()
				+ "/fc-cache" }, null, new String[] { "FONTCONFIG_PATH",
				configdir.getAbsolutePath() }, null, 0);
	}
}
