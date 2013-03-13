package lah.tex.task;

import lah.spectre.interfaces.IResult;
import lah.spectre.multitask.Task;
import lah.spectre.process.TimedShell;
import lah.tex.exceptions.KpathseaException;
import lah.tex.exceptions.SystemFileNotFoundException;
import lah.tex.exceptions.TeXMFFileNotFoundException;
import lah.tex.interfaces.IEnvironment;

/**
 * Base class for a LAHTeX task.
 * 
 * @author L.A.H.
 * 
 */
public class BaseTask implements IResult, Task {

	public static final int STATE_IN_PROGRESS = 0, STATE_EXCEPTION = -1,
			STATE_SUCCESS = 1;

	protected IEnvironment environment;

	Exception exception;

	private int num_exceptions_resolved;

	private BaseTask retry_task;

	protected TimedShell shell;

	int state;

	protected String status = "Pending";

	public BaseTask() {
	}

	public BaseTask(Exception exception) {
		setException(exception);
	}

	public boolean canResolve(Exception e) {
		return e != null
				&& (e instanceof KpathseaException
						|| e instanceof SystemFileNotFoundException || e instanceof TeXMFFileNotFoundException);
	}

	public CharSequence getDescription() {
		return null;
	}

	@Override
	public Exception getException() {
		return exception;
	}

	public int getNumberOfExceptionResolved() {
		return num_exceptions_resolved;
	}

	public BaseTask getParentTask() {
		return retry_task;
	}

	public CharSequence getStatus() {
		return status;
	}

	@Override
	public boolean hasException() {
		return (exception != null);
	}

	@Override
	public boolean isComplete() {
		// TODO Auto-generated method stub
		return false;
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
	 * Identify the missing package from the exception (if any) raised in a
	 * result
	 * 
	 * @param result
	 */
	@SuppressWarnings("unused")
	private void postProcessResult(IResult result) {
		if (result != null && result.hasException()
				&& result.getException() instanceof TeXMFFileNotFoundException) {
			// try {
			// ((TeXMFFileNotFoundException) result.getException())
			// .identifyMissingPackage(seeker);
			// } catch (Exception e) {
			// result.setException(e);
			// }
		}
	}

	public void resetNumberOfExceptionsResolved() {
		num_exceptions_resolved = 0;
	}

	public void resolve(int id, Exception exception) {
		if (id != 0) {
			// BaseTask t = task_manager.get(id);
			// assert (t != null);
			// t.resetNumberOfExceptionsResolved();
			// exception = t.getResult().getException();
		}
		if (exception instanceof SystemFileNotFoundException) {
			// install(new String[] { ((SystemFileNotFoundException) exception)
			// .getMissingSystemFile() },
			// id);
		} else if (exception instanceof TeXMFFileNotFoundException
				&& ((TeXMFFileNotFoundException) exception).getMissingPackage() != null) {
			// install(((TeXMFFileNotFoundException) exception)
			// .getMissingPackage(),
			// id);
		} else if (id != 0) {
			// System.out.println("Encounter unresolvable exception:");
			// exception.printStackTrace(System.out);
			// deregister unresolvable tasks
			// task_manager.deregisterTask(id);
		}
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
	}

	public void setException(Exception e) {
		exception = e;
	}

}
