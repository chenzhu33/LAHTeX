package lah.tex.compile;

import java.io.File;
import java.util.Map;

import lah.spectre.CommandLineArguments;

public class MakePK extends CompileDocument {

	private String kpse_command, missing_file;

	public MakePK(String command, String missing_file) {
		this.kpse_command = command;
		this.missing_file = missing_file;
	}

	@Override
	public String getDescription() {
		return "Make PK font" + missing_file;
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

	@Override
	public void run() {
		reset();
		String[] args = kpse_command.split("\\s+");
		Map<String, String> arg_map = CommandLineArguments.parseCommandLineArguments(args);
		String name = arg_map.get("$ARG");
		String dpi_str = arg_map.get("--dpi");
		int dpi = (dpi_str != null ? Integer.parseInt(dpi_str) : 600);
		String bdpi_str = arg_map.get("--bdpi");
		int bdpi = (bdpi_str != null ? Integer.parseInt(bdpi_str) : 600);
		String mag = arg_map.get("--mag");
		String mfmode = arg_map.get("--mfmode");

		if (mfmode == null || mfmode.equals("/"))
			mfmode = guessModeFromBDPI(bdpi);
		String arg = "\\mode:=" + mfmode + "; \\mag:=" + mag + "; nonstopmode; input " + name;
		String gf_name = name + "." + dpi + "gf";
		String pk_name = name + "." + dpi + "pk";
		File pk_loc = new File(environment.getTeXMFRootDirectory() + "/texmf-var/fonts/pk/" + mfmode + "/tmp/dpi" + dpi);
		pk_loc.mkdirs();
		setDefaultFileExtension("mf");
		try {
			checkProgram("mf");
			shell.fork(new String[] { "mf", arg }, pk_loc, this, default_compilation_timeout);
			checkProgram("gftopk");
			shell.fork(new String[] { "gftopk", gf_name, pk_name }, pk_loc);
			runFinalMakeLSR();
		} catch (Exception e) {
			setException(e);
			return;
		}
	}

}
