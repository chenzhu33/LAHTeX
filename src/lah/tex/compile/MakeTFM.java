package lah.tex.compile;

import java.io.File;

public class MakeTFM extends CompileDocument {

	private String name;

	public MakeTFM(String name) {
		this.name = name;
	}

	@Override
	public void run() {
		int mag = 1;
		String mfmode = "ljfour";
		String arg = "\\mode:=" + mfmode + "; \\mag:=" + mag
				+ "; nonstopmode; input " + name;
		File tfm_loc = new File(environment.getTeXMFRootDirectory()
				+ "/texmf-var/fonts/tfm/");
		tfm_loc.mkdirs();
		setDefaultFileExtension("mf");
		try {
			shell.fork(new String[] { "mf", arg }, tfm_loc);
			make_lsr_task.run();
		} catch (Exception e) {
			setException(e);
			return;
		}
	}

}
