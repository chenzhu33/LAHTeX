package lah.tex.manage;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lah.spectre.Collections;
import lah.spectre.stream.Streams;
import lah.tex.IEnvironment;
import lah.tex.Task;
import lah.tex.exceptions.SystemFileNotFoundException;

public class InstallPackage extends Task {

	public static enum PackageState {
		PACKAGE_FAIL, PACKAGE_INSTALLING, PACKAGE_SUCCESSFULLY_INSTALLED
	}

	/**
	 * Map each package to a list of packages it depends on
	 */
	private static Map<String, String[]> dependency_map;

	private static final Pattern line_pattern = Pattern
			.compile("([^ ]+) (.*)\n");

	/**
	 * File extension for TeX Live package
	 */
	public static final String PACKAGE_EXTENSION = ".tar.xz";

	private static final Pattern single_space_pattern = Pattern.compile(" ");

	/**
	 * RegEx pattern for the sub-directories in TEXMF_ROOT
	 */
	private static final Pattern texmf_subdir_patterns = Pattern
			.compile("texmf.*|readme.*|tlpkg");

	private int num_success_packages;

	private PackageState[] package_states;

	private String[] packages;

	private String[] pending_packages;

	public InstallPackage(String[] packages) {
		this.packages = packages;
	}

	/**
	 * Modify the list of packages to contain all dependent packages as well.
	 * 
	 * @param pkgs
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
		return "Install "
				+ Collections.stringOfArray(packages, " ", null, null);
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
		if (dependency_map == null)
			loadDependMap();
		return dependency_map.get(pkg_name);
	}

	public PackageState getPackageStatus(int position) {
		return package_states[position];
	}

	public String[] getPendingPackages() {
		return pending_packages;
	}

	public String[] getRequestedPackages() {
		return packages;
	}

	@Override
	public String getStatusString() {
		if (state == State.STATE_EXECUTING)
			return pending_packages == null ? "Computing dependency"
					: (num_success_packages + "/" + pending_packages.length + " packages installed");
		else
			return super.getStatusString();
	}

	private void loadDependMap() throws Exception {
		Map<String, String[]> temp_depend = new TreeMap<String, String[]>();
		// String depend_content = Streams.readTextFile(environment
		// .getPackageDependFile());
		String depend_content = environment
				.readDataFile(IEnvironment.LAHTEX_DEPEND);
		Matcher matcher = line_pattern.matcher(depend_content);
		while (matcher.find()) {
			String p = matcher.group(1);
			String[] ds = single_space_pattern.split(matcher.group(2));
			temp_depend.put(p, ds);
		}
		dependency_map = temp_depend;
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
		File texmf_dist = new File(environment.getTeXMFRootDirectory()
				+ "/texmf-dist");

		File[] files = texmf_root_file.listFiles();
		if (files == null)
			return;

		Matcher matcher = texmf_subdir_patterns.matcher("");

		for (File f : files) {
			if (f.isFile())
				continue;
			if (f.getName().equals("bin")) {
				shell.fork(new String[] { environment.getBusyBox(), "cp", "-r",
						f.getName(),
						environment.getTeXMFBinaryDirectory() + "/../../" },
						f.getParentFile());
				shell.fork(new String[] { environment.getBusyBox(), "rm", "-R",
						f.getName() }, f.getParentFile());
				shell.fork(new String[] { environment.getBusyBox(), "chmod",
						"-R", "700",
						environment.getTeXMFBinaryDirectory() + "/../../" },
						null);
			} else if (!matcher.reset(f.getName()).matches()) {
				// this directory should not be in TEXMF_ROOT, relocate it under
				// texmf-dist sub-directory
				if (!texmf_dist.exists())
					texmf_dist.mkdirs();
				shell.fork(
						new String[] { environment.getBusyBox(), "cp", "-R",
								"-f", f.getName(),
								texmf_dist.getAbsolutePath() + "/" },
						f.getParentFile());
				shell.fork(new String[] { environment.getBusyBox(), "rm", "-R",
						f.getName() }, f.getParentFile());
			}
		}
	}

	@Override
	public void run() {
		reset();
		setState(State.STATE_EXECUTING);

		String[] pkgs_to_install;
		try {
			pkgs_to_install = addAllDependentPackages(packages);
		} catch (Exception e) {
			setException(e);
			return;
		}
		setPendingPackages(pkgs_to_install);

		final String texmf_root = environment.getTeXMFRootDirectory();
		boolean has_lualibs = false;
		for (int i = 0; i < pkgs_to_install.length; i++) {
			setPackageState(i, PackageState.PACKAGE_INSTALLING);
			// TODO Fix this: return on failure to install requested package
			// only continue if some dependent package is missing
			try {
				// Retrieve the package
				String pkf_file_name = (pkgs_to_install[i].endsWith(".ARCH") ? pkgs_to_install[i]
						.substring(0, pkgs_to_install[i].length() - 5)
						+ environment.getArchitecture() : pkgs_to_install[i])
						+ PACKAGE_EXTENSION;
				File pkg_file = file_supplier.getFile(pkf_file_name);
				if (pkg_file == null) {
					setPackageState(i, PackageState.PACKAGE_FAIL);
					continue;
				}

				// Copy the package to TeXMF root (if necessary)
				if (!pkg_file.getParentFile().getAbsolutePath()
						.equals(texmf_root)) {
					File new_pkg_file = new File(texmf_root + "/"
							+ pkg_file.getName());
					Streams.streamToFile(new FileInputStream(pkg_file),
							new_pkg_file, true, false);
					pkg_file = new_pkg_file;
				}
				// Extract the package.tar.xz file
				shell.fork(new String[] { environment.getBusyBox(), "tar",
						"xf", pkg_file.getName() }, pkg_file.getParentFile());
				setPackageState(i, PackageState.PACKAGE_SUCCESSFULLY_INSTALLED);
				has_lualibs = has_lualibs
						|| pkgs_to_install[i].equals("lualibs");
			} catch (SystemFileNotFoundException e) {
				setException(e);
				return;
			} catch (Exception e) {
				setPackageState(i, PackageState.PACKAGE_FAIL);
			}
		}

		// Post download and extract packages
		try {
			relocate(); // relocate the files to the TeX directory structures
			if (has_lualibs)
				fixLualibsFile();
			runFinalMakeLSR(); // and also regenerate ls-R files
		} catch (Exception e) {
			setException(e);
		}
	}

	public void setPackageState(int package_id, PackageState package_state) {
		package_states[package_id] = package_state;
		if (package_state == PackageState.PACKAGE_SUCCESSFULLY_INSTALLED)
			num_success_packages++;
	}

	public void setPendingPackages(String[] packages) {
		if (packages != null) {
			// move to state installing packages
			num_success_packages = 0;
			pending_packages = packages;
			package_states = new PackageState[pending_packages.length];
		}
	}

}
