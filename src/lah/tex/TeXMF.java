package lah.tex;

import java.io.File;

import lah.spectre.Collections;
import lah.spectre.interfaces.IFileSupplier;
import lah.spectre.multitask.TaskManager;
import lah.spectre.process.TimedShell;
import lah.tex.compile.CompilationTask;
import lah.tex.interfaces.IEnvironment;
import lah.tex.manage.InstallationTask;
import lah.tex.manage.MakeFontConfigurations;
import lah.tex.manage.MakeLanguageConfigurations;

/**
 * Main expose interface to client of this library.
 * 
 * @author L.A.H.
 * 
 */
public class TeXMF extends TaskManager<Task> {

	public static final int TASK_COMPILE = 0, TASK_INSTALL_PACKAGE = 1,
			TASK_MAKE_FONTCONFIG = 2, TASK_MAKE_LANGUAGES_CONFIG = 3;

	private static TeXMF texmf_instance;

	public static final TeXMF getInstance(IEnvironment environment,
			IFileSupplier file_supplier) {
		if (texmf_instance == null)
			texmf_instance = new TeXMF(environment, file_supplier);
		return texmf_instance;
	}

	private TeXMF(IEnvironment environment, IFileSupplier file_supplier) {
		Task.environment = environment;
		Task.file_supplier = file_supplier;
		Task.shell = new TimedShell();

		// Set up environment variables such as PATH, TMPDIR, FONTCONFIG
		// (for XeTeX to work) and OSFONTDIR (for LuaTeX font search)
		String path = environment.getTeXMFBinaryDirectory() + ":"
				+ System.getenv("PATH");
		String tmpdir = environment.getTeXMFRootDirectory() + "/texmf-var/tmp";
		new File(tmpdir + "/").mkdirs();
		String fontconfig_path = environment.getTeXMFRootDirectory()
				+ "/texmf-var/fonts/conf";
		new File(fontconfig_path + "/").mkdirs();
		Task.shell.export("PATH", path);
		Task.shell.export("TMPDIR", tmpdir);
		Task.shell.export("FONTCONFIG_PATH", fontconfig_path);
		Task.shell.export("OSFONTDIR", environment.getOSFontsDir());
	}

	/**
	 * Create a new task and submit for scheduled execution
	 * 
	 * @param task_type
	 * @param args
	 * @return the created task
	 */
	public Task createTask(int task_type, String[] args) {
		System.out.println("Create task "
				+ Collections.stringOfArray(args, ", ", "[", "]"));
		Task result_task;
		switch (task_type) {
		case TASK_COMPILE:
			result_task = new CompilationTask(args[0], args[1]);
			break;
		case TASK_INSTALL_PACKAGE:
			result_task = new InstallationTask(args);
			break;
		case TASK_MAKE_FONTCONFIG:
			result_task = new MakeFontConfigurations();
			break;
		case TASK_MAKE_LANGUAGES_CONFIG:
			result_task = new MakeLanguageConfigurations(args);
			break;
		default:
			result_task = null;
		}
		if (result_task != null)
			add(result_task);
		return result_task;
	}

	public String[] getAllLanguages() throws Exception {
		return null;
	}

}
