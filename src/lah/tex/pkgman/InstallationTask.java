package lah.tex.pkgman;

import lah.spectre.Collections;
import lah.tex.core.BaseTask;
import lah.tex.interfaces.IInstallationResult;

public class InstallationTask extends BaseTask implements IInstallationResult {

	private int installation_state;

	int num_success_packages;

	private int[] package_states;

	private String[] packages;

	private String[] pending_packages;

	private String[] requested_packages;

	public InstallationTask() {
	}

	public InstallationTask(String[] packages) {
		this.packages = packages;
	}

	@Override
	public CharSequence getDescription() {
		return "Install" + Collections.stringOfArray(packages, " ", null, null);
	}

	@Override
	public int getInstallationState() {
		return installation_state;
	}

	@Override
	public int getPackageStatus(int position) {
		return package_states[position];
	}

	@Override
	public String[] getPendingPackages() {
		return pending_packages;
	}

	@Override
	public String[] getRequestedPackages() {
		return requested_packages;
	}

	@Override
	public String getSummaryString() {
		return num_success_packages
				+ (pending_packages != null ? "/" + pending_packages.length
						: "") + " packages successfully installed.";
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		status = "Executing";
		// portal.getTeXMF().install(this, portal.getFileSupplier(), packages,
		// false);
		status = "Complete";
	}

	@Override
	public void setException(Exception e) {
		super.setException(e);
		setState(STATE_INSTALLATION_ERROR);
	}

	void setPackageState(int package_id, int package_state) {
		package_states[package_id] = package_state;
		if (package_state == PACKAGE_SUCCESSFULLY_INSTALLED)
			num_success_packages++;
	}

	void setPendingPackages(String[] packages) {
		if (packages != null) {
			// move to state installing packages
			setState(STATE_INSTALLING_PACKAGES);
			num_success_packages = 0;
			pending_packages = packages;
			package_states = new int[pending_packages.length];
		}
	}

	void setRequestedPackages(String[] req_pkgs) {
		requested_packages = req_pkgs;
	}

	void setState(int state) {
		installation_state = state;
	}

}
