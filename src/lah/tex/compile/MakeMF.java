package lah.tex.compile;

import java.io.File;
import java.io.FileWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lah.tex.Task;

public class MakeMF extends Task {

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

	private String name;

	public MakeMF(String name) {
		this.name = name;
	}

	@Override
	public String getDescription() {
		return "Generate METAFONT font " + name + ".mf";
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

	@Override
	public void run() {
		Matcher rootname_pointsize_matcher = mf_font_rootname_pointsize_pattern
				.matcher(name);
		String realsize = null, rootname = null;
		if (rootname_pointsize_matcher.matches()) {
			rootname = rootname_pointsize_matcher.group(1);
			String ptsizestr = rootname_pointsize_matcher.group(2);
			// System.out.println("Root name = " + rootname);
			// System.out.println("Point size = " + ptsizestr);
			if (ptsizestr.isEmpty()) {
				setException(new Exception(
						"Invalid point size input for mktexmf"));
				return;
			} else
				realsize = getRealSize(Integer.parseInt(ptsizestr));
		} else {
			// Invalid name pattern
			setException(new Exception("Invalid font name pattern in mktexmf"));
			return;
		}

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
			String mf_font_directory = environment.getTeXMFRootDirectory()
					+ "/texmf-var/fonts/source/";
			new File(mf_font_directory).mkdirs();
			FileWriter mf_output = new FileWriter(mf_font_directory + name
					+ ".mf");
			mf_output.write(mf_content[match]);
			mf_output.close();
			// installer.makeLSR(null);
		} catch (Exception e) {
			setException(e);
			return;
		}
	}

}
