package lah.tex.manage;

import java.io.File;
import java.io.FileOutputStream;

import lah.spectre.multitask.TaskState;
import lah.spectre.stream.StreamRedirector;
import lah.spectre.stream.Streams;
import lah.tex.Task;

/**
 * Task to generate Kpathsea's ls-R files
 * 
 * @author L.A.H.
 * 
 */
public class MakeLSR extends Task {

	/**
	 * Magic header of ls-R files
	 */
	private static final String lsR_magic = "% ls-R -- filename database for kpathsea; do not change this line.\n";

	/**
	 * The directories under tex_root to generate ls-R are
	 */
	private static final String[] texmf_dirs = { "texmf", "texmf-dist", "texmf-var" };

	private File[] texmf_dirs_files;

	public MakeLSR() {
		texmf_dirs_files = new File[texmf_dirs.length];
		for (int i = 0; i < texmf_dirs.length; i++) {
			texmf_dirs_files[i] = new File(environment.getTeXMFRootDirectory(), texmf_dirs[i] + "/");
		}
	}

	@Override
	public String getDescription() {
		return "Generate ls-R path databases";
	}

	@Override
	public void run() {
		reset();
		setState(TaskState.EXECUTING);
		for (int i = 0; i < texmf_dirs_files.length; i++) {
			// System.out.println("Make lsr in directory " + texmf_dirs[i]);
			File texmf_dir = texmf_dirs_files[i];
			// Skip non-existing texmf directory, is not a directory or
			// cannot read/write/execute: skip it
			if (!texmf_dir.exists() || (!(texmf_dir.isDirectory() && texmf_dir.canRead() && texmf_dir.canWrite())))
				// && texmf_dir.canExecute()
				continue;

			// Delete the ls-R before doing path generation
			File lsRfile = new File(texmf_dirs_files[i], "ls-R");
			if (lsRfile.exists() && lsRfile.isFile()) {
				// System.out.println("Delete " + lsRfile.getAbsolutePath());
				lsRfile.delete();
			}

			// Create a temporary ls-R file
			File temp_lsRfile = new File(environment.getTeXMFRootDirectory(), "ls-R");
			try {
				Streams.writeStringToFile(lsR_magic, temp_lsRfile, false);
				// Now do the "ls -R . >> ls-R" in the texmf root directory
				final FileOutputStream lsR_stream = new FileOutputStream(temp_lsRfile, true);
				shell.fork(new String[] { environment.getBusyBox(), "ls", "-R", "." }, texmf_dir, new StreamRedirector(
						lsR_stream), 600000);
				lsR_stream.close();
			} catch (Exception e) {
				temp_lsRfile.delete();
				setException(e);
				return;
			}

			// Move the temporary file to the intended location
			temp_lsRfile.renameTo(lsRfile);
		}
		setState(TaskState.COMPLETE);
	}
}
