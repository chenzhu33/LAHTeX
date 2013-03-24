package lah.tex;

import java.io.File;
import java.util.LinkedList;

import lah.spectre.interfaces.IFileSupplier;
import lah.spectre.interfaces.IResult;
import lah.spectre.multitask.TaskManager;
import lah.spectre.process.TimedShell;
import lah.spectre.stream.Streams;
import lah.tex.exceptions.SolvableException;
import lah.tex.interfaces.IEnvironment;
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

	protected static IFileSupplier file_supplier;

	protected static MakeLSR make_lsr_task;

	protected static TaskManager<Task> manager;

	/**
	 * The content of the text file "index", each line is of format
	 * {@code [package_name]/[file_1]/[file_2]/.../[file_n]/} where
	 * {@code [file_1], [file_2], ..., [file_n]} are all files contained in a
	 * package with name {@code [package_name]}.
	 */
	private static String package_file_index;

	protected static TimedShell shell;

	/**
	 * Find all packages containing a file
	 * 
	 * @param file_query
	 *            File to search for
	 * @return List of names of packages containing file
	 * @throws Exception
	 */
	public static String[] findPackagesWithFile(String file_query)
			throws Exception {
		if (package_file_index == null) {
			String temp_index = Streams.readTextFile(environment
					.getPackageIndexFile());
			package_file_index = temp_index;
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
	 * Decompress a XZ file, return the resulting file
	 * 
	 * @param xz_file
	 *            The XZ file to decompress, must have extension ".xz"
	 * @return The resulting {@link File} of the decompression or
	 *         {@literal null} if input file is invalid or no result could be
	 *         produced (for example, xz process fails).
	 * @throws Exception
	 */
	public static File xzdec(File xz_file, boolean keep_input) throws Exception {
		if (xz_file != null && xz_file.getName().endsWith(".xz")) {
			String[] xz_cmd = new String[keep_input ? 4 : 3];
			xz_cmd[0] = environment.getXZ();
			xz_cmd[1] = "-d";
			xz_cmd[xz_cmd.length - 1] = xz_file.getName();
			if (keep_input)
				xz_cmd[2] = "-k";
			shell.fork(xz_cmd, xz_file.getParentFile());
			File result = new File(xz_file.getParent()
					+ "/"
					+ xz_file.getName().substring(0,
							xz_file.getName().length() - 3));
			return (result.exists() ? result : null);
		}
		return null;
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
		// false to test!
		return true;
	}

	@Override
	public boolean isSuccessful() {
		// TODO Auto-generated method stub
		return false;
	}

	public void reset() {
		this.exception = null;
		setState(State.STATE_PENDING);
	}

	protected void setException(Exception exception) {
		this.exception = exception;
		this.state = State.STATE_COMPLETE;
		if (exception instanceof SolvableException) {
			try {
				((SolvableException) exception).identifySolution();
			} catch (Exception e) {
				setException(e);
			}
		}
	}
	
	protected void setState(State state) {
		this.state = state;
	}

}
