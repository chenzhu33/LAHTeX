package lah.tex.core;

import lah.tex.interfaces.ISeeker;
import lah.utils.spectre.CollectionPrinter;

public class TeXMFInputFileNotFoundException extends Exception {

	private static final long serialVersionUID = 1L;

	protected String missing_file;

	protected String[] missing_package;

	TeXMFInputFileNotFoundException() {
	}

	public TeXMFInputFileNotFoundException(String missing_file,
			String default_file_extension) {
		this.missing_file = missing_file
				+ (missing_file.indexOf('.') < 0
						&& default_file_extension != null ? "."
						+ default_file_extension : "");
	}

	@Override
	public String getMessage() {
		return "Missing "
				+ missing_file
				+ (missing_package != null ? ". Probably the package"
						+ (missing_package.length > 1 ? "s " : " ")
						+ CollectionPrinter.stringOfArray(missing_package,
								", ", null, null)
						+ (missing_package.length > 1 ? " are " : " is ")
						+ "missing or not properly installed." : "");
	}

	public String getMissingFile() {
		return missing_file;
	}

	public String[] getMissingPackage() {
		return missing_package;
	}

	String getMissingPackageForFile(String file_name) {
		if (file_name.equals("mf"))
			return "metafont";
		else if (file_name.equals("gftopk"))
			return "mfware";
		else if (file_name.equals("texmf.cnf"))
			return "kpathsea";
		else
			return file_name;
	}

	public void identifyMissingPackage(ISeeker seeker) throws Exception {
		if (missing_file.equals("mf"))
			missing_package = new String[] { "metafont" };
		else if (missing_file.equals("gftopk"))
			missing_package = new String[] { "mfware" };
		else if (missing_file.equals("texmf.cnf"))
			missing_package = new String[] { "kpathsea" };
		else if (!missing_file.contains("."))
			missing_package = new String[] { missing_file };
		else
			missing_package = seeker.seekFile(getMissingFile());
	}

}
