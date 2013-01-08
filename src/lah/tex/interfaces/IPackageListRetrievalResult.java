package lah.tex.interfaces;

import java.util.List;

import lah.spectre.interfaces.IResult;

public interface IPackageListRetrievalResult extends IResult {

	List<IPackage> getPackageList();

}
