package lah.tex;

import java.util.Set;

import lah.spectre.interfaces.IClient;
import lah.spectre.interfaces.IFileSupplier;
import lah.tex.interfaces.ICompilationCommand;
import lah.tex.interfaces.ICompilationResult;
import lah.tex.interfaces.ICompiler;
import lah.tex.interfaces.IEnvironment;
import lah.tex.interfaces.IInstallationResult;
import lah.tex.interfaces.IInstaller;
import lah.tex.interfaces.ILoader;
import lah.tex.interfaces.IPackageListRetrievalResult;
import lah.tex.interfaces.ISeeker;
import lah.tex.pkgman.Installer;
import lah.tex.pkgman.Loader;
import lah.tex.pkgman.Seeker;

class TeXMF extends AbstractTeXMF {

	public static final String _CPU = "arm";

	public static final String _OS = "android";

	public static final String ARCH = _CPU + "-" + _OS;

	private IEnvironment environment;

	private ICompiler texmf_compiler;

	private IInstaller texmf_installer;

	private ILoader texmf_loader;

	private ISeeker texmf_seeker;

	TeXMF(IEnvironment environment) {
		this.environment = environment;
	}

	@Override
	public ICompilationResult compile(IClient<ICompilationResult> client,
			ICompilationCommand command, long timeout) {
		return getCompiler().compile(client, command, timeout);
	}

	private ICompiler getCompiler() {
		if (texmf_compiler == null)
			texmf_compiler = new lah.tex.compile.Compiler(environment,
					getSeeker(), getInstaller());
		return texmf_compiler;
	}

	@Override
	public Set<String> getInstalledPackages() {
		return getInstaller().getInstalledPackages();
	}

	private IInstaller getInstaller() {
		if (texmf_installer == null)
			texmf_installer = new Installer(environment);
		return texmf_installer;
	}

	private ILoader getLoader() {
		if (texmf_loader == null)
			texmf_loader = new Loader(environment);
		return texmf_loader;
	}

	private ISeeker getSeeker() {
		if (texmf_seeker == null)
			texmf_seeker = new Seeker(environment);
		return texmf_seeker;
	}

	@Override
	public IInstallationResult install(IClient<IInstallationResult> client,
			IFileSupplier package_supplier, String[] package_names,
			boolean ignore_installed) {
		return getInstaller().install(client, package_supplier, package_names,
				ignore_installed);
	}

	@Override
	public IPackageListRetrievalResult loadPackageList() {
		return getLoader().loadPackageList();
	}

	@Override
	public void makeLanguageConfiguration(String[] languages,
			boolean[] enable_languages) throws Exception {
		getInstaller().makeLanguageConfiguration(languages, enable_languages);
	}

	@Override
	public void makeLSR(String[] files) throws Exception {
		getInstaller().makeLSR(files);
	}

	@Override
	public String[] seekFile(String query) throws Exception {
		return getSeeker().seekFile(query);
	}

}
