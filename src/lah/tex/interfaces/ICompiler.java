package lah.tex.interfaces;

import lah.spectre.interfaces.IClient;

public interface ICompiler {

	ICompilationResult compile(IClient<ICompilationResult> client,
			ICompilationCommand command, long timeout);

}
