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

	public static final int STATE_PENDING = 0, STATE_EXECUTING = 1,
			STATE_COMPLETE = 2;

	protected Exception exception;

	private int num_exceptions_resolved;

	protected int state;

	protected Task() {
		state = STATE_PENDING;
	}

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

	public String getStatus() {
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
		return state == STATE_COMPLETE;
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

	public void setException(Exception exception) {
		this.exception = exception;
		this.state = STATE_COMPLETE;
	}

	public void setState(int state) {
		this.state = state;
	}

}
