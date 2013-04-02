package lah.tex;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;

import lah.spectre.interfaces.IResult;
import lah.spectre.multitask.TaskState;
import lah.spectre.process.TimedShell;
import lah.tex.exceptions.SolvableException;
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
	 * The content of the text file "index", each line is of format
	 * {@code [package_name]/[file_1]/[file_2]/.../[file_n]/} where {@code [file_1], [file_2], ..., [file_n]} are all
	 * files contained in a package with name {@code [package_name]}.
	 */
	private static String package_file_index;

	protected static TimedShell shell;

	protected static TeXMF task_manager;

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

	private ConcurrentLinkedQueue<Task> dependent_tasks;

	protected Exception exception;

	private boolean is_successful;

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

	public String getStatusString() {
		switch (state) {
		case PENDING:
			if (dependent_tasks.isEmpty())
				return "Pending";
			else
				return "Waiting for dependent task";
		case EXECUTING:
			return "Executing ...";
		case COMPLETE:
			if (exception != null)
				return "Error: " + exception.getMessage();
			return "Complete successfully";
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
	public final boolean isSuccessful() {
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
			try {
				Task solution_task = ((SolvableException) exception).getSolution();
				// solution is available ==> mark dependency FIRST to prohibit this from being executed right away
				// and then submit the solution task
				if (solution_task != null) {
					addDependency(solution_task);
					task_manager.add(this, true);
					task_manager.add(solution_task, true, task_group);
				}
			} catch (Exception e) {
				// TODO this potentially go into a loop so we need to bound the recursion explicitly; for example, check
				// if the exception is already thrown earlier
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
