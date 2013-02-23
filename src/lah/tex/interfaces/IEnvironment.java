package lah.tex.interfaces;

import java.io.File;

import lah.tex.core.SystemFileNotFoundException;

public interface IEnvironment {

	String getArchitecture();

	String getBusyBox() throws SystemFileNotFoundException;

	String getCHMOD() throws SystemFileNotFoundException;

	String getCP() throws SystemFileNotFoundException;

	String getLS() throws SystemFileNotFoundException;

	String getOSFontsDir();

	File getPackageDependFile() throws SystemFileNotFoundException;

	File getPackageDescriptionFile() throws SystemFileNotFoundException;

	File getPackageIndexFile() throws SystemFileNotFoundException;

	String getRM() throws SystemFileNotFoundException;

	String getTAR() throws SystemFileNotFoundException;

	String getTeXMFBinaryDirectory();

	String getTeXMFRootDirectory();

	String getXZ() throws SystemFileNotFoundException;

	boolean isPortable();

}
