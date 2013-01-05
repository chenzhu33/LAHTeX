package lah.tex.interfaces;

import lah.spectre.interfaces.IExceptionWrapper;

public interface IResult extends IExceptionWrapper {

	boolean isComplete();

	boolean isSuccessful();

}
