package lah.tex.manage;

public class TLPackage implements Comparable<TLPackage> {

	public static final String KEY_PKG_SHORT_DESCRIPTION = "shortdesc";

	private String description;

	private boolean is_installed;

	private final String name;

	private int revision;

	public TLPackage(String pkg_name, String short_desc) {
		name = pkg_name;
		description = short_desc;
	}

	/**
	 * Compare this package's name with another package's name
	 * 
	 * @param pkg
	 * @return
	 */
	public int compareTo(TLPackage pkg) {
		return name.compareTo(pkg.getName());
	}

	/**
	 * Compare this package object with another object.
	 * 
	 * Here, we allow for comparison with String so that installer can remove installed packages directly from their
	 * names.
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TLPackage)
			return name.equals(((TLPackage) obj).getName());
		else if (obj instanceof String)
			return name.equals(obj);
		else
			return false;
	}

	public String getDescription() {
		return description;
	}

	/**
	 * Get the name of this package
	 * 
	 * @return the name of the package
	 */
	public String getName() {
		return name;
	}

	/**
	 * Get the current revision of this package
	 * 
	 * @return
	 */
	public int getRevision() {
		return revision;
	}

	/**
	 * Check if this package is of Collection category. Currently we obtain this information directly from the name, not
	 * from the field category.
	 * 
	 * @return
	 */
	public boolean isCollection() {
		return name.startsWith("collection-");
	}

	public boolean isInstalled() {
		return is_installed;
	}

	/**
	 * Check if this package is of Scheme category. Currently we obtain this information directly from the name, not
	 * from the field category.
	 * 
	 * @return
	 */
	public boolean isScheme() {
		return name.startsWith("scheme-");
	}

	@Override
	public String toString() {
		return getName();
	}

}
