package lah.tex.interfaces;

import java.io.File;

import lah.tex.compile.LogLine;

/**
 * Interface to contain result of a compilation process
 */
public interface ICompilationResult extends IResult {

	public static final int STATE_INIT = 0, STATE_IN_PROGRESS = 1,
			STATE_COMPLETE = 2;

	ICompilationCommand getCompilationCommand();

	LogLine[] getLogLines(int start);

	File getOutputFile();

	String getOutputFileType();

	int getState();

}