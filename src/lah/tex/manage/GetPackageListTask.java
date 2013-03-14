package lah.tex.manage;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lah.spectre.stream.Streams;
import lah.tex.Task;
import lah.tex.interfaces.IPackage;
import lah.tex.interfaces.IPackageListRetrievalResult;

public class GetPackageListTask extends Task implements
		IPackageListRetrievalResult {

	private static List<IPackage> package_list;

	private final Pattern line_pattern = Pattern.compile("([^ ]+) (.*)\n");

	public List<IPackage> getPackageList() {
		return package_list;
	}

	@Override
	public void run() {
		// already loaded, there is no need to run again
		if (package_list != null)
			return;
		try {
			String desc = Streams.readTextFile(environment
					.getPackageDescriptionFile());
			Matcher matcher = line_pattern.matcher(desc);
			List<TLPackage> pkgs_list = new ArrayList<TLPackage>();
			while (matcher.find()) {
				String name = matcher.group(1);
				String shortdesc = matcher.group(2);
				TLPackage pkg = new TLPackage(name, shortdesc);
				pkgs_list.add(pkg);
			}
			package_list = new ArrayList<IPackage>();
			package_list.addAll(pkgs_list);
		} catch (Exception e) {
			setException(e);
		}
	}

}
