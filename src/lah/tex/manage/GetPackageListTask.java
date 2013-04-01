package lah.tex.manage;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lah.spectre.multitask.TaskState;
import lah.tex.IEnvironment;
import lah.tex.Task;

public class GetPackageListTask extends Task {

	private static List<TLPackage> package_list;

	private final Pattern line_pattern = Pattern.compile("([^ ]+) (.*)\n");

	@Override
	public String getDescription() {
		return "Get list of available packages";
	}

	public List<TLPackage> getPackageList() {
		return package_list;
	}

	@Override
	public void run() {
		reset();
		// already loaded, there is no need to run again
		if (package_list != null)
			return;
		try {
			// String desc = Streams.readTextFile(environment
			// .getPackageDescriptionFile());
			String desc = environment.readLahTeXAsset(IEnvironment.LAHTEX_DESC);
			Matcher matcher = line_pattern.matcher(desc);
			List<TLPackage> temp_package_list = new ArrayList<TLPackage>();
			while (matcher.find()) {
				String name = matcher.group(1);
				String shortdesc = matcher.group(2);
				TLPackage pkg = new TLPackage(name, shortdesc);
				temp_package_list.add(pkg);
			}
			package_list = temp_package_list;
			setState(TaskState.COMPLETE);
		} catch (Exception e) {
			setException(e);
		}
	}

}
