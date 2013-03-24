package lah.tex.manage;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import lah.spectre.stream.Streams;
import lah.tex.Task;

public class InstallSystemFile extends Task {

	private static final Pattern busybox_applets_pattern = Pattern
			.compile("cp|ls|tar|chmod|rm");

	private String system_file_name;

	public InstallSystemFile(String system_file_name) {
		this.system_file_name = system_file_name;
	}

	@Override
	public String getDescription() {
		return "Install base system";
	}

	@Override
	public void run() {
		reset();
		try {
			setState(State.STATE_EXECUTING);
			// Create necessary symbolic links for system commands
			if (busybox_applets_pattern.matcher(system_file_name).find()) {
				File cmdfile = new File(environment.getTeXMFBinaryDirectory()
						+ "/" + system_file_name);
				if (!cmdfile.exists()) {
					File bin_dir = new File(
							environment.getTeXMFBinaryDirectory());
					if (!cmdfile.exists()) {
						shell.fork(new String[] { environment.getBusyBox(),
								"ln", "-s", "busybox", system_file_name },
								bin_dir);
					}
					// TODO use this code for JDK1.7
					// Path applet_link = Paths.get(
					// environment.getTeXMFBinaryDirectory(),
					// system_file_name);
					// Path target = Paths.get(
					// environment.getTeXMFBinaryDirectory(), "busybox");
					// try {
					// Files.createSymbolicLink(applet_link, target);
					// } catch (Exception x) {
					// setException(x);
					// return;
					// }
				}
				cmdfile.setExecutable(true);
			} else {
				// Otherwise, the file is an app-data file which should be
				// obtained remotely
				File df = file_supplier.getFile(system_file_name
						+ (system_file_name.equals("xz") ? ".gz" : ".xz"));

				// unpack the *.xz files
				if (df.getName().endsWith(".xz"))
					df = xzdec(df, false);

				if (system_file_name.equals("xz")
						|| system_file_name.equals("busybox")) {
					// Move binaries to the binary directory and set as
					// executable
					InputStream dfstr = new FileInputStream(df);
					if (system_file_name.equals("xz"))
						dfstr = new GZIPInputStream(dfstr);
					File target = new File(
							environment.getTeXMFBinaryDirectory() + "/"
									+ system_file_name);
					Streams.streamToFile(dfstr, target, true, false);
					target.setExecutable(true);
					df.delete();
				}
			}
			setState(State.STATE_COMPLETE);
		} catch (Exception e) {
			setException(e);
			return;
		}
	}

}
