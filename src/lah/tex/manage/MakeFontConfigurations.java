package lah.tex.manage;

import java.io.File;
import java.io.IOException;

import lah.spectre.stream.Streams;
import lah.tex.Task;

public class MakeFontConfigurations extends Task {

	@Override
	public String getDescription() {
		return "Generate fontconfig configuration and cache";
	}

	@Override
	public void run() {
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
			try {
				Streams.writeStringToFile(config, configfile, false);
			} catch (IOException e) {
				setException(e);
				return;
			}
		}
		// Execute command to re-generate the font cache
		// TODO Remove this!
		try {
			shell.fork(new String[] { environment.getTeXMFBinaryDirectory()
					+ "/fc-cache" }, null, new String[] { "FONTCONFIG_PATH",
					configdir.getAbsolutePath() }, null, 0);
		} catch (Exception e) {
			setException(e);
			return;
		}
	}

}
