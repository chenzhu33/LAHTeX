package lah.tex.interfaces;

public interface ISeeker {

	/**
	 * Query the package list for some information
	 * 
	 * @param query
	 * @return The list containing names of packages satisfying the query
	 */
	String[] seekFile(String query) throws Exception;

}
