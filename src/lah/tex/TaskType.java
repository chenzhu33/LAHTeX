package lah.tex;

/**
 * Enumeration of classified services
 * 
 * @author L.A.H.
 * 
 */
public enum TaskType {
	/**
	 * Compile a document
	 */
	COMPILE,
	/**
	 * Install a package
	 */
	INSTALL_PACKAGE,
	/**
	 * Generate fontconfig's configuration files and cache
	 */
	MAKE_FONTCONFIG,
	/**
	 * Generate language configurations (hyphenation patterns)
	 */
	MAKE_LANGUAGES_CONFIG
}