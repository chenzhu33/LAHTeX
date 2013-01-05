package lah.tex.interfaces;

import lah.utils.spectre.interfaces.ProgressListener;

public interface ICompiler {

	ICompilationResult compile(
			ProgressListener<ICompilationResult> progress_listener,
			ICompilationCommand command);

	void updateFontMap() throws Exception;

}
