package lah.tex;

import lah.spectre.interfaces.IFileSupplier;
import lah.spectre.interfaces.IResult;
import lah.spectre.multitask.TaskManager;
import lah.spectre.process.TimedShell;
import lah.tex.interfaces.IEnvironment;

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

	protected static TaskManager<Task> manager;

	protected static TimedShell shell;

	protected Exception exception;

	private int num_exceptions_resolved;

	protected State state;

	protected Task() {
		state = State.STATE_PENDING;
	}

	public abstract String getDescription();

	@Override
	public Exception getException() {
		return exception;
	}

	public int getNumberOfExceptionResolved() {
		return num_exceptions_resolved;
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

	public void resetNumberOfExceptionsResolved() {
		num_exceptions_resolved = 0;
	}

	protected void setException(Exception exception) {
		this.exception = exception;
		this.state = State.STATE_COMPLETE;
	}

	protected void setState(State state) {
		this.state = state;
	}

}
