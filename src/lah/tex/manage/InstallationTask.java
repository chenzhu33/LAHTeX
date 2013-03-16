package lah.tex.manage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
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

import lah.spectre.Collections;
import lah.spectre.interfaces.IFileSupplier;
import lah.spectre.process.TimedShell;
import lah.spectre.stream.Streams;
import lah.tex.Task;
import lah.tex.exceptions.SystemFileNotFoundException;
import lah.tex.interfaces.IInstallationResult;

public class InstallationTask extends Task implements IInstallationResult {

	/**
	 * Map each package to a list of packages it depends on
	 */
	private static Map<String, String[]> depend;

	private static final String PACKAGE_EXTENSION = ".tar.xz";

	/**
	 * RegEx pattern for the sub-directories in TEXMF_ROOT
	 */
	private static final Pattern texmf_subdir_patterns = Pattern
			.compile("texmf.*|readme.*|tlpkg");

	private int installation_state;

	final Pattern line_pattern = Pattern.compile("([^ ]+) (.*)\n");

	private int num_success_packages;

	private IFileSupplier package_file_supplier;

	private String[] package_names;

	private int[] package_states;

	private String[] packages;

	private String[] pending_packages;

	private String[] requested_packages;

	protected TimedShell shell = new TimedShell();

	final Pattern single_space_pattern = Pattern.compile(" ");

	private File texmf_dist;

