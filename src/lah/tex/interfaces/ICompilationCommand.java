package lah.tex.interfaces;

import java.io.File;

public interface ICompilationCommand {

	String[] getCommand();

	File getDirectory();

	String getInputFileWithoutExt();

	String getOutputType();

}
