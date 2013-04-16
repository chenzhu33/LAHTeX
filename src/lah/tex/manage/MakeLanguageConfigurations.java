package lah.tex.manage;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lah.spectre.stream.Streams;
import lah.tex.Task;
import lah.tex.exceptions.TeXMFFileNotFoundException;

/**
 * Task to generate language configurations (hyphenation patterns)
 * 
 * @author L.A.H.
 * 
 */
public class MakeLanguageConfigurations extends Task {

	private static final String[] config_prefixes = {
			"english		hyphen.tex  % do not change!\n=usenglish\n=USenglish\n=american\ndumylang	dumyhyph.tex    %for testing a new language.\nnohyphenation	zerohyph.tex    %a language with no patterns at all.\n",
			"%% e-TeX V2.0;2\n\\addlanguage {USenglish}{hyphen}{}{2}{3} %%% This MUST be the first non-comment line of the file\n" };

	private static final String[] config_suffixes = { "",
			"\\uselanguage {USenglish}             %%% This MUST be the last line of the file.\n" };

	private static final String[] lang_config_files = { "language.dat", "language.def" };

	private static final Pattern lang_config_pattern = Pattern.compile("% from hyphen-(.*):\n[^%]*");

	private String[] languages;

	public MakeLanguageConfigurations(String[] languages) {
		this.languages = languages;
	}

	@Override
	public String getDescription() {
		return "Generate language configurations";
	}

	@Override
	public void run() {
		reset();
		String lang_config_loc = environment.getTeXMFRootDirectory() + "/texmf-var/tex/generic/config";
		new File(lang_config_loc + "/").mkdirs();
		for (int l = 0; l < lang_config_files.length; l++) {
			String lf = lang_config_files[l];
			File orig_config_file = new File(environment.getTeXMFRootDirectory() + "/texmf/tex/generic/config", lf);
			if (!orig_config_file.exists()) {
				setException(new TeXMFFileNotFoundException(lf, null));
				return;
			}
			File config_file = new File(lang_config_loc, lf);
			try {
				Map<String, String> lcfgmap = new TreeMap<String, String>();
				String lfcontent = Streams.readTextFile(orig_config_file);
				Matcher matcher = lang_config_pattern.matcher(lfcontent);
				while (matcher.find())
					lcfgmap.put(matcher.group(1), matcher.group());
				StringBuilder config = new StringBuilder(config_prefixes[l]);
				if (languages != null) {
					for (int i = 0; i < languages.length; i++) {
						if (languages[i] != null && lcfgmap.containsKey(languages[i]))
							config.append(lcfgmap.get(languages[i]));
					}
				}
				config.append(config_suffixes[l]);
				Streams.writeStringToFile(config.toString(), config_file, false);
			} catch (Exception e) {
				// for safety, delete the file if exception occurs
				config_file.delete();
				setException(e);
				return;
			}
		}
		// Regenerate path databases and remove existing format files (if any) so that they will be properly regenerated
		// in subsequent compilation with consideration for newly enabled/disabled languages
		try {
			shell.fork(new String[] { "rm", "-r", environment.getTeXMFRootDirectory() + "/texmf-var/web2c" }, null);
			runFinalMakeLSR();
		} catch (Exception e) {
			setException(e);
			return;
		}
	}
}
