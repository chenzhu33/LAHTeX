package lah.tex.exceptions;

import lah.tex.Task;

/**
 * Extension of {@link Exception} which can spawn task to fix itself
 * 
 * @author L.A.H.
 * 
 */
public abstract class SolvableException extends Exception {

	private static final long serialVersionUID = 1L;

	public abstract void identifySolution() throws Exception;

	/**
	 * Get a task to resolve this exception
	 * 
	 * @return A {@link Task} executing which probably solves this exception
	 *         (raised via some other task); return {@code null} if there is no
	 *         feasible resolution.
	 */
	public abstract Task getSolution() throws Exception;

	public abstract boolean hasSolution();

}
