package lah.tex.manage;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import lah.spectre.stream.Streams;
import lah.tex.Task;

public class InstallSystemFile extends Task {

	private String system_file_name;

	public InstallSystemFile(String system_file_name) {
		this.system_file_name = system_file_name;
	}

	@Override
	public String getDescription() {
		return "Install system file " + system_file_name;
	}

	@Override
	public void run() {
		reset();
		try {
			setState(State.STATE_EXECUTING);
			File system_file = new File("/"); // file_supplier.getFile(system_file_name
												// + ".gz");
			InputStream file_stream = new GZIPInputStream(new FileInputStream(
					system_file));
			File target_file = new File(environment.getTeXMFBinaryDirectory()
					+ "/" + system_file_name);
			Streams.streamToFile(file_stream, target_file, true, false);
			// target_file.setExecutable(true);
			system_file.delete();
			setState(State.STATE_COMPLETE);
		} catch (Exception e) {
			setException(e);
			return;
		}
	}

}
