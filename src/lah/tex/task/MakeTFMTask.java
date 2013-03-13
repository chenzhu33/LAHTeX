package lah.tex.task;

import java.io.File;

import lah.tex.interfaces.IEnvironment;

public class MakeTFMTask extends CompilationTask {

	/**
	 * Generate the necessary TeX Font Metric (TFM) files
	 */
	@SuppressWarnings("unused")
	private CompilationTask makeTFM(IEnvironment environment, String name) {
		int mag = 1;
		String mfmode = "ljfour";
		String arg = "\\mode:=" + mfmode + "; \\mag:=" + mag
				+ "; nonstopmode; input " + name;
		File tfm_loc = new File(environment.getTeXMFRootDirectory()
				+ "/texmf-var/fonts/tfm/");
		tfm_loc.mkdirs();
		return executeTeXMF(new String[] { "mf", arg }, tfm_loc, "mf");
	}

}
