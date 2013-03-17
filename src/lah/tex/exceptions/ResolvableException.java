package lah.tex.exceptions;

import lah.tex.Task;

/**
 * Extension of {@link Exception} which can spawn task to fix itself
 * 
 * @author L.A.H.
 * 
 */
public abstract class ResolvableException extends Exception {

	private static final long serialVersionUID = 1L;

	/**
	 * Get a task to resolve this exception
	 * 
	 * @return A {@link Task} executing which probably solves this exception
	 *         (raised via some other task); return {@code null} if there is no
	 *         feasible resolution.
	 */
	public abstract Task getResolution() throws Exception;

}
