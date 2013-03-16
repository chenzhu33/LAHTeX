package lah.tex.exceptions;

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
		// System.out.println("KpathseaException : " + kpathsea_command);
		kpse_command = kpathsea_command.trim();
		String name = kpse_command.substring(kpse_command.lastIndexOf(' '))
				.trim();
		if (kpse_command.startsWith("mktextfm"))
			missing_file = name + ".tfm";
		else if (kpse_command.startsWith("mktexmf"))
			missing_file = name + ".mf";
		else if (kpse_command.startsWith("mktexpk"))
			missing_file = name + ".pk";
		else
			missing_file = name; // *.mf or *.fmt
	}

	public String getCommand() {
		return kpse_command;
	}

	/**
	 * Execute the translated method which corresponds to a mktex(fmt|mf|pk|tfm)
	 * script in Kpathsea package
	 * 
	 * @param command
	 *            The command to execute
	 * @return
	 */
	// private IResult executeKpathseaScript(String command) {
	// if (command.startsWith("mktexfmt"))
	// return makeFMT(command.substring("mktexfmt".length()).trim());
	// else if (command.startsWith("mktexpk"))
	// return makePK(command);
	// else if (command.startsWith("mktextfm"))
	// return makeTFM(command.substring("mktextfm".length()).trim());
	// else if (command.startsWith("mktexmf"))
	// return makeMF(command.substring("mktexmf".length()).trim());
	// else
	// return null; // should it be a new BaseResult
	// }

}
