package lah.tex.pkgman;

import java.util.LinkedList;
import java.util.regex.Pattern;

import lah.spectre.stream.Streams;
import lah.tex.interfaces.IEnvironment;

public class Seeker extends PkgManBase implements lah.tex.interfaces.ISeeker {

	private String index;

	final Pattern single_dot_pattern = Pattern.compile("\\.");

	public Seeker(IEnvironment environment) {
		super(environment);
	}

	private void loadIndex() throws Exception {
		if (index == null) {
			index = Streams.readTextFile(environment.getPackageIndexFile());
		}
	}

	@Override
	public String[] seekFile(String query) throws Exception {
		loadIndex();
		LinkedList<String> res = new LinkedList<String>();
		int k = 0;
		query = "/" + query + "/";
		while ((k = index.indexOf(query, k)) >= 0) {
			int j = k;
			while (j >= 0 && index.charAt(j) != '\n')
				j--;
			j++;
			int i = j;
			while (index.charAt(i) != '/')
				i++;
			k++;
			res.add(index.substring(j, i));
		}
		return res.size() > 0 ? res.toArray(new String[res.size()]) : null;
	}
}
