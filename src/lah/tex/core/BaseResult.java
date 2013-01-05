package lah.tex.core;

import lah.tex.interfaces.IResult;

public class BaseResult implements IResult {

	public static final int STATE_IN_PROGRESS = 0, STATE_EXCEPTION = -1,
			STATE_SUCCESS = 1;

	Exception exception;

	int state;

	public BaseResult() {
	}

	public BaseResult(Exception exception) {
		setException(exception);
	}

	@Override
	public Exception getException() {
		return exception;
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
	public boolean isSuccessful() {
		// TODO Auto-generated method stub
		return false;
	}

	public void setException(Exception e) {
		exception = e;
	}

}
