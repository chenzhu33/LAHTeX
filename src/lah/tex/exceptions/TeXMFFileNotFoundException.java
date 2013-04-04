package lah.tex.exceptions;

import lah.spectre.Collections;
import lah.tex.Task;
import lah.tex.compile.MakeFMT;
import lah.tex.manage.InstallPackage;

public class TeXMFFileNotFoundException extends SolvableException {

	private static final long serialVersionUID = 1L;

	protected String missing_file;

	private String[] missing_packages;

	protected TeXMFFileNotFoundException() {
	}

	public TeXMFFileNotFoundException(String missing_file, String default_file_extension) {
		if (missing_file.indexOf('.') < 0 && default_file_extension != null)
			this.missing_file = missing_file + "." + default_file_extension;
		else
			this.missing_file = missing_file;
	}

	@Override
	public boolean equals(Object obj) {
		return (obj instanceof TeXMFFileNotFoundException) ? missing_file
				.equals(((TeXMFFileNotFoundException) obj).missing_file) : false;
	}

	@Override
	public String getMessage() {
		return "Missing "
				+ missing_file
				+ (missing_packages == null ? "" : "\nProbably package "
						+ Collections.stringOfArray(missing_packages, ", ", null, null)
						+ (missing_packages.length > 1 ? " are" : " is") + " either not installed or corrupted.");
	}

	@Override
	public Task getSolution() throws Exception {
		if (MakeFMT.format_pattern.matcher(missing_file).matches())
			return new MakeFMT(missing_file);

		// System.out.println("Get solution for missing {" + missing_file + "}");
		if (missing_file.equals("language.dat") || missing_file.equals("language.def"))
			// this case is necessary because we might require these files
			// before the package index is available
			missing_packages = new String[] { "hyphen-base" };
		else if (missing_file.equals("texmf.cnf"))
			missing_packages = new String[] { "kpathsea" };
		else if (missing_file.equals("mf"))
			missing_packages = new String[] { "metafont" };
		else if (missing_file.equals("gftopk"))
			missing_packages = new String[] { "mfware" };
		else if (missing_file.equals("mpost"))
			missing_packages = new String[] { "metapost" };
		else if (!missing_file.contains("."))
			// engines like tex, pdftex, etc
			missing_packages = new String[] { missing_file };
		else
			// other input files
			missing_packages = Task.findPackagesWithFile(missing_file);

		return (missing_packages == null) ? null : new InstallPackage(missing_packages);
	}

}
