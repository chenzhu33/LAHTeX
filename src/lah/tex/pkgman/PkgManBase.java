package lah.tex.pkgman;

import java.util.regex.Pattern;

import lah.tex.interfaces.IEnvironment;

class PkgManBase {

	IEnvironment environment;

	final Pattern line_pattern = Pattern.compile("([^ ]+) (.*)\n");

	PkgManBase(IEnvironment environment) {
		this.environment = environment;
	}

}
