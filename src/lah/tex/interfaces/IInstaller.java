package lah.tex.interfaces;

import java.util.Set;

import lah.spectre.interfaces.IClient;
import lah.spectre.interfaces.IFileSupplier;

public interface IInstaller {

	String[] getAllLanguages() throws Exception;

	Set<String> getInstalledPackages();

	IInstallationResult install(IClient<IInstallationResult> client,
			IFileSupplier package_supplier, String[] package_names,
			boolean ignore_installed);

	void makeFontConfiguration() throws Exception;

	void makeLanguageConfiguration(String[] languages) throws Exception;

	void makeLSR(String[] files) throws Exception;

}
