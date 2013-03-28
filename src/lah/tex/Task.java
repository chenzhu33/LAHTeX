package lah.tex;

import java.util.LinkedList;

import lah.spectre.interfaces.IResult;
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

	public static enum State {
		/**
		 * Task is already completed
		 */
		STATE_COMPLETE,
		/**
		 * Task is executing i.e. run() is executed
		 */
		STATE_EXECUTING,
		/**
		 * Task is waiting for execution
		 */
		STATE_PENDING;
	}

	protected static IEnvironment environment;

	protected static MakeLSR make_lsr_task;

	/**
	 * The content of the text file "index", each line is of format
	 * {@code [package_name]/[file_1]/[file_2]/.../[file_n]/} where
	 * {@code [file_1], [file_2], ..., [file_n]} are all files contained in a
	 * package with name {@code [package_name]}.
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

	protected Exception exception;

	protected State state;

	protected Task() {
		state = State.STATE_PENDING;
	}

	public abstract String getDescription();

	@Override
	public Exception getException() {
		return exception;
	}

	public String getStatusString() {
		switch (state) {
		case STATE_PENDING:
			return "Pending";
		case STATE_EXECUTING:
			return "Executing ...";
		case STATE_COMPLETE:
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
	public boolean isComplete() {
		return state == State.STATE_COMPLETE;
	}

	@Override
	public boolean isExecutable() {
		// TODO implement accordingly
		return true;
	}

	@Override
	public boolean isSuccessful() {
		// TODO Auto-generated method stub
		return false;
	}

	/**
	 * Reset the state of this task for next execution. Subclasses SHOULD invoke
	 * reset() first when implementing run().
	 */
	public void reset() {
		this.exception = null;
		setState(State.STATE_PENDING);
	}

	/**
	 * Regenerate the ls-R files. This MUST be called lastly.
	 */
	protected void runFinalMakeLSR() {
		make_lsr_task.run();
		if (make_lsr_task.hasException())
			setException(make_lsr_task.getException());
		else
			setState(State.STATE_COMPLETE);
	}

	protected void setException(Exception exception) {
		this.exception = exception;
		this.state = State.STATE_COMPLETE;
		if (exception instanceof SolvableException) {
			try {
				SolvableException e = (SolvableException) exception;
				e.identifySolution();
				if (e.hasSolution()) {
					task_manager.add(e.getSolution());
					task_manager.add(this);
				}
			} catch (Exception e) {
				// TODO this potentially go into a loop so we need to bound the
				// recursion explicitly; for example, check if the exception is
				// already thrown earlier
				setException(e);
			}
		}
	}

	protected void setState(State state) {
		this.state = state;
	}

}
