package lah.tex.exceptions;

import lah.tex.Task;

public class ExceptionResolutionTask extends Task {

	private Task target_task;

	public ExceptionResolutionTask(Task target_task) {
		this.target_task = target_task;
	}

	@Override
	public String getDescription() {
		return "Resolve exception for \"" + target_task.getDescription() + "\"";
	}

	@Override
	public void run() {
		setState(State.STATE_EXECUTING);
		if (target_task.getException() instanceof ResolvableException) {
			ResolvableException exception = (ResolvableException) target_task
					.getException();
			try {
				Task t = exception.getResolution();
				if (t != null)
					t.run();
			} catch (Exception e) {
				setException(e);
			}
		}
		setState(State.STATE_COMPLETE);
	}

}
