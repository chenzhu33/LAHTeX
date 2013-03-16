package lah.tex.interfaces;

import java.io.File;

import lah.spectre.interfaces.IResult;
import lah.tex.compile.LogLine;

/**
 * Interface to contain result of a compilation process
 */
public interface ICompilationResult extends IResult {

	LogLine getLogLine(int index);

	File getOutputFile();

	// String getOutputFileType();

}