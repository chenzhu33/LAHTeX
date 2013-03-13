package lah.tex.pkgman;

import java.util.List;

import lah.tex.interfaces.IPackage;
import lah.tex.interfaces.IPackageListRetrievalResult;
import lah.tex.task.BaseTask;

public class PackageListRetrievalResult extends BaseTask implements
		IPackageListRetrievalResult {

	List<IPackage> package_list;

	PackageListRetrievalResult() {
	}

	PackageListRetrievalResult(List<IPackage> pkg_list) {
		super();
		package_list = pkg_list;
	}

	public List<IPackage> getPackageList() {
		return package_list;
	}

}
