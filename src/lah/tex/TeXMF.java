package lah.tex;

import lah.spectre.multitask.TaskManager;
import lah.spectre.process.TimedShell;
import lah.tex.interfaces.IEnvironment;

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
	 * Make a task
	 * 
	 * @param task
	 * @return
	 */
	public Task createTask(String[] task) {
		if (task == null)
			return null;
		if (task[0].equals("lahtex-install")) {
			// install a new package
			return null;
		} else if (task[0].equals("lahtex-makefontconfig")) {

		} else if (task[0].equals("lahtex-makelangconfig")) {

		} else {
			// compile a file or make font cache fc-cache
		}
		return null;
	}

	public String[] getAllLanguages() throws Exception {
		return null;
	}

}
