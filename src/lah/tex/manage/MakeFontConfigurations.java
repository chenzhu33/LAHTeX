package lah.tex.manage;

import java.io.File;
import java.io.IOException;

import lah.spectre.multitask.TaskState;
import lah.spectre.stream.Streams;
import lah.tex.Task;

/**
 * Task to generate FONTCONFIG configuration file and the font cache
 * 
 * @author L.A.H.
 * 
 */
public class MakeFontConfigurations extends Task {

	private static final String[] FC_CACHE_CMD = new String[] { "fc-cache" };

	@Override
	public String getDescription() {
		return strings.getString("desc_generate_fontconfig_cache");
	}

	@Override
	public void run() {
		reset();
		setState(TaskState.EXECUTING);
		// Prepare the font configuration file (if necessary)
		File configfile = new File(environment.getTeXMFRootDirectory() + "/texmf-var/fonts/conf/fonts.conf");
		File configdir = new File(environment.getTeXMFRootDirectory() + "/texmf-var/fonts/conf/");
		if (!configdir.exists())
			configdir.mkdirs();
		File cachedir = new File(environment.getTeXMFRootDirectory() + "/texmf-var/fonts/cache/");
		if (!cachedir.exists())
			cachedir.mkdirs();
		if (!configfile.exists()) {
			String tl_fonts = environment.getTeXMFRootDirectory() + "/texmf-dist/fonts";
			String config = "<?xml version=\"1.0\"?>" + "<!DOCTYPE fontconfig SYSTEM \"fonts.dtd\"><fontconfig>"
					+ "<cachedir>" + cachedir.getAbsolutePath() + "</cachedir>\n" + "<dir>"
					+ environment.getOSFontsDirectory() + "</dir>\n<dir>" + tl_fonts + "/opentype</dir>\n" + "<dir>"
					+ tl_fonts + "/truetype</dir>\n" + "<dir>" + tl_fonts + "/type1</dir>\n" + "</fontconfig>\n";
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
			shell.fork(FC_CACHE_CMD, null);
			setState(TaskState.COMPLETE);
		} catch (Exception e) {
			setException(e);
			return;
		}
	}
}
