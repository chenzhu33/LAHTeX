package lah.tex.interfaces;

import java.util.Set;

import lah.spectre.interfaces.IClient;
import lah.spectre.interfaces.IFileSupplier;

public interface IInstaller {

	Set<String> getInstalledPackages();

	IInstallationResult install(IClient<IInstallationResult> client,
			IFileSupplier package_supplier, String[] package_names,
			boolean ignore_installed);

	void makeLanguageConfiguration(String[] languages,
			boolean[] enable_languages) throws Exception;

	void makeLSR(String[] files) throws Exception;

}
