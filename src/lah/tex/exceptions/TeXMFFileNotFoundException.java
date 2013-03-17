package lah.tex.exceptions;

import lah.tex.Task;
import lah.tex.manage.InstallationTask;
import lah.tex.manage.PackageSearchTask;

public class TeXMFFileNotFoundException extends ResolvableException {

	private static final long serialVersionUID = 1L;

	protected String missing_file;

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
	public String getMessage() {
		return "Missing " + missing_file;
	}

	@Override
	public Task getResolution() {
		String[] missing_packages;
		if (missing_file.equals("language.dat")
				|| missing_file.equals("language.def"))
			missing_packages = new String[] { "hyphen-base" };
		else if (missing_file.equals("mf"))
			missing_packages = new String[] { "metafont" };
		else if (missing_file.equals("gftopk"))
			missing_packages = new String[] { "mfware" };
		else if (missing_file.equals("texmf.cnf"))
			missing_packages = new String[] { "kpathsea" };
		else if (!missing_file.contains("."))
			// engines like tex, pdftex, etc
			missing_packages = new String[] { missing_file };
		else {
			// other input files
			PackageSearchTask task = new PackageSearchTask(missing_file);
			task.run();
			if (task.hasException())
				missing_packages = null;
			else
				missing_packages = task.getSearchResult();
		}
		return (missing_packages == null) ? null : new InstallationTask(
				missing_packages);
	}
}
