package lah.tex.compile;

import java.io.File;
import java.util.Map;

import lah.spectre.CommandLineArguments;
import lah.spectre.interfaces.IResult;
import lah.tex.interfaces.IEnvironment;

public class MakePKTask extends CompilationTask {
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
	 * Make PK font using arguments from a command line
	 * 
	 * @param cmd
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings("unused")
	private IResult makePK(IEnvironment environment, String cmd) {
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
		File pk_loc = new File(environment.getTeXMFRootDirectory()
				+ "/texmf-var/fonts/pk/" + mfmode + "/tmp/dpi" + dpi);
		pk_loc.mkdirs();
		IResult mfresult = executeTeXMF(new String[] { "mf", arg }, pk_loc,
				"mf");
		if (mfresult != null && mfresult.hasException())
			return mfresult;
		else
			return executeTeXMF(new String[] { "gftopk", gf_name, pk_name },
					pk_loc, null);
	}

}
