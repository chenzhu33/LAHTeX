package lah.tex.core;

import java.io.File;

import lah.spectre.FileName;
import lah.tex.interfaces.ICompilationCommand;

public class CompilationCommand implements ICompilationCommand {

	public static String getProgramFromFormat(String format) {
		if (format.startsWith("pdf"))
			return "pdftex";
		else if (format.startsWith("xe"))
			return "xetex";
		else if (format.startsWith("lua"))
			return "luatex";
		else
			return "tex";
	}

	File directory;

	String engine;

	File input_file;

	String input_file_no_ext;

	public CompilationCommand(String engine, File input) {
		this.engine = engine;
		input_file = input;
		directory = input_file.getParentFile();
	}

	public CompilationCommand(String[] command) {
		this(command[0], new File(command[1]));
	}

	@Override
	public String[] getCommand() {
		input_file_no_ext = FileName.removeFileExtension(input_file.getName());
		if (engine.equals("bibtex") || engine.equals("makeindex"))
			return new String[] { engine, input_file_no_ext };
		else {
			String tex_fmt = engine.equals("pdftex") ? "pdfetex" : engine;
			return new String[] { getProgramFromFormat(engine),
					"-interaction=nonstopmode", "-fmt=" + tex_fmt,
					input_file.getName() };
		}
	}

	@Override
	public File getDirectory() {
		return directory;
	}

	@Override
	public String getInputFileWithoutExt() {
		return input_file_no_ext;
	}

	@Override
	public String getOutputType() {
		if (engine.equals("pdftex") || engine.equals("pdflatex"))
			return "pdf";
		else if (engine.equals("tex") || engine.equals("latex"))
			return "dvi";
		else if (engine.equals("bibtex"))
			return "bbl";
		else if (engine.equals("makeindex"))
			return "idx";
		return null;
	}
}
