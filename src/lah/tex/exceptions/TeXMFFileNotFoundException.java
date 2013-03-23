package lah.tex.exceptions;

import lah.spectre.Collections;
import lah.tex.Task;
import lah.tex.manage.InstallationTask;

public class TeXMFFileNotFoundException extends SolvableException {

	private static final long serialVersionUID = 1L;

	protected String missing_file;

	private String[] missing_packages;

	protected TeXMFFileNotFoundException() {
	}

	public TeXMFFileNotFoundException(String missing_file,
			String default_file_extension) {
		if (missing_file.indexOf('.') < 0 && default_file_extension != null)
			this.missing_file = missing_file + "." + default_file_extension;
		else
			this.missing_file = missing_file;
	}

	@Override
	public void identifySolution() throws Exception {
		if (missing_file.equals("language.dat")
				|| missing_file.equals("language.def"))
			// this case is necessary because we might require these files
			// before the package index is available
			missing_packages = new String[] { "hyphen-base" };
		else if (missing_file.equals("texmf.cnf"))
			missing_packages = new String[] { "kpathsea" };
		else if (missing_file.equals("mf"))
			missing_packages = new String[] { "metafont" };
		else if (missing_file.equals("gftopk"))
			missing_packages = new String[] { "mfware" };
		else if (!missing_file.contains("."))
			// engines like tex, pdftex, etc
			missing_packages = new String[] { missing_file };
		else
			// other input files
			missing_packages = Task.findPackagesWithFile(missing_file);
	}

	@Override
	public String getMessage() {
		return "Missing "
				+ missing_file
				+ (missing_packages != null ? ". Probably package "
						+ Collections.stringOfArray(missing_packages, ", ",
								null, null) + " is not installed/corrupted."
						: "");
	}

	@Override
	public Task getSolution() throws Exception {
		return (missing_packages == null) ? null : new InstallationTask(
				missing_packages);
	}

	@Override
	public boolean hasSolution() {
		return missing_packages != null;
	}

}
