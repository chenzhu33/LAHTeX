package lah.tex.interfaces;

import lah.spectre.interfaces.IResult;

public interface IInstallationResult extends IResult {

	public static final int PACKAGE_PENDING = 0, PACKAGE_INSTALLING = 1,
			PACKAGE_SUCCESSFULLY_INSTALLED = 2, PACKAGE_FAIL = 3;

	public static final int STATE_CONSTRUCT_PACKAGE_LIST = 0,
			STATE_INSTALLING_PACKAGES = 1, STATE_INSTALLATION_FINISH = 2,
			STATE_INSTALLATION_ERROR = 3;

	/**
	 * Get the current state of the installation process, must be one of STATE_*
	 * 
	 * @return
	 */
	int getInstallationState();

	/**
	 * Get the status of a pending package
	 * 
	 * @param position
	 * @return
	 */
	int getPackageStatus(int position);

	/**
	 * Get the list of packages waiting to be installed; this is the original
	 * list of requested packages together with all the dependencies. This is
	 * only available after STATE_CONSTRUCT_PACKAGE_LIST (i.e. during state
	 * STATE_INSTALLING_PACKAGES and STATE_INSTALLATION_FINISH)
	 * 
	 * @return
	 */
	String[] getPendingPackages();

	String[] getRequestedPackages();

	String getSummaryString();

}
