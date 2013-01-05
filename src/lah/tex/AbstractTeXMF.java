package lah.tex;

import lah.tex.interfaces.ICompiler;
import lah.tex.interfaces.IEnvironment;
import lah.tex.interfaces.IInstaller;
import lah.tex.interfaces.ILoader;
import lah.tex.interfaces.ISeeker;

public abstract class AbstractTeXMF implements ICompiler, IInstaller, ISeeker,
		ILoader {

	private static AbstractTeXMF texmf_instance;

	public static final AbstractTeXMF getInstance(IEnvironment environment) {
		if (texmf_instance == null)
			texmf_instance = new TeXMF(environment);
		return texmf_instance;
	}

}
