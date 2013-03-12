package lah.tex.compile;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lah.spectre.interfaces.IClient;
import lah.spectre.stream.IBufferProcessor;
import lah.tex.core.KpathseaException;
import lah.tex.core.TeXMFFileNotFoundException;
import lah.tex.interfaces.ICompilationResult;

class TeXMFOutputAnalyzer implements IBufferProcessor {

	@SuppressWarnings("unused")
	static private Pattern[] other_issue_patterns = {
			Pattern.compile("! (.*)"),
			Pattern.compile("(l\\.|line |lines )\\s*(\\d+)[^\\d].*"),
			Pattern.compile("(((! )?(La|pdf)TeX)|Package) .*Warning.*:(.*)"),
			Pattern.compile("(Over|Under)(full \\\\[hv]box .*)") };

	private StringBuilder accumulated_error_message;

	private IClient<ICompilationResult> client;

	String default_file_extension = "tex";

	private final Matcher kpathsea_matcher = Pattern.compile(
			"kpathsea: Running (.+)\\s*").matcher("");

	private StringBuilder output_buffer = new StringBuilder();

	// Pattern for missing fonts, probably not necessary
	// Pattern.compile("! Font \\\\[^=]*=([^\\s]*)\\s"),
	// Pattern.compile("! Font [^\\n]*file\\:([^\\:\\n]*)\\:"),
	// Pattern.compile("! Font \\\\[^/]*/([^/]*)/")

	private boolean pdftex_error = false;

	private final Matcher pdftex_error_end = Pattern.compile(
			" ==> Fatal error occurred, no output PDF file produced!").matcher(
			"");

	private final Matcher pdftex_error_start = Pattern.compile(
			"!pdfTeX error: .+").matcher("");

	private final Matcher pdftex_missing_file_matcher = Pattern.compile(
			"!pdfTeX error: .+ \\(file (.+)\\): .+").matcher("");

	private final Matcher single_line_matcher = Pattern.compile("(.+)\n")
			.matcher("");

	// for XeTeX
	private final Matcher[] tex_missing_file_matchers = {
			Pattern.compile("! LaTeX Error: File `([^`']*)' not found.*")
					.matcher(""),
			Pattern.compile("! I can't find file `([^`']*)'.*").matcher(""),
			Pattern.compile(
					"! Package fontenc Error: Encoding file `([^`']*)' not found.")
					.matcher(""),
			Pattern.compile(
					"Could not open config file \"(dvipdfmx\\.cfg)\"\\.")
					.matcher(""),
			Pattern.compile(
					"! OOPS! I can't find any hyphenation patterns for US english.")
					.matcher(""), };

	private CompilationTask texmf_result;

	public CompilationTask getTeXMFResult() {
		if (texmf_result == null)
			texmf_result = new CompilationTask();
		return texmf_result;
	}

	@Override
	public void processBuffer(byte[] buffer, int count) throws Exception {
		// reset if there is no result available
		if (texmf_result == null)
			reset();
		output_buffer.append(new String(buffer, 0, count));
		single_line_matcher.reset(output_buffer);
		String line;
		while (single_line_matcher.find()) {
			line = single_line_matcher.group(1);

			// Always append the log
			texmf_result.appendLog(line);
			output_buffer.delete(0, single_line_matcher.end());

			// Get pdfTeX error: go to accumulation state
			if (pdftex_error_start.reset(line).matches()) {
				pdftex_error = true;
				accumulated_error_message = new StringBuilder(line);
			}
			if (pdftex_error) {
				// End of pdfTeX error: identify the missing file
				if (pdftex_error_end.reset(line).matches()) {
					pdftex_error = false;
					// now we parse the error for missing file
					if (pdftex_missing_file_matcher.reset(
							accumulated_error_message).matches()) {
						throw new TeXMFFileNotFoundException(
								pdftex_missing_file_matcher.group(1),
								default_file_extension);
					}
				} else {
					// continue accumulating the error
					accumulated_error_message.append(line);
					continue;
				}
			}

			// Get Kpathsea error if any
			if (kpathsea_matcher.reset(line).matches())
				throw new KpathseaException(kpathsea_matcher.group(1));

			// Looking for missing TeX|MF files
			for (int i = 0; i < tex_missing_file_matchers.length; i++) {
				if (tex_missing_file_matchers[i].reset(line).find()) {
					if (i == tex_missing_file_matchers.length - 1)
						throw new TeXMFFileNotFoundException("hyphen.tex",
								default_file_extension);
					else
						throw new TeXMFFileNotFoundException(
								tex_missing_file_matchers[i].group(1),
								default_file_extension);
				}
			}
		}
	}

	public void reset() {
		output_buffer.delete(0, output_buffer.length());
		texmf_result = new CompilationTask();
		texmf_result.setState(ICompilationResult.STATE_INIT);
		if (client != null)
			client.onServerReady(texmf_result);
	}

	public void setClient(IClient<ICompilationResult> client) {
		this.client = client;
	}

	void setDefaultFileExtension(String ext) {
		default_file_extension = ext;
	}

}
