package lah.tex.pkgman;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import lah.spectre.interfaces.IClient;
import lah.spectre.interfaces.IFileSupplier;
import lah.spectre.process.TimedShell;
import lah.spectre.stream.Streams;
import lah.tex.core.SystemFileNotFoundException;
import lah.tex.interfaces.IEnvironment;
import lah.tex.interfaces.IInstallationResult;
import lah.tex.interfaces.IInstaller;

public class Installer extends PkgManBase implements IInstaller {

	private static final String ARCH = "arm-android";

	public static final String PACKAGE_EXTENSION = ".tar.xz";

	private Map<String, String[]> depend;

	private TimedShell shell = new TimedShell();

	final Pattern single_space_pattern = Pattern.compile(" ");

	public Installer(IEnvironment environment) {
		super(environment);
	}

	/**
	 * Modify the list of packages to contain all dependent packages as well.
	 * 
	 * @param pkgs
	 * @throws FileNotFoundException
	 * @throws SystemFileNotFoundException
	 * @throws PackageDBNotReadyException
	 */
	private String[] addAllDependentPackages(String[] pkgs) throws Exception {
		// Queue containing the packages whose dependencies are to be added
		Queue<String> queue = new LinkedList<String>();

		// Set of all packages we have found, for efficient membership
		// testing to decide whether a package is already processed
		Set<String> found_packages = new TreeSet<String>();

		List<String> pkgs_to_install = new LinkedList<String>();

		// Initialize the queue & found packages with the input list
		for (String p : pkgs) {
			queue.add(p);
			found_packages.add(p);
			pkgs_to_install.add(p);
		}

		// Process until there is no more package to process i.e. we have
		// added all the necessary dependencies
		while (!queue.isEmpty()) {
			// Pick a pending package & add its dependency that has not been
			// added earlier
			String p = queue.poll();
			String[] pdepstr = getPackageDependency(p);
			if (pdepstr != null) {
				for (String k : pdepstr) {
					if (k.endsWith(".ARCH"))
						k = k.substring(0, k.length() - 4) + ARCH;
					if (!found_packages.contains(k)) {
						queue.add(k);
						found_packages.add(k);
						pkgs_to_install.add(k);
					}
				}
			}
		}
		return pkgs_to_install.toArray(new String[pkgs_to_install.size()]);
	}

	public Set<String> getInstalledPackages() {
		File tlpobj_dir = new File(environment.getTeXMFRootDirectory()
				+ "/tlpkg/tlpobj");
		if (!tlpobj_dir.exists())
			return null;

		Set<String> installed_packages = new TreeSet<String>();
		File[] tlpobj_files = tlpobj_dir.listFiles();
		final int suffix_length = ".tlpobj".length();
		for (File f : tlpobj_files) {
			String fname = f.getName();
			if (fname.endsWith(".tlpobj")) {
				installed_packages.add(fname.substring(0, fname.length()
						- suffix_length));
			}
		}
		return installed_packages;
	}

	private String[] getPackageDependency(String pkg_name) throws Exception {
		if (depend == null)
			loadDependMap();
		return depend.get(pkg_name);
	}

	public synchronized IInstallationResult install(
			IClient<IInstallationResult> client, IFileSupplier file_supplier,
			String[] package_names, boolean ignore_installed) {
		InstallationResult result = new InstallationResult();
		result.setRequestedPackages(package_names);

		// inform the listener that the result is initialized
		if (client != null)
			client.onServerReady(result);

		// copy xz, busybox, ... to private directory
		if (package_names.length == 1 && package_names[0].startsWith("/")) {
			return installSystemFile(package_names[0].substring(1),
					file_supplier);
		}

		String[] pkgs_to_install;
		try {
			pkgs_to_install = addAllDependentPackages(package_names);
		} catch (Exception e) {
			result.setException(e);
			return result;
		}
		result.setPendingPackages(pkgs_to_install);

		// FileRelocator relocator = new TDSFileLocator(texmf_root);
		for (int i = 0; i < pkgs_to_install.length; i++) {
			result.setPackageState(i, IInstallationResult.PACKAGE_INSTALLING);
			// TODO Fix this: return on failure to install requested package
			// only continue if some dependent package is missing
			try {
				String pkf_file_name = (pkgs_to_install[i].endsWith(".ARCH") ? pkgs_to_install[i]
						.substring(0, pkgs_to_install[i].length() - 5) + ARCH
						: pkgs_to_install[i])
						+ PACKAGE_EXTENSION;
				File pkg_file = file_supplier.getFile(pkf_file_name);
				if (pkg_file == null) {
					result.setPackageState(i, IInstallationResult.PACKAGE_FAIL);
					continue;
				}

				// decompress the package
				shell.fork(new String[] { environment.getXZ(), "-d", "-k",
						pkg_file.getName() }, pkg_file.getParentFile());

				// untar it to the tree if the tar file exists
				if (new File(pkg_file.getParentFile() + "/"
						+ pkgs_to_install[i] + ".tar").exists()) {
					shell.fork(new String[] { environment.getTAR(), "-xf",
							pkgs_to_install[i] + ".tar" },
							pkg_file.getParentFile());
					shell.fork(new String[] { environment.getRM(),
							pkgs_to_install[i] + ".tar" },
							pkg_file.getParentFile());
					result.setPackageState(i,
							IInstallationResult.PACKAGE_SUCCESSFULLY_INSTALLED);
				} else {
					result.setPackageState(i, IInstallationResult.PACKAGE_FAIL);
				}
			} catch (SystemFileNotFoundException e) {
				result.setException(e);
				return result;
			} catch (Exception e) {
				result.setPackageState(i, IInstallationResult.PACKAGE_FAIL);
			}
		}
		// post download, extract
		try {
			relocate();
			result.setState(IInstallationResult.STATE_INSTALLATION_FINISH);
		} catch (Exception e) {
			result.setException(e);
		}
		return result;
	}

