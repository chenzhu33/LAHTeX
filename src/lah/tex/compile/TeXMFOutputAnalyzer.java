package lah.tex.compile;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lah.tex.core.KpathseaException;
import lah.tex.core.TeXMFInputFileNotFoundException;
import lah.tex.interfaces.ICompilationResult;
import lah.utils.spectre.interfaces.ProgressListener;
import lah.utils.spectre.stream.IBufferProcessor;

class TeXMFOutputAnalyzer implements IBufferProcessor {

	@SuppressWarnings("unused")
	static private Pattern[] other_issue_patterns = {
			Pattern.compile("! (.*)"),
			Pattern.compile("(l\\.|line |lines )\\s*(\\d+)[^\\d].*"),
			Pattern.compile("(((! )?(La|pdf)TeX)|Package) .*Warning.*:(.*)"),
			Pattern.compile("(Over|Under)(full \\\\[hv]box .*)") };

	private StringBuilder accumulated_error_message;

	private String default_file_extension = "tex";

	private final Matcher kpathsea_matcher = Pattern.compile(
			"kpathsea: Running (.+)\\s*").matcher("");

	private StringBuilder output_buffer = new StringBuilder();

	private boolean pdftex_error = false;

	// Pattern for missing fonts, probably not necessary
	// Pattern.compile("! Font \\\\[^=]*=([^\\s]*)\\s"),
	// Pattern.compile("! Font [^\\n]*file\\:([^\\:\\n]*)\\:"),
	// Pattern.compile("! Font \\\\[^/]*/([^/]*)/")

	private final Matcher pdftex_error_end = Pattern.compile(
			" ==> Fatal error occurred, no output PDF file produced!").matcher(
			"");

	private final Matcher pdftex_error_start = Pattern.compile(
			"!pdfTeX error: .+").matcher("");

	private final Matcher pdftex_missing_file_matcher = Pattern.compile(
			"!pdfTeX error: .+ \\(file (.+)\\): .+").matcher("");

	ProgressListener<ICompilationResult> progress_listener;

	private Matcher single_line_matcher = Pattern.compile("(.+)\n").matcher("");

	private final Matcher[] tex_missing_file_matchers = {
			Pattern.compile("! LaTeX Error: File `([^`']*)' not found.*")
					.matcher(""),
			Pattern.compile("! I can't find file `([^`']*)'.*").matcher(""),
			Pattern.compile(
					"! Package fontenc Error: Encoding file `([^`']*)' not found.")
					.matcher(""),
			Pattern.compile(
					"! OOPS! I can't find any hyphenation patterns for US english.")
					.matcher("") };

	TeXMFResult texmf_result;

	public TeXMFResult getTeXMFResult() {
		if (texmf_result == null)
			texmf_result = new TeXMFResult();
		return texmf_result;
	}

	@Override
	public void processBuffer(byte[] buffer, int count) throws Exception {
		output_buffer.append(new String(buffer, 0, count));
		single_line_matcher.reset(output_buffer);
		String line;
		while (single_line_matcher.find()) {
			line = single_line_matcher.group(1);
			// System.out.println(line);

			// Get pdfTeX error: go to accumulation state
			if (pdftex_error_start.reset(line).matches()) {
				pdftex_error = true;
				accumulated_error_message = new StringBuilder(line);
			}

			if (pdftex_error) {
				// End of pdfTeX error: identify the missing file
				if (pdftex_error_end.reset(line).matches()) {
					// System.out.println("Accumulated pdfTeX error: "
					// + accumulated_error_message);
					pdftex_error = false;
					// now we parse the error for missing file
					if (pdftex_missing_file_matcher.reset(
							accumulated_error_message).matches()) {
						Exception e = new TeXMFInputFileNotFoundException(
								pdftex_missing_file_matcher.group(1),
								default_file_extension);
						texmf_result.setException(e);
						throw e;
					}
				} else {
					// continue accumulating the error
					accumulated_error_message.append(line);
					continue;
				}
			}

			// Get Kpathsea error
			if (kpathsea_matcher.reset(line).matches()) {
				Exception e = new KpathseaException(kpathsea_matcher.group(1));
				texmf_result.setException(e);
				throw e;
			}

			// Looking for missing TeX|MF files
			for (int i = 0; i < tex_missing_file_matchers.length; i++) {
				if (tex_missing_file_matchers[i].reset(line).matches()) {
					Exception e;
					if (i == 3)
						e = new TeXMFInputFileNotFoundException("hyphen.tex",
								default_file_extension);
					else
						e = new TeXMFInputFileNotFoundException(
								tex_missing_file_matchers[i].group(1),
								default_file_extension);
					texmf_result.setException(e);
					throw e;
				}
			}

			// Looking for other warning, error, badbox for the log & notify the
			// progress listener
			texmf_result.appendLog(line);
			output_buffer.delete(0, single_line_matcher.end());
		}

		// Notify the listener if we manage to process this buffer without
		// encountering any exception
		if (progress_listener != null) {
			texmf_result.setState(ICompilationResult.STATE_IN_PROGRESS);
			progress_listener.onProgress(texmf_result);
		}
	}

	public void reset() {
		output_buffer.delete(0, output_buffer.length());
		texmf_result = new TeXMFResult();
		texmf_result.setState(ICompilationResult.STATE_INIT);
		if (progress_listener != null)
			progress_listener.onProgress(texmf_result);
	}

	void setDefaultFileExtension(String ext) {
		default_file_extension = ext;
	}

	void setProgressListener(
			ProgressListener<ICompilationResult> progress_listener) {
		this.progress_listener = progress_listener;
	}

}
