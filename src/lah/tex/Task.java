package lah.tex;

import java.io.File;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentLinkedQueue;

import lah.spectre.interfaces.IResult;
import lah.spectre.multitask.TaskState;
import lah.spectre.process.TimedShell;
import lah.spectre.stream.Streams;
import lah.tex.exceptions.SolvableException;
import lah.tex.exceptions.TeXMFFileNotFoundException;
import lah.tex.manage.MakeLSR;

/**
 * Base class for a LAHTeX task.
 * 
 * @author L.A.H.
 * 
 */
public abstract class Task implements IResult, lah.spectre.multitask.Task {

	protected static IEnvironment environment;

	protected static MakeLSR make_lsr_task;

	/**
	 * Default maximum number of exceptions we are willing to fix
	 */
	private static final int MAX_NUM_SOLVABLE_EXCEPTIONS = 100;

	/**
	 * The content of the text file "index", each line is of format
	 * {@code [package_name]/[file_1]/[file_2]/.../[file_n]/} where {@code [file_1], [file_2], ..., [file_n]} are all
	 * files contained in a package with name {@code [package_name]}.
	 */
	private static String package_file_index;

	/**
	 * Single instance of {@link TimedShell} for command execution
	 */
	protected static TimedShell shell;

	public static ResourceBundle strings;

	protected static TeXMF task_manager;

	private static File texmfcnf_file, texmfcnf_src;

	/**
	 * Check if a program exist
	 * 
	 * @param program
	 * @throws Exception
	 */
	protected static void checkProgram(String program) throws Exception {
		File program_file = new File(environment.getTeXMFBinaryDirectory(), program);
		if (program_file.exists()) {
			prepareKpathseaConfigurationIfNecessary();
		} else {
			throw new TeXMFFileNotFoundException(program_file.getName(), null);
		}
	}

	/**
	 * Find all packages containing a file
	 * 
	 * @param file_query
	 *            File to search for
	 * @return List of names of packages containing file
	 * @throws Exception
	 */
	public static String[] findPackagesWithFile(String file_query) throws Exception {
		if (package_file_index == null) {
			package_file_index = environment.readLahTeXAsset(IEnvironment.LAHTEX_INDEX);
		}
		LinkedList<String> res = new LinkedList<String>();
		int k = 0;
		file_query = "/" + file_query + "/";
		while ((k = package_file_index.indexOf(file_query, k)) >= 0) {
			int j = k;
			while (j >= 0 && package_file_index.charAt(j) != '\n')
				j--;
			j++;
			int i = j;
			while (package_file_index.charAt(i) != '/')
				i++;
			k++;
			res.add(package_file_index.substring(j, i));
		}
		return res.size() > 0 ? res.toArray(new String[res.size()]) : null;
	}

	/**
	 * Generate configuration file texmf.cnf in the TeX binary directory reflecting the installation
	 * 
	 * @throws Exception
	 */
	private static void prepareKpathseaConfigurationIfNecessary() throws Exception {
		if (!texmfcnf_src.exists())
			throw new TeXMFFileNotFoundException("texmf.cnf", null);
		if (!texmfcnf_file.exists())
			Streams.writeStringToFile(
					"# This is TeXPortal generated version of texmf.cnf\n# Do not edit directly unless you know the consequences\n"
							+ "TEXMFROOT = " + environment.getTeXMFRootDirectory() + "\n"
							+ (environment.isPortable() ? "TEXMFVAR = $TEXMFSYSVAR\n" : ""), texmfcnf_file, false);
	}

	/**
	 * Set up environment variables such as PATH, TMPDIR and FONTCONFIG (for XeTeX to work), OSFONTDIR (for LuaTeX
	 * system font search) and TEXMFCNF (kpathsea path configuration search dirs)
	 */
	static void setupEnvironment() {
		String texmf_root = environment.getTeXMFRootDirectory();
		String texmf_bin = environment.getTeXMFBinaryDirectory();
		String path = texmf_bin + ":" + System.getenv("PATH");
		String tmpdir = texmf_root + "/texmf-var/tmp";
		new File(tmpdir + "/").mkdirs();
		String fontconfig_path = texmf_root + "/texmf-var/fonts/conf";
		new File(fontconfig_path + "/").mkdirs();
		shell.export("PATH", path);
		shell.export("TMPDIR", tmpdir);
		shell.export("FONTCONFIG_PATH", fontconfig_path);
		shell.export("OSFONTDIR", Task.environment.getOSFontsDirectory());
		shell.export("TEXMFCNF", texmf_root + "/texmf-var" + ":" + texmf_root + "/texmf/web2c");
		texmfcnf_file = new File(texmf_root + "/texmf-var/texmf.cnf");
		texmfcnf_src = new File(texmf_root + "/texmf/web2c/texmf.cnf");
		strings = ResourceBundle.getBundle("lah.tex.translate.strings", environment.getLocale());
	}

