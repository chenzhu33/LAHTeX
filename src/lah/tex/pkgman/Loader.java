package lah.tex.pkgman;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import lah.tex.interfaces.IEnvironment;
import lah.tex.interfaces.ILoader;
import lah.tex.interfaces.IPackage;
import lah.tex.interfaces.IPackageListRetrievalResult;
import lah.utils.spectre.stream.Streams;

public class Loader extends PkgManBase implements ILoader {

	private List<TLPackage> pkgs_list;

	private PackageListRetrievalResult pkgs_list_result;

	public Loader(IEnvironment environment) {
		super(environment);
	}

	public IPackageListRetrievalResult loadPackageList() {
		// pkgs_list is already loaded
		if (pkgs_list != null)
			return pkgs_list_result;

		try {
			String desc = Streams.readTextFile(environment
					.getPackageDescriptionFile());
			Matcher matcher = line_pattern.matcher(desc);
			pkgs_list = new ArrayList<TLPackage>();
			while (matcher.find()) {
				String name = matcher.group(1);
				String shortdesc = matcher.group(2);
				TLPackage pkg = new TLPackage(name, shortdesc);
				pkgs_list.add(pkg);
			}
			ArrayList<IPackage> result = new ArrayList<IPackage>();
			result.addAll(pkgs_list);
			pkgs_list_result = new PackageListRetrievalResult(result);
			return pkgs_list_result;
		} catch (Exception e) {
			pkgs_list_result = new PackageListRetrievalResult();
			pkgs_list_result.setException(e);
			return pkgs_list_result;
		}
	}
}
