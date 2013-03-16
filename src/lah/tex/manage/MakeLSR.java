package lah.tex.manage;

import java.io.File;
import java.io.FileOutputStream;

import lah.spectre.stream.StreamRedirector;
import lah.spectre.stream.Streams;
import lah.tex.Task;

public class MakeLSR extends Task {

	private static final String lsR_magic = "% ls-R -- filename database for kpathsea; do not change this line.\n";

	@Override
	public void run() {
		// The directories under tex_root to generate ls-R are:
		final String[] texmfdir_names = { "texmf", "texmf-dist", "texmf-var" };

		for (int i = 0; i < texmfdir_names.length; i++) {
			File texmf_dir = new File(environment.getTeXMFRootDirectory() + "/"
					+ texmfdir_names[i]);

			// Skip non-existing texmf directory, is not a directory or
			// cannot read/write/execute: skip it
			if (!texmf_dir.exists()
					|| (!(texmf_dir.isDirectory() && texmf_dir.canRead()
							&& texmf_dir.canWrite() && texmf_dir.canExecute())))
				continue;

			// Delete the ls-R before doing path generation
			File lsRfile = new File(texmf_dir + "/ls-R");
			if (lsRfile.exists() && lsRfile.isFile()) {
				// System.out.println("Delete " + lsRfile.getAbsolutePath());
				lsRfile.delete();
			}

			// Create a temporary ls-R file
			File temp_lsRfile = new File(environment.getTeXMFRootDirectory()
					+ "/ls-R");
			try {
				Streams.writeStringToFile(lsR_magic, temp_lsRfile, false);
				// Now do the "ls -R . >> ls-R" in the texmf root directory
				final FileOutputStream stream = new FileOutputStream(
						temp_lsRfile, true);
				shell.fork(new String[] { environment.getLS(), "-R", "." },
						texmf_dir, new StreamRedirector(stream), 600000);
				stream.close();
			} catch (Exception e) {
				setException(e);
				return;
			}

			// Move the temporary file to the intended location
			temp_lsRfile.renameTo(lsRfile);
		}
	}
}
