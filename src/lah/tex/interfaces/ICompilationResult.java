package lah.tex.interfaces;

import java.io.File;

import lah.spectre.interfaces.IResult;
import lah.tex.compile.LogLine;

/**
 * Interface to contain result of a compilation process
 */
public interface ICompilationResult extends IResult {

	public static final int STATE_INIT = 0, STATE_INPROGRESS = 1,
			STATE_COMPLETE = 2;

	LogLine getLogLine(int index);

	File getOutputFile();

	//String getOutputFileType();

}