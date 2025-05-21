package gov.ca.qp.eplibraries.builder.crbc.common;

public class ReferenceObject
{
	private String _pathToLiveDirWithRegDir = null, _sourceFile = null, _targetPath = null;

	public ReferenceObject(String pathToLiveDirWithRegDir, String sourceFile, String targetPath)
	{
		_pathToLiveDirWithRegDir = pathToLiveDirWithRegDir;
		_sourceFile = sourceFile;
		_targetPath = targetPath;
	}
	
	/**
	 * @return the _pathToLiveDirWithRegDir
	 */
	public String get_pathToLiveDirWithRegDir()
	{
		return _pathToLiveDirWithRegDir;
	}

	/**
	 * @param _pathToLiveDirWithRegDir the _pathToLiveDirWithRegDir to set
	 */
	public void set_pathToLiveDirWithRegDir(String _pathToLiveDirWithRegDir)
	{
		this._pathToLiveDirWithRegDir = _pathToLiveDirWithRegDir;
	}

	/**
	 * @return the _sourceFile
	 */
	public String get_sourceFile()
	{
		return _sourceFile;
	}

	/**
	 * @param _sourceFile the _sourceFile to set
	 */
	public void set_sourceFile(String _sourceFile)
	{
		this._sourceFile = _sourceFile;
	}

	/**
	 * @return the _targetPath
	 */
	public String get_targetPath()
	{
		return _targetPath;
	}

	/**
	 * @param _targetPath the _targetPath to set
	 */
	public void set_targetPath(String _targetPath)
	{
		this._targetPath = _targetPath;
	}
}
