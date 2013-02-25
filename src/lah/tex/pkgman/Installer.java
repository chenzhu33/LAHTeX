package lah.tex.pkgman;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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

import lah.spectre.interfaces.IClient;
import lah.spectre.interfaces.IFileSupplier;
import lah.spectre.process.TimedShell;
import lah.spectre.stream.StreamRedirector;
import lah.spectre.stream.Streams;
import lah.tex.core.SystemFileNotFoundException;
import lah.tex.core.TeXMFFileNotFoundException;
import lah.tex.interfaces.IEnvironment;
import lah.tex.interfaces.IInstallationResult;
import lah.tex.interfaces.IInstaller;

/**
 * This class installs TeX Live packages
 * 
 * @author L.A.H.
 * 
 */
public class Installer extends PkgManBase implements IInstaller {

	private static final Pattern lang_pattern = Pattern
			.compile("% from hyphen-(.*):\n[^%]*");

	private static final String[] language_files = { "language.dat",
			"language.def" };

	private static final String lsR_magic = "% ls-R -- filename database for kpathsea; do not change this line.\n";

	public static final String PACKAGE_EXTENSION = ".tar.xz";

	/**
	 * RegEx pattern for the sub-directories in TEXMF_ROOT
	 */
	private static final Pattern texmf_subdir_patterns = Pattern
			.compile("texmf.*|readme.*|tlpkg");

	private Map<String, String[]> depend;

	private Map<String, String> language_dat_map, language_def_map;

	private TimedShell shell = new TimedShell();

	final Pattern single_space_pattern = Pattern.compile(" ");

	private final File texmf_dist;

