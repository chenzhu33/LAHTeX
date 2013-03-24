package lah.tex;

import java.io.File;

import lah.spectre.Collections;
import lah.spectre.interfaces.IFileSupplier;
import lah.spectre.multitask.TaskManager;
import lah.spectre.process.TimedShell;
import lah.tex.compile.CompileDocument;
import lah.tex.exceptions.SolvableException;
import lah.tex.interfaces.IEnvironment;
import lah.tex.manage.InstallPackage;
import lah.tex.manage.MakeFontConfigurations;
import lah.tex.manage.MakeLSR;
import lah.tex.manage.MakeLanguageConfigurations;

/**
 * Main expose interface to client of this library.
 * 
 * @author L.A.H.
 * 
 */
public class TeXMF extends TaskManager<Task> {

	public static enum TaskType {
		/**
		 * Compile a document
		 */
		TASK_COMPILE,
		/**
		 * Install a package
		 */
		TASK_INSTALL_PACKAGE,
		/**
		 * Generate fontconfig's configuration files and cache
		 */
		TASK_MAKE_FONTCONFIG,
		/**
		 * Generate language configurations (hyphenation patterns)
		 */
		TASK_MAKE_LANGUAGES_CONFIG,
		/**
		 * Do not execute anything
		 */
		TASK_NULL
	}

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
		Task.manager = this;
		Task.shell = new TimedShell();
		Task.make_lsr_task = new MakeLSR();

		// Set up environment variables such as PATH, TMPDIR, FONTCONFIG
		// (for XeTeX to work) and OSFONTDIR (for LuaTeX font search)
		// TODO set TEXMFCNF to the search locations of texmf.cnf as well
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
	 *            One of {@link TaskType} enumeration
	 * @param args
	 *            A list of Strings as additional argument, interpretation
	 *            depending on the task type
	 * @return A new {@link Task} created, added for scheduling
	 */
	public Task createTask(TaskType task_type, String[] args) {
		System.out.println("Create task "
				+ Collections.stringOfArray(args, ", ", "[", "]"));
		Task result_task;
		switch (task_type) {
		case TASK_COMPILE:
			result_task = new CompileDocument(args[0], args[1]);
			break;
		case TASK_INSTALL_PACKAGE:
			result_task = new InstallPackage(args);
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

	public void resetAndAdd(Task task) {
		task.reset();
		add(task);
	}

	public void resolve(Task task) {
		if (task != null && task.hasException()
				&& task.exception instanceof SolvableException
				&& ((SolvableException) task.exception).hasSolution()) {
			add(((SolvableException) task.exception).getSolution());
			task.reset();
			// re-queue task for retry
			// TODO set dependency: only start when the solution task completes
			resetAndAdd(task);
		}
	}

}
