package lah.tex;

import lah.spectre.interfaces.IFileSupplier;
import lah.spectre.interfaces.IResult;
import lah.spectre.process.TimedShell;
import lah.tex.exceptions.KpathseaException;
import lah.tex.exceptions.SystemFileNotFoundException;
import lah.tex.exceptions.TeXMFFileNotFoundException;
import lah.tex.interfaces.IEnvironment;
import lah.tex.manage.InstallationTask;

/**
 * Base class for a LAHTeX task.
 * 
 * @author L.A.H.
 * 
 */
public abstract class Task implements IResult, lah.spectre.multitask.Task {

	protected static IEnvironment environment;

	protected static IFileSupplier file_supplier;

	protected static TimedShell shell;

	protected Exception exception;

	private int num_exceptions_resolved;

	protected Task retry_task;

	protected int state;

	protected String status = "Pending";

	public boolean canResolve(Exception e) {
		return e != null
				&& (e instanceof KpathseaException
						|| e instanceof SystemFileNotFoundException || e instanceof TeXMFFileNotFoundException);
	}

	public String getDescription() {
		return null;
	}

	@Override
	public Exception getException() {
		return exception;
	}

	public int getNumberOfExceptionResolved() {
		return num_exceptions_resolved;
	}

	public Task getParentTask() {
		return retry_task;
	}

	public String getStatus() {
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
		// false to test!
		return true;
	}

	@Override
	public boolean isSuccessful() {
		// TODO Auto-generated method stub
		return false;
	}

	public void resetNumberOfExceptionsResolved() {
		num_exceptions_resolved = 0;
	}

	public Task resolve() {
		if (exception instanceof SystemFileNotFoundException) {
			return new InstallationTask(
					new String[] { ((SystemFileNotFoundException) exception)
							.getMissingSystemFile() });
		} else if (exception instanceof TeXMFFileNotFoundException
				&& ((TeXMFFileNotFoundException) exception).getMissingPackage() != null) {
			return new InstallationTask(
					((TeXMFFileNotFoundException) exception)
							.getMissingPackage());
		} else
			return null;
	}

	public void setException(Exception e) {
		exception = e;
	}

}
