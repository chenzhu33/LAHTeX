package lah.tex.manage;

import java.util.LinkedList;
import java.util.regex.Pattern;

import lah.spectre.stream.Streams;
import lah.tex.Task;

public class PackageSearchTask extends Task {

	private static String index;

	private String query;

	private String[] result;

	final Pattern single_dot_pattern = Pattern.compile("\\.");

	public PackageSearchTask(String query) {
		this.query = query;
	}

	@Override
	public String getDescription() {
		return "Search for " + query;
	}

	public String[] getResult() {
		return result;
	}

	private void loadIndex() throws Exception {
		if (index == null)
			index = Streams.readTextFile(environment.getPackageIndexFile());
	}

	@Override
	public void run() {
		try {
			loadIndex();
		} catch (Exception e) {
			setException(e);
			return;
		}
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
		result = res.size() > 0 ? res.toArray(new String[res.size()]) : null;
	}

}