	public InstallationTask(String[] packages) {
		this.packages = packages;
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
						k = k.substring(0, k.length() - 4)
								+ environment.getArchitecture();
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

	/**
	 * Fix lualibs-file.lua function iswritable and isreadable
	 */
	private void fixLualibsFile() {
		File lualibs_file_lua = new File(environment.getTeXMFRootDirectory()
				+ "/texmf-dist/tex/luatex/lualibs/lualibs-file.lua");
		if (lualibs_file_lua.exists()) {
			try {
				String content = Streams.readTextFile(lualibs_file_lua);
				content = Pattern
						.compile(
								"return a and sub(a.permissions,1,1) == \"r\"",
								Pattern.LITERAL).matcher(content)
						.replaceFirst("return a");
				content = Pattern
						.compile(
								"return a and sub(a.permissions,2,2) == \"w\"",
								Pattern.LITERAL).matcher(content)
						.replaceFirst("return a");
				Streams.writeStringToFile(content, lualibs_file_lua, false);
			} catch (IOException e) {
				System.out.println("Error modifying lualibs-file.lua");
			}
		}
	}

	@Override
	public String getDescription() {
		return "Install " + Collections.stringOfArray(packages, " ", null, null);
	}

	@Override
	public int getInstallationState() {
		return installation_state;
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

	@Override
	public int getPackageStatus(int position) {
		return package_states[position];
	}

	@Override
	public String[] getPendingPackages() {
		return pending_packages;
	}

	@Override
	public String[] getRequestedPackages() {
		return requested_packages;
	}

	private void installSystemFile(String file_name, IFileSupplier file_supplier) {
		// String[] app_data_files = { "xz", "busybox", "desc", "depend",
		// "index", "dbkeys" };
		// String[] commands = { "cp", "ls", "tar", "chmod", "rm" };
		// InstallationTask result = new InstallationTask();
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
					df = xzdec(df, false);

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
			// return null;
		} catch (Exception e) {
			// result.
			setException(e);
			// return result;
		}
	}

	private void loadDependMap() throws Exception {
		Map<String, String[]> temp_depend = new TreeMap<String, String[]>();
		String depend_content = Streams.readTextFile(environment
				.getPackageDependFile());
		Matcher matcher = line_pattern.matcher(depend_content);
		while (matcher.find()) {
			String p = matcher.group(1);
			String[] ds = single_space_pattern.split(matcher.group(2));
			temp_depend.put(p, ds);
		}
		depend = temp_depend;
	}

	/**
	 * Relocate the extracted files and directories into the standard TeX
	 * directory structure (i.e. texmf and texmf-dist)
	 * 
	 * @throws Exception
	 */
	private void relocate() throws Exception {
		File texmf_root_file = new File(environment.getTeXMFRootDirectory());
		if (!texmf_root_file.exists())
			return;

		File[] files = texmf_root_file.listFiles();
		if (files == null)
			return;

		Matcher matcher = texmf_subdir_patterns.matcher("");

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
						environment.getTeXMFBinaryDirectory() + "/../../" },
						null);
			} else if (!matcher.reset(f.getName()).matches()) {
				// this directory should not be in TEXMF_ROOT, relocate it under
				// texmf-dist sub-directory
				if (!texmf_dist.exists())
					texmf_dist.mkdirs();
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

	@Override
	public void run() {
		status = "Executing";
		setRequestedPackages(package_names);

		// copy xz, busybox, ... to private directory
		if (package_names.length == 1 && package_names[0].startsWith("/")) {
			installSystemFile(package_names[0].substring(1),
					package_file_supplier);
			return;
		}

		String[] pkgs_to_install;
		try {
			pkgs_to_install = addAllDependentPackages(package_names);
		} catch (Exception e) {
			setException(e);
			return;
		}
		setPendingPackages(pkgs_to_install);

		final String texmf_root = environment.getTeXMFRootDirectory();
		boolean has_lualibs = false;
		for (int i = 0; i < pkgs_to_install.length; i++) {
			setPackageState(i, IInstallationResult.PACKAGE_INSTALLING);
			// TODO Fix this: return on failure to install requested package
			// only continue if some dependent package is missing
			try {
				// Retrieve the package
				String pkf_file_name = (pkgs_to_install[i].endsWith(".ARCH") ? pkgs_to_install[i]
						.substring(0, pkgs_to_install[i].length() - 5)
						+ environment.getArchitecture() : pkgs_to_install[i])
						+ PACKAGE_EXTENSION;
				File pkg_file = package_file_supplier.getFile(pkf_file_name);
				if (pkg_file == null) {
					setPackageState(i, IInstallationResult.PACKAGE_FAIL);
					continue;
				}

				// Copy the package to TeX root (if necessary)
				if (!pkg_file.getParentFile().getAbsolutePath()
						.equals(texmf_root)) {
					File new_pkg_file = new File(texmf_root + "/"
							+ pkg_file.getName());
					Streams.streamToFile(new FileInputStream(pkg_file),
							new_pkg_file, true, false);
					pkg_file = new_pkg_file;
				}

				// Decompress the package, assuming that the file is in root
				shell.fork(new String[] { environment.getXZ(), "-d", "-k",
						pkg_file.getName() }, pkg_file.getParentFile());

				// xzdec(pkg_file, true);

				// Untar the decompressed package to the tree if it exists
				if (new File(pkg_file.getParentFile() + "/"
						+ pkgs_to_install[i] + ".tar").exists()) {
					shell.fork(new String[] { environment.getTAR(), "-xf",
							pkgs_to_install[i] + ".tar" },
							pkg_file.getParentFile());
					shell.fork(new String[] { environment.getRM(),
							pkgs_to_install[i] + ".tar" },
							pkg_file.getParentFile());
					setPackageState(i,
							IInstallationResult.PACKAGE_SUCCESSFULLY_INSTALLED);
				} else {
					setPackageState(i, IInstallationResult.PACKAGE_FAIL);
				}

				has_lualibs = has_lualibs
						|| pkgs_to_install[i].equals("lualibs");
			} catch (SystemFileNotFoundException e) {
				setException(e);
				return;
			} catch (Exception e) {
				setPackageState(i, IInstallationResult.PACKAGE_FAIL);
			}
		}

		// Post download and extract packages
		try {
			relocate(); // relocate the files to the TeX directory structures
			// makeLSR(null); // and also regenerate ls-R files
			if (has_lualibs)
				fixLualibsFile();
			setState(IInstallationResult.STATE_INSTALLATION_FINISH);
		} catch (Exception e) {
			setException(e);
		}
		status = "Complete";
	}

	@Override
	public void setException(Exception e) {
		super.setException(e);
		setState(STATE_INSTALLATION_ERROR);
	}

	public void setPackageState(int package_id, int package_state) {
		package_states[package_id] = package_state;
		if (package_state == PACKAGE_SUCCESSFULLY_INSTALLED)
			num_success_packages++;
	}

	public void setPendingPackages(String[] packages) {
		if (packages != null) {
			// move to state installing packages
			setState(STATE_INSTALLING_PACKAGES);
			num_success_packages = 0;
			pending_packages = packages;
			package_states = new int[pending_packages.length];
		}
	}

	public void setRequestedPackages(String[] req_pkgs) {
		requested_packages = req_pkgs;
	}

	public void setState(int state) {
		installation_state = state;
	}

	/**
	 * Decompress a XZ file, return the resulting file
	 * 
	 * @param xz_file
	 *            The XZ file to decompress, must have extension ".xz"
	 * @return The resulting {@link File} of the decompression or
	 *         {@literal null} if input file is invalid or no result could be
	 *         produced (for example, xz process fails).
	 * @throws Exception
	 */
	private File xzdec(File xz_file, boolean keep_input) throws Exception {
		if (xz_file != null && xz_file.getName().endsWith(".xz")) {
			String[] xz_cmd = new String[keep_input ? 4 : 3];
			xz_cmd[0] = environment.getXZ();
			xz_cmd[1] = "-d";
			xz_cmd[xz_cmd.length - 1] = xz_file.getName();
			if (keep_input)
				xz_cmd[2] = "-k";
			shell.fork(xz_cmd, xz_file.getParentFile());
			File result = new File(xz_file.getParent()
					+ "/"
					+ xz_file.getName().substring(0,
							xz_file.getName().length() - 3));
			return (result.exists() ? result : null);
		}
		return null;
	}
	
	@Override
	public String getStatus() {
		return num_success_packages
				+ (pending_packages != null ? "/" + pending_packages.length
						: "") + " packages successfully installed.";
	}

}
