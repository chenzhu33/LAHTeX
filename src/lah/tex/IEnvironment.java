package lah.tex;

import java.io.File;
import java.util.Locale;

public interface IEnvironment {

	public static final String BUSYBOX = "busybox";

	public static final String LAHTEX_DEPEND = "depend.lahtex", LAHTEX_INDEX = "index.lahtex",
			LAHTEX_DESC = "desc.lahtex", LAHTEX_DBKEYS = "dbkeys.lahtex";

	String getArchitecture();

	String getBusyBox() throws Exception;

	String getOSFontsDirectory();

	File getPackage(String package_name) throws Exception;

	String getTeXMFBinaryDirectory();

	String getTeXMFRootDirectory();
	
	Locale getLocale();

	boolean isPortable();

	String readLahTeXAsset(String asset_file_name) throws Exception;

}
