package lah.tex.core;

/**
 * Raise when a kpathsea command needs to be run
 * 
 * @author L.A.H.
 * 
 */
public class KpathseaException extends TeXMFInputFileNotFoundException {

	private static final long serialVersionUID = 1L;

	private String kpse_command;

	public KpathseaException(String kpathsea_command) {
		System.out.println("KpathseaException : " + kpathsea_command);
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
		// System.out.println(kpse_command);
		return kpse_command;
	}

}
