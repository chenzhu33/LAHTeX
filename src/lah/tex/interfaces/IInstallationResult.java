package lah.tex.interfaces;

import lah.spectre.interfaces.IResult;

public interface IInstallationResult extends IResult {

	public static final int PACKAGE_PENDING = 0, PACKAGE_INSTALLING = 1,
			PACKAGE_SUCCESSFULLY_INSTALLED = 2, PACKAGE_FAIL = 3;

	/**
	 * Get the status of a pending package
	 * 
	 * @param position
	 * @return
	 */
	int getPackageStatus(int position);

	/**
	 * Get the list of packages waiting to be installed; this is the original
	 * list of requested packages together with all the dependencies.
	 * 
	 * @return
	 */
	String[] getPendingPackages();

	String[] getRequestedPackages();

}