	public Installer(IEnvironment environment) {
		super(environment);
		texmf_dist = new File(environment.getTeXMFRootDirectory()
				+ "/texmf-dist");
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

	public String[] getAllLanguages() throws Exception {
		for (String lf : language_files) {
			File langfile = new File(environment.getTeXMFRootDirectory()
					+ "/texmf/tex/generic/config/" + lf);
			if (!langfile.exists())
				throw new TeXMFFileNotFoundException(lf, null);
			String content = Streams.readTextFile(langfile);
			Matcher langconfig_matcher = lang_pattern.matcher(content);
			Map<String, String> lfmap;
			if (lf.equals(language_files[0]))
				lfmap = language_dat_map = new TreeMap<String, String>();
			else
				lfmap = language_def_map = new TreeMap<String, String>();
			while (langconfig_matcher.find())
				lfmap.put(langconfig_matcher.group(1),
						langconfig_matcher.group());
		}
		return language_dat_map.keySet().toArray(
				new String[language_dat_map.size()]);
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
	public synchronized IInstallationResult install(
			IClient<IInstallationResult> client,
			IFileSupplier package_file_supplier, String[] package_names,
			boolean ignore_installed) {
		InstallationResult result = new InstallationResult();
		result.setRequestedPackages(package_names);

		// inform the listener that the result is initialized
		if (client != null)
			client.onServerReady(result);

		// copy xz, busybox, ... to private directory
		if (package_names.length == 1 && package_names[0].startsWith("/")) {
			return installSystemFile(package_names[0].substring(1),
					package_file_supplier);
		}

		String[] pkgs_to_install;
		try {
			pkgs_to_install = addAllDependentPackages(package_names);
		} catch (Exception e) {
			result.setException(e);
			return result;
		}
		result.setPendingPackages(pkgs_to_install);

		final String texmf_root = environment.getTeXMFRootDirectory();
		boolean has_lualibs = false;
		for (int i = 0; i < pkgs_to_install.length; i++) {
			result.setPackageState(i, IInstallationResult.PACKAGE_INSTALLING);
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
					result.setPackageState(i, IInstallationResult.PACKAGE_FAIL);
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
					result.setPackageState(i,
							IInstallationResult.PACKAGE_SUCCESSFULLY_INSTALLED);
				} else {
					result.setPackageState(i, IInstallationResult.PACKAGE_FAIL);
				}

				has_lualibs = has_lualibs
						|| pkgs_to_install[i].equals("lualibs");
			} catch (SystemFileNotFoundException e) {
				result.setException(e);
				return result;
			} catch (Exception e) {
				result.setPackageState(i, IInstallationResult.PACKAGE_FAIL);
			}
		}

		// Post download and extract packages
		try {
			relocate(); // relocate the files to the TeX directory structures
			makeLSR(null); // and also regenerate ls-R files
			if (has_lualibs)
				fixLualibsFile();
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
			return null;
		} catch (Exception e) {
			result.setException(e);
			return result;
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

	@Override
	public void makeFontConfiguration() throws Exception {
		// Prepare the font configuration file (if necessary)
		File configfile = new File(environment.getTeXMFRootDirectory()
				+ "/texmf-var/fonts/conf/fonts.conf");
		File configdir = new File(environment.getTeXMFRootDirectory()
				+ "/texmf-var/fonts/conf/");
		if (!configdir.exists())
			configdir.mkdirs();
		File cachedir = new File(environment.getTeXMFRootDirectory()
				+ "/texmf-var/fonts/cache/");
		if (!cachedir.exists())
			cachedir.mkdirs();
		if (!configfile.exists()) {
			String tl_fonts = environment.getTeXMFRootDirectory()
					+ "/texmf-dist/fonts";
			String config = "<?xml version=\"1.0\"?>"
					+ "<!DOCTYPE fontconfig SYSTEM \"fonts.dtd\"><fontconfig>"
					+ "<cachedir>" + cachedir.getAbsolutePath() + "</cachedir>\n"
					+ "<dir>" + environment.getOSFontsDir() + "</dir>\n"
					+ "<dir>" + tl_fonts + "/opentype</dir>\n"
					+ "<dir>" + tl_fonts + "/truetype</dir>\n"
					+ "<dir>" + tl_fonts + "/type1</dir>\n"
					+ "</fontconfig>\n";
			Streams.writeStringToFile(config, configfile, false);
		}
		// Execute command to re-generate the font cache
		shell.fork(new String[] { environment.getTeXMFBinaryDirectory()
				+ "/fc-cache" }, null, new String[] { "FONTCONFIG_PATH",
				configdir.getAbsolutePath() }, null, 0);
	}

	@Override
	public void makeLanguageConfiguration(String[] languages) throws Exception {
		final String texmf_language_config = environment
				.getTeXMFRootDirectory() + "/texmf-var/tex/generic/config";
		new File(texmf_language_config).mkdirs();
		// Write language.dat
		String language_dat = "english		hyphen.tex  % do not change!\n"
				+ "=usenglish\n"
				+ "=USenglish\n"
				+ "=american\n"
				+ "dumylang	dumyhyph.tex    %for testing a new language.\n"
				+ "nohyphenation	zerohyph.tex    %a language with no patterns at all.\n";
		if (languages != null) {
			if (language_dat_map == null)
				getAllLanguages();
			for (int i = 0; i < languages.length; i++)
				language_dat = language_dat
						+ language_dat_map.get(languages[i]);
		}
		Streams.writeStringToFile(language_dat, new File(texmf_language_config
				+ "/language.dat"), false);
		// Write language.def
		String language_def = "%% e-TeX V2.0;2\n"
				+ "\\addlanguage {USenglish}{hyphen}{}{2}{3} %%% This MUST be the first non-comment line of the file\n";
		if (languages != null) {
			if (language_def_map == null)
				getAllLanguages();
			for (int i = 0; i < languages.length; i++)
				language_def = language_def
						+ language_def_map.get(languages[i]);
		}
		language_def = language_def
				+ "\\uselanguage {USenglish}             %%% This MUST be the last line of the file.\n";
		Streams.writeStringToFile(language_def, new File(texmf_language_config
				+ "/language.def"), false);
		// Regenerate path databases and remove existing format files, if any
		shell.fork(
				new String[] {
						"rm",
						"-r",
						environment.getTeXMFRootDirectory()
								+ "/texmf-var/web2c" }, null);
		makeLSR(null);
	}

	/**
	 * Generate the path database files (ls-R) in all TeX directory trees
	 * (texmf*)
	 * 
	 * @throws Exception
	 */
	@Override
	public void makeLSR(String[] files) throws Exception {
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
			Streams.writeStringToFile(lsR_magic, temp_lsRfile, false);

			// Now do the "ls -R . >> ls-R" in the texmf root directory
			final FileOutputStream stream = new FileOutputStream(temp_lsRfile,
					true);
			shell.fork(new String[] { environment.getLS(), "-R", "." },
					texmf_dir, new StreamRedirector(stream), 600000);
			stream.close();

			// Move the temporary file to the intended location
			temp_lsRfile.renameTo(lsRfile);
		}
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
}
