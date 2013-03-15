package lah.tex.manage;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lah.spectre.stream.Streams;
import lah.tex.Task;
import lah.tex.exceptions.TeXMFFileNotFoundException;

public class MakeLanguageConfigurations extends Task {

	private static final Pattern lang_pattern = Pattern
			.compile("% from hyphen-(.*):\n[^%]*");

	private static final String[] language_files = { "language.dat",
			"language.def" };

	private Map<String, String> language_dat_map, language_def_map;

	private String[] languages;

	public MakeLanguageConfigurations(String[] languages) {
		this.languages = languages;
	}

	public String[] getAllLanguages() throws Exception {
		for (String lf : language_files) {
			File langfile = new File(environment.getTeXMFRootDirectory()
					+ "/texmf/tex/generic/config/" + lf);
			if (!langfile.exists())
				throw new TeXMFFileNotFoundException(lf, null);
			String content = Streams.readTextFile(langfile);
			Matcher langconfig_matcher = lang_pattern.matcher(content);
			Map<String, String> lfmap;
			if (lf.equals(language_files[0]))
				lfmap = language_dat_map = new TreeMap<String, String>();
			else
				lfmap = language_def_map = new TreeMap<String, String>();
			while (langconfig_matcher.find())
				lfmap.put(langconfig_matcher.group(1),
						langconfig_matcher.group());
		}
		return language_dat_map.keySet().toArray(
				new String[language_dat_map.size()]);
	}

	public void makeLanguageConfiguration(String[] languages) throws Exception {

	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		final String texmf_language_config = environment
				.getTeXMFRootDirectory() + "/texmf-var/tex/generic/config";
		new File(texmf_language_config).mkdirs();
		// Write language.dat
		String language_dat = "english		hyphen.tex  % do not change!\n"
				+ "=usenglish\n"
				+ "=USenglish\n"
				+ "=american\n"
				+ "dumylang	dumyhyph.tex    %for testing a new language.\n"
				+ "nohyphenation	zerohyph.tex    %a language with no patterns at all.\n";
		if (languages != null) {
			if (language_dat_map == null)
				try {
					getAllLanguages();
				} catch (Exception e) {
					setException(e);
					return;
				}
			for (int i = 0; i < languages.length; i++)
				language_dat = language_dat
						+ language_dat_map.get(languages[i]);
		}
		try {
			Streams.writeStringToFile(language_dat, new File(
					texmf_language_config + "/language.dat"), false);
		} catch (IOException e) {
			setException(e);
			return;
		}
		// Write language.def
		String language_def = "%% e-TeX V2.0;2\n"
				+ "\\addlanguage {USenglish}{hyphen}{}{2}{3} %%% This MUST be the first non-comment line of the file\n";
		if (languages != null) {
			if (language_def_map == null)
				try {
					getAllLanguages();
				} catch (Exception e) {
					setException(e);
					return;
				}
			for (int i = 0; i < languages.length; i++)
				language_def = language_def
						+ language_def_map.get(languages[i]);
		}
		language_def = language_def
				+ "\\uselanguage {USenglish}             %%% This MUST be the last line of the file.\n";
		try {
			Streams.writeStringToFile(language_def, new File(
					texmf_language_config + "/language.def"), false);
		} catch (IOException e) {
			setException(e);
			return;
		}
		// Regenerate path databases and remove existing format files, if any
		try {
			shell.fork(
					new String[] {
							"rm",
							"-r",
							environment.getTeXMFRootDirectory()
									+ "/texmf-var/web2c" }, null);
		} catch (Exception e) {
			setException(e);
			return;
		}
		// makeLSR(null);
	}
}
