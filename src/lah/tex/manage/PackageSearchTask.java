package lah.tex.manage;

import java.util.LinkedList;

import lah.spectre.stream.Streams;
import lah.tex.Task;

public class PackageSearchTask extends Task {

	/**
	 * The content of the text file "index", each line is of format
	 * {@code [package_name]/[file_1]/[file_2]/.../[file_n]/} where
	 * {@code [file_1], [file_2], ..., [file_n]} are all files contained in a
	 * package with name {@code [package_name]}.
	 */
	private static String package_file_index;

	protected String file_query;

	protected String[] search_result;

	protected PackageSearchTask() {
	}

	public PackageSearchTask(String file_query) {
		this.file_query = file_query;
	}

	@Override
	public String getDescription() {
		return "Search for package containing " + file_query;
	}

	public String[] getSearchResult() {
		return search_result;
	}

	@Override
	public void run() {
		try {
			if (package_file_index == null) {
				String temp_index = Streams.readTextFile(environment
						.getPackageIndexFile());
				package_file_index = temp_index;
			}
			LinkedList<String> res = new LinkedList<String>();
			int k = 0;
			file_query = "/" + file_query + "/";
			while ((k = package_file_index.indexOf(file_query, k)) >= 0) {
				int j = k;
				while (j >= 0 && package_file_index.charAt(j) != '\n')
					j--;
				j++;
				int i = j;
				while (package_file_index.charAt(i) != '/')
					i++;
				k++;
				res.add(package_file_index.substring(j, i));
			}
			search_result = res.size() > 0 ? res
					.toArray(new String[res.size()]) : null;
		} catch (Exception e) {
			setException(e);
			return;
		}
	}

}
