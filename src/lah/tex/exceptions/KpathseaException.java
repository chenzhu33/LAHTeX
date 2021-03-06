package lah.tex.exceptions;

import lah.tex.Task;
import lah.tex.compile.MakeFMT;
import lah.tex.compile.MakeMF;
import lah.tex.compile.MakePK;
import lah.tex.compile.MakeTFM;

/**
 * Raise when a kpathsea command needs to be run
 * 
 * @author L.A.H.
 * 
 */
public class KpathseaException extends TeXMFFileNotFoundException {

	private static final long serialVersionUID = 1L;

	private String kpse_command;

	public KpathseaException(String kpathsea_command) {
		kpse_command = kpathsea_command.trim();
		String base_name = kpse_command.substring(kpse_command.lastIndexOf(' ')).trim();
		if (kpse_command.startsWith("mktextfm"))
			missing_file = base_name + ".tfm";
		else if (kpse_command.startsWith("mktexmf"))
			missing_file = base_name + ".mf";
		else if (kpse_command.startsWith("mktexpk"))
			missing_file = base_name.indexOf('.') >= 0 ? base_name : base_name + ".pk";
		else
			// format file *.(fmt|base|mem)
			missing_file = base_name;
	}

	@Override
	public boolean equals(Object obj) {
		return ((obj instanceof KpathseaException) && kpse_command.equals(((KpathseaException) obj).kpse_command))
				|| super.equals(obj);
	}

	public String getCommand() {
		return kpse_command;
	}

	@Override
	public Task getSolution() throws Exception {
		// System.out.println("Get solution for {" + kpse_command + "}");
		if (kpse_command.startsWith("mktexfmt"))
			return new MakeFMT(kpse_command.substring("mktexfmt".length()).trim());
		Task t = super.getSolution();
		if (t != null)
			return t;
		if (kpse_command.startsWith("mktexpk"))
			return new MakePK(kpse_command, missing_file);
		else if (kpse_command.startsWith("mktextfm"))
			return new MakeTFM(kpse_command.substring("mktextfm".length()).trim());
		else if (kpse_command.startsWith("mktexmf"))
			return new MakeMF(kpse_command.substring("mktexmf".length()).trim());
		else
			return null;
	}

}
