package lah.tex.interfaces;

import java.util.Set;

import lah.utils.spectre.interfaces.FileSupplier;
import lah.utils.spectre.interfaces.ProgressListener;

public interface IInstaller {

	Set<String> getInstalledPackages();

	IInstallationResult install(
			ProgressListener<IInstallationResult> progress_listener,
			FileSupplier package_supplier, String[] package_names,
			boolean ignore_installed);

}
