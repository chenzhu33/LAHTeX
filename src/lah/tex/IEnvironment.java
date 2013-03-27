package lah.tex;

public interface IEnvironment {

	public static final String BUSYBOX = "busybox";

	public static final String LAHTEX_DEPEND = "depend.lahtex",
			LAHTEX_INDEX = "index.lahtex", LAHTEX_DESC = "desc.lahtex",
			LAHTEX_DBKEYS = "dbkeys.lahtex";

	String getArchitecture();

	String getBusyBox() throws Exception;

	String getOSFontsDirectory();

	String getTeXMFBinaryDirectory();

	String getTeXMFRootDirectory();

	boolean isPortable();

	String readLahTeXAssetFile(String asset_file_name) throws Exception;

}