	private ConcurrentLinkedQueue<Task> dependent_tasks;

	protected Exception exception;

	private boolean is_successful;

	private List<SolvableException> solvable_exceptions = new LinkedList<SolvableException>();

	protected TaskState state;

	private TaskGroup task_group;

	protected Task() {
		state = TaskState.PENDING;
		dependent_tasks = new ConcurrentLinkedQueue<Task>();
	}

	private synchronized void addDependency(Task task) {
		dependent_tasks.add(task);
	}

	public abstract String getDescription();

	@Override
	public Exception getException() {
		return exception;
	}

	TaskGroup getGroup() {
		return task_group;
	}

	public String getStatusString() {
		switch (state) {
		case PENDING:
			if (dependent_tasks.isEmpty())
				return strings.getString("state_pending");
			else
				return strings.getString("state_waiting_for_dependency");
		case EXECUTING:
			return strings.getString("state_executing");
		case COMPLETE:
			if (exception != null)
				return MessageFormat.format(strings.getString("state_error_"), exception.getMessage());
			return strings.getString("state_complete_successfully");
		default:
			return null;
		}
	}

	@Override
	public boolean hasException() {
		return exception != null;
	}

	@Override
	public boolean isDone() {
		return state == TaskState.COMPLETE;
	}

	@Override
	public synchronized boolean isExecutable() {
		// Remove dependent tasks that have completed successfully
		Iterator<Task> iter = dependent_tasks.iterator();
		while (iter.hasNext()) {
			Task dep = iter.next();
			if (dep.isSuccessful())
				iter.remove();
		}
		// If there is no more dependency ==> ready!
		return dependent_tasks.isEmpty();
	}

	public boolean isMainTask() {
		return getGroup().getMainTask() == this;
	}

	@Override
	public boolean isSuccessful() {
		// return isComplete() && !hasException(); // TODO implement properly in each subclass
		return is_successful;
	}

	/**
	 * Reset the state of this task for next execution. Subclasses SHOULD invoke reset() first when implementing run().
	 */
	public void reset() {
		this.exception = null;
		this.is_successful = false;
		this.state = TaskState.PENDING;
		// if (solvable_exceptions.size() == MAX_NUM_SOLVABLE_EXCEPTIONS)
		// solvable_exceptions.clear();
	}

	/**
	 * Regenerate the ls-R files. This MUST be called lastly.
	 */
	protected void runFinalMakeLSR() {
		make_lsr_task.run();
		if (make_lsr_task.hasException())
			setException(make_lsr_task.getException());
		else
			setState(TaskState.COMPLETE);
	}

	/**
	 * Set the exception raised during execution i.e. run()
	 * 
	 * @param exception
	 */
	protected synchronized void setException(Exception exception) {
		this.exception = exception;
		this.state = TaskState.COMPLETE;
		this.is_successful = false;
		if (exception instanceof SolvableException) {
			// Exception is already encountered
			if (solvable_exceptions.contains(exception)) {
				this.exception = new Exception(MessageFormat.format(strings.getString("exception_encounter_again"),
						exception.getMessage()));
				return;
			}

			// Already at maximum tolerance
			solvable_exceptions.add((SolvableException) exception);
			if (solvable_exceptions.size() > MAX_NUM_SOLVABLE_EXCEPTIONS) {
				this.exception = new Exception(strings.getString("exception_too_many_exceptions"));
				return;
			}

			// Fix it if possible
			try {
				Task solution_task = ((SolvableException) exception).getSolution();
				// Solution is available ==> mark dependency FIRST to prohibit this from being executed right away
				// Then submit the solution task; this task should wait for the solution task to finish and then execute
				if (solution_task != null) {
					addDependency(solution_task);
					task_manager.enqueue(this);
					task_manager.add(solution_task, task_group);
				}
			} catch (Exception e) {
				setException(e);
			}
		}
	}

	void setGroup(TaskGroup group) {
		this.task_group = group;
	}

	protected synchronized void setState(TaskState state) {
		this.state = state;
		this.is_successful = (this.state == TaskState.COMPLETE && !hasException());
	}

	@Override
	public String toString() {
		return "{" + getDescription() + "}";
	}

}
