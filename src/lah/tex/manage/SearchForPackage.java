package lah.tex.manage;

import lah.tex.Task;

public class SearchForPackage extends Task {

	protected String file_query;

	protected String[] search_result;

	protected SearchForPackage() {
	}

	public SearchForPackage(String file_query) {
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
			search_result = findPackagesWithFile(file_query);
		} catch (Exception e) {
			setException(e);
		}
	}

}
