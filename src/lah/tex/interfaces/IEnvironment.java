package lah.tex.interfaces;

import java.io.File;

import lah.tex.exceptions.SystemFileNotFoundException;

public interface IEnvironment {

	public static final String BUSYBOX = "busybox";

	public static final String LAHTEX_DEPEND = "lahtex_depend",
			LAHTEX_INDEX = "lahtex_index", LAHTEX_DESC = "lahtex_desc";

	String getArchitecture();

	String getBusyBox() throws SystemFileNotFoundException;

	String getOSFontsDir();

	File getPackageDependFile() throws SystemFileNotFoundException;

	File getPackageDescriptionFile() throws SystemFileNotFoundException;

	File getPackageIndexFile() throws SystemFileNotFoundException;

	String getTeXMFBinaryDirectory();

	String getTeXMFRootDirectory();

	boolean isPortable();

}
