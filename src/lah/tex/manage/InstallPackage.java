package lah.tex.manage;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.MessageFormat;
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
import lah.spectre.multitask.TaskState;
import lah.spectre.stream.Streams;
import lah.tex.IEnvironment;
import lah.tex.Task;
import lah.tex.exceptions.SystemFileNotFoundException;

/**
 * Task to install a TeX Live package
 * 
 * @author L.A.H.
 * 
 */
public class InstallPackage extends Task {

	public static enum PackageState {
		PACKAGE_FAIL, PACKAGE_INSTALLING, PACKAGE_SUCCESSFULLY_INSTALLED
	}

	/**
	 * Map each package to a list of packages it depends on
	 */
	private static Map<String, String[]> dependency_map;

	private static final Pattern line_pattern = Pattern.compile("([^ ]+) (.*)\n");

	/**
	 * File extension for TeX Live package
	 */
	public static final String PACKAGE_EXTENSION = ".tar.xz";

	private static final Pattern single_space_pattern = Pattern.compile(" ");

	/**
	 * RegEx pattern for the sub-directories in TEXMF_ROOT
	 */
	private static final Pattern texmf_subdir_patterns = Pattern.compile("texmf.*|readme.*|tlpkg");

	private static void loadDependMap() throws Exception {
		Map<String, String[]> temp_depend = new TreeMap<String, String[]>();
		String depend_content = environment.readLahTeXAsset(IEnvironment.LAHTEX_DEPEND);
		Matcher matcher = line_pattern.matcher(depend_content);
		while (matcher.find()) {
			String p = matcher.group(1);
			String[] ds = single_space_pattern.split(matcher.group(2));
			temp_depend.put(p, ds);
		}
		dependency_map = temp_depend;
	}

	private int num_success_packages;

	private PackageState[] package_states;

	private String[] packages;

	private String[] pending_packages;

	public InstallPackage(String[] packages) {
		this.packages = packages;
	}

