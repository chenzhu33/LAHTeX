package lah.tex;

import lah.spectre.Collections;
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

	private static TeXMF texmf_instance;

	public static final TeXMF getInstance(IEnvironment environment) {
		if (texmf_instance == null)
			texmf_instance = new TeXMF(environment);
		return texmf_instance;
	}

	private TeXMF(IEnvironment environment) {
		Task.environment = environment;
		Task.shell = new TimedShell();
	}

	/**
	 * Create a new task and submit for scheduled execution
	 * 
	 * @param args
	 * @return the created task
	 */
	public Task createTask(String[] args) {
		System.out.print("Create task "
				+ Collections.stringOfArray(args, ", ", "[", "]"));
		if (args == null)
			return null;
		Task result_task;
		if (args[0].equals("lahtex-install")) {
			// install a new package
			String[] packages_to_install = new String[args.length - 1];
			System.arraycopy(args, 0, packages_to_install, 0, args.length - 1);
			result_task = new InstallationTask(packages_to_install);
		} else if (args[0].equals("lahtex-makefontconfig")) {
			result_task = new MakeFontConfigurations();
		} else if (args[0].equals("lahtex-makelangconfig")) {
			String[] languages = new String[args.length - 1];
			System.arraycopy(args, 0, languages, 0, args.length - 1);
			result_task = new MakeLanguageConfigurations(languages);
		} else {
			// compile a file or make font cache fc-cache
			result_task = new CompilationTask(args[0], args[1]);
		}
		addTask(result_task);
		return result_task;
	}

	public String[] getAllLanguages() throws Exception {
		return null;
	}

}
