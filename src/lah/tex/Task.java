package lah.tex;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentLinkedQueue;

import lah.spectre.interfaces.IResult;
import lah.spectre.multitask.TaskState;
import lah.spectre.process.TimedShell;
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

	protected static TeXMF task_manager;

	/**
	 * Check if a program exist
	 * 
	 * @param program
	 * @throws Exception
	 */
	protected static void checkProgram(String program) throws Exception {
		File program_file = new File(environment.getTeXMFBinaryDirectory(), program);
		if (program_file.exists()) {
			makeTEXMFCNF();
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
	private static void makeTEXMFCNF() throws Exception {
		File texmfcnf_file = new File(environment.getTeXMFBinaryDirectory() + "/texmf.cnf");
		if (!texmfcnf_file.exists()) {
			File texmfcnf_src = new File(environment.getTeXMFRootDirectory() + "/texmf/web2c/texmf.cnf");
			if (!texmfcnf_src.exists()) {
				throw new TeXMFFileNotFoundException("texmf.cnf", null);
			}
			BufferedReader reader = new BufferedReader(new FileReader(texmfcnf_src));
			FileWriter writer = new FileWriter(texmfcnf_file);
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.startsWith("TEXMFROOT"))
					writer.write("TEXMFROOT = " + environment.getTeXMFRootDirectory() + "\n");
				else if (line.startsWith("TEXMFVAR") && environment.isPortable())
					writer.write("TEXMFVAR = $TEXMFSYSVAR\n");
				else
					writer.write(line + "\n");
			}
			reader.close();
			writer.close();
		}
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
		ResourceBundle strings = ResourceBundle.getBundle("lah.tex.translate.strings", environment.getLocale());
		switch (state) {
		case PENDING:
			if (dependent_tasks.isEmpty())
				return strings.getString("pending");
			else
				return strings.getString("waiting_for_dependent_task");
		case EXECUTING:
			return strings.getString("executing");
		case COMPLETE:
			if (exception != null)
				return strings.getString("error_") + exception.getMessage();
			return strings.getString("complete_successfully");
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
				this.exception = new Exception("<" + exception.getMessage()
						+ "> is encountered again. Previous attempt to fix it probably failed.");
				return;
			}

			// Already at maximum tolerance
			solvable_exceptions.add((SolvableException) exception);
			if (solvable_exceptions.size() > MAX_NUM_SOLVABLE_EXCEPTIONS) {
				this.exception = new Exception("Too many exceptions!");
				return;
			}

			// Fix it if possible
			try {
				Task solution_task = ((SolvableException) exception).getSolution();
				// solution is available ==> mark dependency FIRST to prohibit this from being executed right away
				// and then submit the solution task
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
		// System.out.println(getDescription() + " : " + getStatusString());
	}

	@Override
	public String toString() {
		return "{" + getDescription() + "}";
	}

}