	/**
	 * Extend a list of packages to contain all dependent packages as well
	 * 
	 * @param initial_packages
	 */
	private String[] addAllDependentPackages(String[] initial_packages) throws Exception {
		if (dependency_map == null)
			loadDependMap();

		// Queue containing the packages whose dependencies are to be added
		Queue<String> queue = new LinkedList<String>();

		// Set of all packages found, for efficient membership testing to decide whether a package is already processed
		Set<String> found_packages = new TreeSet<String>();

		List<String> pkgs_to_install = new LinkedList<String>();

		// Initialize the queue & found packages with the input list
		for (String pkg : initial_packages) {
			queue.add(pkg);
			found_packages.add(pkg);
			pkgs_to_install.add(pkg);
		}

		// Process until there is no more package to process i.e. we have added all the necessary dependencies
		while (!queue.isEmpty()) {
			// Pick a pending package & add its dependency that has not been added earlier
			String pkg = queue.poll();
			String[] pkg_deps = dependency_map.get(pkg);
			if (pkg_deps != null) {
				for (String d : pkg_deps) {
					if (d.endsWith(".ARCH"))
						d = d.substring(0, d.length() - 4) + environment.getArchitecture();
					if (!found_packages.contains(d)) {
						queue.add(d);
						found_packages.add(d);
						pkgs_to_install.add(d);
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
				content = Pattern.compile("return a and sub(a.permissions,1,1) == \"r\"", Pattern.LITERAL)
						.matcher(content).replaceFirst("return a");
				content = Pattern.compile("return a and sub(a.permissions,2,2) == \"w\"", Pattern.LITERAL)
						.matcher(content).replaceFirst("return a");
				Streams.writeStringToFile(content, lualibs_file_lua, false);
			} catch (IOException e) {
				// System.out.println("Error modifying lualibs-file.lua");
			}
		}
	}

	@Override
	public String getDescription() {
		return strings.getString("install_") + Collections.stringOfArray(packages, " ", null, null);
	}

	public Set<String> getInstalledPackages() {
		File tlpobj_dir = new File(environment.getTeXMFRootDirectory() + "/tlpkg/tlpobj");
		if (!tlpobj_dir.exists())
			return null;

		Set<String> installed_packages = new TreeSet<String>();
		File[] tlpobj_files = tlpobj_dir.listFiles();
		final int suffix_length = ".tlpobj".length();
		for (File f : tlpobj_files) {
			String fname = f.getName();
			if (fname.endsWith(".tlpobj")) {
				installed_packages.add(fname.substring(0, fname.length() - suffix_length));
			}
		}
		return installed_packages;
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
		if (state == TaskState.EXECUTING) {
			if (pending_packages == null)
				return strings.getString("computing_dependency");
			else
				return MessageFormat.format(strings.getString("packages_installed"), num_success_packages,
						pending_packages.length);
		} else
			return super.getStatusString();
	}

	/**
	 * Relocate the extracted files and directories into the standard TeX directory structure (i.e. texmf and
	 * texmf-dist)
	 * 
	 * @throws Exception
	 */
	private void relocate() throws Exception {
		File texmf_root_file = new File(environment.getTeXMFRootDirectory());
		if (!texmf_root_file.exists())
			return;
		File texmf_dist = new File(environment.getTeXMFRootDirectory() + "/texmf-dist");

		File[] files = texmf_root_file.listFiles();
		if (files == null)
			return;

		Matcher matcher = texmf_subdir_patterns.matcher("");

		for (File f : files) {
			if (f.isFile())
				continue;
			if (f.getName().equals("bin")) {
				shell.fork(
						new String[] { environment.getBusyBox(), "cp", "-r", f.getName(),
								environment.getTeXMFBinaryDirectory() + "/../../" }, f.getParentFile());
				shell.fork(new String[] { environment.getBusyBox(), "rm", "-R", f.getName() }, f.getParentFile());
				shell.fork(
						new String[] { environment.getBusyBox(), "chmod", "-R", "700",
								environment.getTeXMFBinaryDirectory() + "/../../" }, null);
			} else if (!matcher.reset(f.getName()).matches()) {
				// this directory should not be in TEXMF_ROOT, relocate it under
				// texmf-dist sub-directory
				if (!texmf_dist.exists())
					texmf_dist.mkdirs();
				shell.fork(
						new String[] { environment.getBusyBox(), "cp", "-R", "-f", f.getName(),
								texmf_dist.getAbsolutePath() + "/" }, f.getParentFile());
				shell.fork(new String[] { environment.getBusyBox(), "rm", "-R", f.getName() }, f.getParentFile());
			}
		}
	}

	@Override
	public void run() {
		reset();
		setState(TaskState.EXECUTING);

		// compute dependency if necessary
		num_success_packages = 0;
		if (pending_packages == null) {
			try {
				pending_packages = addAllDependentPackages(packages);
				package_states = new PackageState[pending_packages.length];
				// This state change should not affect `executability` status of other tasks so there is no need to
				// notify the task manager
				environment.onStateChanged(this);
			} catch (Exception e) {
				setException(e);
				return;
			}
		}

		boolean has_lualibs = false;
		for (int i = 0; i < pending_packages.length; i++) {
			if (Thread.interrupted())
				break;
			setPackageState(i, PackageState.PACKAGE_INSTALLING);
			// TODO Fix this: return on failure to install requested package
			// only continue if some dependent package is missing
			try {
				// Retrieve the package
				File pkg_file = environment.getPackage(pending_packages[i]);
				if (pkg_file == null) {
					setPackageState(i, PackageState.PACKAGE_FAIL);
					continue;
				}
				// Copy the package file to TeXMF root (if necessary)
				if (!pkg_file.getParentFile().getAbsolutePath().equals(environment.getTeXMFRootDirectory())) {
					File new_pkg_file = new File(environment.getTeXMFRootDirectory() + "/" + pkg_file.getName());
					Streams.streamToFile(new FileInputStream(pkg_file), new_pkg_file, true, false);
					pkg_file = new_pkg_file;
				}
				// Extract the package.tar.xz file
				shell.fork(new String[] { environment.getBusyBox(), "tar", "xf", pkg_file.getName() },
						pkg_file.getParentFile());
				setPackageState(i, PackageState.PACKAGE_SUCCESSFULLY_INSTALLED);
				has_lualibs = has_lualibs || pending_packages[i].equals("lualibs");
				environment.onStateChanged(this); // notify environment directly
			} catch (SystemFileNotFoundException e) {
				setException(e);
				return;
			} catch (InterruptedException e) {
				break;
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
			return;
		}
	}

	private void setPackageState(int package_id, PackageState package_state) {
		package_states[package_id] = package_state;
		if (package_state == PackageState.PACKAGE_SUCCESSFULLY_INSTALLED)
			num_success_packages++;
	}

}
