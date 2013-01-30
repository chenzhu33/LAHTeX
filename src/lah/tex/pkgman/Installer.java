package lah.tex.pkgman;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
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

	private static final String lsR_magic = "% ls-R -- filename database for kpathsea; do not change this line.\n";

	public static final String PACKAGE_EXTENSION = ".tar.xz";

	/**
	 * RegEx pattern for the sub-directories in TEXMF_ROOT
	 */
	private static final Pattern texmf_subdir_patterns = Pattern
			.compile("texmf.*|readme.*|tlpkg");

	private Map<String, String[]> depend;

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

		// FileRelocator relocator = new TDSFileLocator(texmf_root);
		for (int i = 0; i < pkgs_to_install.length; i++) {
			result.setPackageState(i, IInstallationResult.PACKAGE_INSTALLING);
			// TODO Fix this: return on failure to install requested package
			// only continue if some dependent package is missing
			try {
				String pkf_file_name = (pkgs_to_install[i].endsWith(".ARCH") ? pkgs_to_install[i]
						.substring(0, pkgs_to_install[i].length() - 5)
						+ environment.getArchitecture() : pkgs_to_install[i])
						+ PACKAGE_EXTENSION;
				File pkg_file = package_file_supplier.getFile(pkf_file_name);
				if (pkg_file == null) {
					result.setPackageState(i, IInstallationResult.PACKAGE_FAIL);
					continue;
				}

				// decompress the package
				shell.fork(new String[] { environment.getXZ(), "-d", "-k",
						pkg_file.getName() }, pkg_file.getParentFile());

				// xzdec(pkg_file, true);

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
		// post download and extract packages
		try {
			relocate(); // relocate the files to the TeX directory structures
			makeLSR(null); // and also regenerate ls-R files
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
					+ "<cachedir>"
					+ cachedir.getAbsolutePath()
					+ "</cachedir>\n"
					+ "<dir>/system/fonts</dir>\n" // Android system fonts
					+ "<dir>" + tl_fonts + "/opentype</dir>\n" + "<dir>"
					+ tl_fonts + "/truetype</dir>\n" + "<dir>" + tl_fonts
					+ "/type1</dir>\n" + "</fontconfig>\n";
			Streams.writeStringToFile(config, configfile, false);
		}
		// Execute command to re-generate the font cache
		shell.fork(new String[] { environment.getTeXMFBinaryDirectory()
				+ "/fc-cache" }, null, new String[][] { { "FONTCONFIG_PATH",
				configdir.getAbsolutePath() } }, null, 0);
	}

	/**
	 * Write out language configurations
	 * 
	 * Writing language.dat to /texmf-var/tex/generic/config/language.dat
	 * Writing language.def to /texmf-var/tex/generic/config/language.def
	 * writing language.dat.lua to
	 * /texmf-var/tex/generic/config/language.dat.lua
	 * 
	 * @throws Exception
	 */
	@Override
	public void makeLanguageConfiguration(String[] languages,
			boolean[] enable_languages) throws Exception {
		final String texmf_language_config = environment
				.getTeXMFRootDirectory() + "/texmf-var/tex/generic/config";
		// language_configs[i] is a String array whose first element is the name
		// of the configuration file and the remaining is its content to write
		// to.
		String[][] language_configs = {
				{
						"language.dat",
						"english		hyphen.tex  % do not change!",
						"=usenglish",
						"=USenglish",
						"=american",
						"dumylang	dumyhyph.tex    %for testing a new language.",
						"nohyphenation	zerohyph.tex    %a language with no patterns at all." },
				{
						"language.def",
						"%% e-TeX V2.0;2",
						"\\addlanguage {USenglish}{hyphen}{}{2}{3} %%% This MUST be the first non-comment line of the file",
						"\\uselanguage {USenglish}             %%% This MUST be the last line of the file." }

		};
		new File(texmf_language_config).mkdirs();
		boolean is_modified = false;
		for (int i = 0; i < language_configs.length; i++) {
			if (!new File(texmf_language_config + "/" + language_configs[i][0])
					.exists()) {
				FileWriter fwr = new FileWriter(new File(texmf_language_config
						+ "/" + language_configs[i][0]));
				for (int j = 1; j < language_configs[i].length; j++)
					fwr.write(language_configs[i][j] + "\n");
				fwr.close();
				is_modified = true;
			}
		}
		if (is_modified)
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