	private IInstallationResult installSystemFile(String file_name,
			IFileSupplier file_supplier) {
		// String[] app_data_files = { "xz", "busybox", "desc", "depend",
		// "index", "dbkeys" };
		// String[] commands = { "cp", "ls", "tar", "chmod", "rm" };
		InstallationResult result = new InstallationResult();
		try {
			// Create necessary symbolic links for system commands
			if (file_name.equals("cp") || file_name.equals("ls")
					|| file_name.equals("tar") || file_name.equals("chmod")
					|| file_name.equals("rm")) {
				File bin_dir = new File(environment.getTeXMFBinaryDirectory());
				File cmdfile = new File(environment.getTeXMFBinaryDirectory()
						+ "/" + file_name);
				if (!cmdfile.exists()) {
					shell.fork(new String[] { environment.getBusyBox(), "ln",
							"-s", "busybox", file_name }, bin_dir);
				}
				cmdfile.setExecutable(true);
			} else {
				// Otherwise, the file is an app-data file which should be
				// obtained remotely
				File df = file_supplier.getFile(file_name
						+ (file_name.equals("xz") ? ".gz" : ".xz"));

				// unpack the *.xz files
				if (df.getName().endsWith(".xz"))
					df = xzdec(df);

				if (file_name.equals("xz") || file_name.equals("busybox")) {
					// Move binaries to the binary directory and set as
					// executable
					InputStream dfstr = new FileInputStream(df);
					if (file_name.equals("xz"))
						dfstr = new GZIPInputStream(dfstr);
					File target = new File(
							environment.getTeXMFBinaryDirectory() + "/"
									+ file_name);
					Streams.streamToFile(dfstr, target, true, false);
					target.setExecutable(true);
					df.delete();
				}
			}
			return null;
		} catch (Exception e) {
			result.setException(e);
			return result;
		}
	}

	private void loadDependMap() throws Exception {
		depend = new TreeMap<String, String[]>();
		String depend_content = Streams.readTextFile(environment
				.getPackageDependFile());
		Matcher matcher = line_pattern.matcher(depend_content);
		while (matcher.find()) {
			String p = matcher.group(1);
			String[] ds = single_space_pattern.split(matcher.group(2));
			depend.put(p, ds);
		}
	}

	private void relocate() throws Exception {
		File texmf_root_file = new File(environment.getTeXMFRootDirectory());
		if (!texmf_root_file.exists())
			return;

		File[] files = texmf_root_file.listFiles();
		if (files == null)
			return;

		for (File f : files) {
			if (f.isFile())
				continue;

			if (f.getName().equals("bin")) {
				shell.fork(
						new String[] {
								environment.getCP(),
								"-r",
								f.getName(),
								environment.getTeXMFBinaryDirectory()
										+ "/../../" }, f.getParentFile());
				shell.fork(
						new String[] { environment.getRM(), "-R", f.getName() },
						f.getParentFile());
				shell.fork(new String[] { environment.getCHMOD(), "-R", "700",
						"." }, new File(environment.getTeXMFBinaryDirectory()));
			} else if (!f.getName().equals("tlpkg")
					&& !f.getName().startsWith("texmf")
					&& !f.getName().startsWith("readme")) {
				File texmf_dist = new File(environment.getTeXMFRootDirectory()
						+ "/texmf-dist");
				if (!texmf_dist.exists())
					texmf_dist.mkdirs();
				// relocate the directory
				shell.fork(
						new String[] { environment.getCP(), "-R", "-f",
								f.getName(), texmf_dist.getAbsolutePath() + "/" },
						f.getParentFile());
				shell.fork(
						new String[] { environment.getRM(), "-R", f.getName() },
						f.getParentFile());
			}
		}
	}

	private File xzdec(File f) throws Exception {
		shell.fork(new String[] { environment.getXZ(), "-d", f.getName() },
				f.getParentFile());
		return new File(f.getParent() + "/"
				+ f.getName().substring(0, f.getName().length() - 3));
	}

}
