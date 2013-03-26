package lah.tex;

public interface IEnvironment {

	public static final String BUSYBOX = "busybox";

	public static final String LAHTEX_DEPEND = "pkgdepend.lahtex",
			LAHTEX_INDEX = "pkgindex.lahtex", LAHTEX_DESC = "pkgdesc.lahtex",
			LAHTEX_DBKEYS = "dbkeys.lahtex";

	String getArchitecture();

	String getBusyBox() throws Exception;

	String getOSFontsDirectory();

	String getTeXMFBinaryDirectory();

	String getTeXMFRootDirectory();

	boolean isPortable();

	String readDataFile(String data_file_name) throws Exception;

}
