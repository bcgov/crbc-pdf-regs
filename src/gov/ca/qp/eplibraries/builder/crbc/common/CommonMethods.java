package gov.ca.qp.eplibraries.builder.crbc.common;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.io.FileUtils;
import org.xml.sax.SAXException;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.xml.xmp.PdfSchema;
import com.itextpdf.text.xml.xmp.XmpWriter;

import qputility.config.QPConfigManager;
import qputility.enums.QPDisplayDebugInfo;
import qputility.exceptions.QPConfig_Exception;
import qputility.exceptions.QPGenericException;
import qputility.io.QPDirectory;
import qputility.io.QPFileIO;
import qputility.io.QPReader;
import qputility.io.QPWriter;

public class CommonMethods
{
	private final static Pattern dirIdPattern = Pattern.compile("id=\"([^\"]+?)\"", Pattern.CASE_INSENSITIVE);
	private final static Pattern pitDatePattern = Pattern.compile("(\\d\\d\\d\\d-\\d\\d-\\d\\d)", Pattern.CASE_INSENSITIVE);
	
	private static PdfStamper stamper = null;
	private static PdfReader reader = null;
	private static HashMap<String, String> info = null;
	private static ByteArrayOutputStream baos = null;
	private static XmpWriter xmp = null;
	private static File sourceFile = null;
	private static File outputFile = null;
	
	public static void createPermalink(String path, String id) throws IOException
	{
		StringBuilder pmsb = new StringBuilder();
		pmsb.append("id=\"");
		pmsb.append(id);
		pmsb.append("\"");
		
		QPWriter writer = new QPWriter();
		writer.writeStringToFile(pmsb.toString(), path + "\\directory.permalink", false, true);	
	}
	
	public static void testAndDeleteOrphanedDirs(String currentFolder) throws Exception
	{
		System.out.println("testing currentFolder = " + currentFolder); 
		File dir = new File(currentFolder);
		if(dir.isDirectory())
		{
			boolean foundPDF = (!(findAnyChildPDFs(dir, new ArrayList<String>())).isEmpty());
			String dirTitle = QPDirectory.getDirectoryName(currentFolder);

			if((!foundPDF) && (!dirTitle.equalsIgnoreCase("live")))
			{	
				FileUtils.deleteDirectory(dir);
				if(!dir.exists())
				{
					System.out.println("Deleted directory: " + dir.getPath());	
				}
				else
				{
					throw new Exception("Unable to delete directory: " + dir.getPath());
				}
				testAndDeleteOrphanedDirs(dir.getParent());
			}
		}
	}
	
	public static ArrayList<String>findAnyChildPDFs(File dir, ArrayList<String> foundFiles)
	{
		File[] files = dir.listFiles();
		for(int i = 0; i < files.length; i ++)
		{
			File file = files[i];
			if(!file.isFile())
			{
				foundFiles = findAnyChildPDFs(file, foundFiles);
			}
			else
			{
				if(file.getPath().contains(".pdf"))
				{
					foundFiles.add(file.getPath());
				}				
			}
		}
		return foundFiles;
	}
	
	//This method returns the ID from a permalink file
	public static String getPermalinkID(String permalinkFilePath, QPDisplayDebugInfo displayDebugInfo) throws IOException
	{
		String id = "";
		
		try {
		
			File f = new File(permalinkFilePath);
			
			if(f != null && f.exists()) {
				String permaLinkContents = QPReader.readFileToString(f);
				Matcher idMatcher = dirIdPattern.matcher(permaLinkContents);
				
				if(idMatcher.find()) {
					id = idMatcher.group(1).toString();		
				} else {
					throw new IOException("Error Directory id not found: " + permalinkFilePath);
				}
			} else {
				throw new IOException("Cannot open file: " + permalinkFilePath);
			}
		} catch (IOException e) {
			
			throw e;
		}

		return id;
	}
	
	//This method returns the ID of a PDF if it exists, or creates a new ID if it does not.  ID created based on the DIR it lives in.
	public static String getOrSetPDFID(String fileLocation, String pdfPassword, QPDisplayDebugInfo displayDebugInfo, String idToAdd) throws Exception
	{
		File file = new File(fileLocation);
		Long lastModified = file.lastModified();
		
		//checking to see if the pdf has an embedded id
		reader = new PdfReader(fileLocation, pdfPassword.getBytes());
		info = reader.getInfo();
		String id = "";
		if (!info.containsKey("id") || (idToAdd != null))
		{
			reader = null;
			info = null;
	
			//add new id
			reader = new PdfReader(fileLocation, pdfPassword.getBytes());
			info = reader.getInfo();
			stamper = new PdfStamper(reader, new FileOutputStream(fileLocation + "_temp"));
			
			System.out.println("Setting security on: " + fileLocation);
			
			stamper.setEncryption(null, pdfPassword.getBytes(), PdfWriter.ALLOW_COPY | PdfWriter.ALLOW_PRINTING, PdfWriter.ENCRYPTION_AES_128 | PdfWriter.DO_NOT_ENCRYPT_METADATA);
			
			PdfSchema pdfSchema = new PdfSchema();
			pdfSchema.setProperty(PdfSchema.VERSION, "1.4");
			if(idToAdd != null)
			{
				id = idToAdd;
			}
			else
			{
				id = createRegOrRegPITDocID(fileLocation);
			}
			System.out.println("~~Adding ID: " + id + " to the pdf: " + fileLocation);
			info.put("id", id);
			info.put("Title", file.getName().replace(".pdf", ""));
			info.put("Author", "");
	
			stamper.setMoreInfo(info);
			stamper.setFullCompression();
	
			baos = new ByteArrayOutputStream();
			xmp = new XmpWriter(baos, info);
			xmp.addRdfDescription(pdfSchema);
			xmp.close();
	
			stamper.setXmpMetadata(baos.toByteArray());
			stamper.close();
	
			sourceFile = new File(fileLocation);
			if (sourceFile.exists())
			{
				sourceFile.delete();
			}
			outputFile = new File(fileLocation + "_temp");
			outputFile.renameTo(sourceFile);
			sourceFile = null;
			outputFile = null;
			stamper = null;
			info = null;
			baos = null;
			xmp = null;
		}
		else
		{
			id = info.get("id");
		}

		reader = null;
		info = null;
		
		file.setLastModified(lastModified);
		return id;
	}
	
	public static void copyResource(File sourceFile, File targetFile, String resourceName) throws IOException
	{
		System.out.println("Copying static resource " + resourceName + " to the live dir...");
		FileUtils.copyFile(sourceFile, targetFile, true);
		System.out.println("Copied " + resourceName);
	}
	
	public static void ensureStaticResourcesArePresent(String configLocation) throws QPConfig_Exception, ParserConfigurationException, SAXException, IOException
	{
		String resourceDir = QPConfigManager.getInstance(configLocation).getStringValue("staticResourceDir");
		String liveDir = QPConfigManager.getInstance(configLocation).getStringValue("liveDirectory");
		if(!liveDir.endsWith("\\"))
		{
			liveDir += "\\";
		}
		List<String> staticResources = QPConfigManager.getInstance(configLocation).getListValue("staticResourcesToCopyToLive");
		
		for(int i=0; i< staticResources.size(); i++)
		{
			File targetFile = new File(liveDir + staticResources.get(i));
			File sourceFile = new File(resourceDir + staticResources.get(i));
			if(!(targetFile.exists() && (sourceFile.lastModified() == targetFile.lastModified())))
			{
				CommonMethods.copyResource(sourceFile, targetFile, staticResources.get(i));
			}
		}
	}
	
	public static Map<String, String> getAllCRBCRegsForReference(String rootDir, Map<String, String> crbcRegs, int level) throws IOException
	{
		File root = new File(rootDir);
		File[] files = root.listFiles(); // --A--
		
		for(int i=0; i< files.length; i++)
		{
			if(files[i].isDirectory())
			{
				if(level != 2)
				{
					crbcRegs = getAllCRBCRegsForReference(files[i].getPath(), crbcRegs, level + 1);
				}
				else
				{
					String permalinkID = CommonMethods.getPermalinkID(files[i].getPath() + "\\directory.permalink", QPDisplayDebugInfo.displayDebugInfo); 
					
					//here we have a map of the permalink ID to the source CRBC reg w/RegPits.
					//e.g. 386_92_dir
					//\\serv-fs\web_test\EPL\crbc\live-TEST\-- A --\Assessment Authority Act [RSBC 1996] c. 21\386_92
					crbcRegs.put(permalinkID, files[i].getPath());
				}
			}
		}
		
		return crbcRegs;
	}
	
	public static String getActIDForReferencedIDs(String targetPath) throws IOException
	{
		String actPermalinkPath = targetPath.substring(0, targetPath.lastIndexOf("\\")) + "\\directory.permalink";
		return CommonMethods.getPermalinkID(actPermalinkPath, QPDisplayDebugInfo.displayDebugInfo);
	}
	

	public static ArrayList<ReferenceObject> getReferences(File file, ArrayList<ReferenceObject> references, String liveDirectory, String mirrorDirectory, boolean isReferencesToDelete) throws IOException
	{
		File[] files = file.listFiles();

		for(int i=0; i<files.length; i++)
		{
			if(files[i].isDirectory())
			{
				references = getReferences(files[i], references, liveDirectory, mirrorDirectory, isReferencesToDelete);
			}
			else
			{
				if(QPFileIO.getFileExtension(files[i]).equalsIgnoreCase(".reference"))
				{	
					String targetPath = null;
					String pathToLiveDirWithRegDir = files[i].getPath().substring(0, files[i].getPath().lastIndexOf("\\crbc.reference")).replace(mirrorDirectory, liveDirectory);
					if(isReferencesToDelete)
					{
						String fileName = CommonMethods.getRegNameFromDir(pathToLiveDirWithRegDir);
						String referencedFolderPathName = pathToLiveDirWithRegDir.substring(pathToLiveDirWithRegDir.lastIndexOf("\\")).replace("\\", "");
						targetPath = pathToLiveDirWithRegDir.replace(referencedFolderPathName, fileName);
					}
					references.add(new ReferenceObject(pathToLiveDirWithRegDir, files[i].getPath(), targetPath));
				}
			}
		}
		return references;
	}
	

	public static void resolveReferences(ArrayList<ReferenceObject> mirroredReferences, String configLocation, Pattern pathPattern) throws Exception
	{
		if(!mirroredReferences.isEmpty())
		{
			Map<String, String> crbcRegs = CommonMethods.getAllCRBCRegsForReference(QPConfigManager.getInstance(configLocation).getStringValue("liveDirectory"), new HashMap<String, String>(), 0);
			
			//loop through new references
			for(int i=0;i<mirroredReferences.size();i++)
			{
				String refererenceID = CommonMethods.getPermalinkID(mirroredReferences.get(i).get_sourceFile(), QPDisplayDebugInfo.displayDebugInfo) + "_dir";
				String pathToReferenceFile = crbcRegs.get(refererenceID);
		
				System.out.println();
				
				Matcher pathMatcher = pathPattern.matcher(mirroredReferences.get(i).get_sourceFile());
				pathMatcher.find();
				String sourceReferenceFileInMirrorDir = (QPConfigManager.getInstance(configLocation).getStringValue("mirrorDirectory") + "\\" + pathMatcher.group(1)).replace("/", "\\");
				String sourceDir = sourceReferenceFileInMirrorDir.substring(0, sourceReferenceFileInMirrorDir.lastIndexOf("\\")) + "\\";

				String fileName = pathToReferenceFile.substring(pathToReferenceFile.lastIndexOf("\\")).replace("\\", "");
				String referencedFolderPathName = mirroredReferences.get(i).get_pathToLiveDirWithRegDir().substring(mirroredReferences.get(i).get_pathToLiveDirWithRegDir().lastIndexOf("\\")).replace("\\", "");
				String targetPath = mirroredReferences.get(i).get_pathToLiveDirWithRegDir().replace(referencedFolderPathName, fileName);
				
				System.out.println("Copying referenced CRBC from:");
				System.out.println(pathToReferenceFile);
				System.out.println("to:");
				System.out.println(targetPath);
				System.out.println();
				
				FileUtils.copyDirectory(new File(pathToReferenceFile), new File(targetPath));
				
				CommonMethods.ensureDirectoyPermalinksAreCopied(configLocation, sourceDir, targetPath, true);
				
				//Here we need to update the IDs on the PDFs contained within the referenced CRBC reg and PITs
				//use parrent act ID + referencedID + ref
				
				String actID = CommonMethods.getActIDForReferencedIDs(targetPath);
				System.out.println("actID for references = " + actID);
				System.out.println();
				System.out.println("Updating reference IDs...");
				CommonMethods.updateReferencedIDs(targetPath, actID, configLocation);
				System.out.println();
			}
		}
	}
	
	public static void updateReferencedIDs(String targetPath, String actID, String configFileLocation) throws QPConfig_Exception, ParserConfigurationException, SAXException, Exception
	{
		ArrayList<String> pdfs = new ArrayList<String>();
		pdfs = CommonMethods.findAnyChildPDFs(new File(targetPath), pdfs);
		
		boolean foundPitDir = false;
		for(int i=0;i<pdfs.size();i++)
		{
			if(pdfs.get(i).toLowerCase().contains("point in time") && (!foundPitDir))
			{
				foundPitDir = true;
				String pitFolderPermalinkPath = pdfs.get(i).substring(0, pdfs.get(i).lastIndexOf("\\")) + "\\directory.permalink";
				System.out.println("Updating permalink in: " + pitFolderPermalinkPath);
				String permalinkID = CommonMethods.getPermalinkID(pitFolderPermalinkPath, QPDisplayDebugInfo.displayDebugInfo);
				permalinkID = actID + "_r_" + permalinkID;
				System.out.println("Setting permalinkID to: " + permalinkID);
				CommonMethods.createPermalink(pdfs.get(i).substring(0, pdfs.get(i).lastIndexOf("\\")), permalinkID);
				System.out.println("Permalink updated.");
			}
			String pdfID = CommonMethods.getOrSetPDFID(pdfs.get(i), QPConfigManager.getInstance(configFileLocation).getStringValue("pdfPassword"), QPDisplayDebugInfo.displayDebugInfo, null);
			pdfID = actID + "_r_" + pdfID;
			System.out.println("Setting reference ID: " + pdfID + " on: " + pdfs.get(i));
			pdfID = CommonMethods.getOrSetPDFID(pdfs.get(i), QPConfigManager.getInstance(configFileLocation).getStringValue("pdfPassword"), QPDisplayDebugInfo.displayDebugInfo, pdfID);
			System.out.println();
		}
	}
	
	public static void removeReferences(ArrayList<ReferenceObject> mirroredReferences)
	{
		if(!mirroredReferences.isEmpty())
		{
			for(int i=0;i<mirroredReferences.size();i++)
			{
				System.out.println("mirroredReferences.get(i).get_pathToLiveDirWithRegDir())= " + mirroredReferences.get(i).get_pathToLiveDirWithRegDir());
				
				String targetPath = mirroredReferences.get(i).get_targetPath();
				
				System.out.println("Deleting referenced files in:");
				System.out.println(targetPath);
				try
				{
					FileUtils.deleteDirectory(new File(targetPath));
				} 
				catch (IOException e)
				{
					System.out.println();
					System.out.println();
					System.out.println("ERROR--");
					System.out.println("Unable to delete the above refernce from live, as it does not exist.  Skipping and moving on...");
					System.out.println();
					System.out.println();
				}
				
				System.out.println();
				System.out.println("Cleaning up orphaned dirs...");
				try
				{
					CommonMethods.testAndDeleteOrphanedDirs(mirroredReferences.get(i).get_pathToLiveDirWithRegDir().substring(0, mirroredReferences.get(i).get_pathToLiveDirWithRegDir().lastIndexOf("\\")));
				} 
				catch (Exception e)
				{
					System.out.println();
					System.out.println();
					System.out.println("ERROR--");
					System.out.println("Unable to testAndDeleteOrphanedDirs from the above refernce from live, as it does not exist.  Skipping and moving on...");
					System.out.println();
					System.out.println();
				}
			}
		}
	}
	
	public static String getRegNameFromDir(String dirPath) throws IOException
	{
		File[] files = (new File(dirPath.substring(0, dirPath.lastIndexOf("\\")))).listFiles();
		String idWeAreSearchingFor = dirPath.substring(dirPath.lastIndexOf("\\")).toLowerCase().replace("\\", "") + "_dir";  // this will need to be updated to reflect the new ID of the referenced docs
		
		for(int i=0; i<files.length; i++)
		{
			if(files[i].isDirectory())
			{
				File[] regFiles = files[i].listFiles();
				for(int r=0;r<regFiles.length;r++)
				{
					if(!regFiles[r].isDirectory())
					{
						if(QPFileIO.getFileExtension(regFiles[r]).equalsIgnoreCase(".permalink"))
						{
							if(CommonMethods.getPermalinkID(regFiles[r].getPath(), QPDisplayDebugInfo.displayDebugInfo).equalsIgnoreCase(idWeAreSearchingFor))
							{
								for(int v=0;v<regFiles.length;v++)
								{
									if(!regFiles[v].isDirectory())
									{
										if(QPFileIO.getFileExtension(regFiles[v]).equalsIgnoreCase(".pdf"))
										{
											System.out.println(QPFileIO.getFileName(regFiles[v]));
											return QPFileIO.getFileName(regFiles[v]);
										}
									}
								}
							}
								
						}
					}
				}
			}
		}
		return null;
	}
	
	public static String getREGNameFromPDFInDir(String folderContainingREG) throws  IOException
	{
		File[] files = (new File(folderContainingREG)).listFiles();
		for(int i=0;i<files.length;i++)
		{
			if(!(files[i]).isDirectory())
			{
				System.out.println("--- " + files[i]);
				if(QPFileIO.getFileExtension(files[i]).equalsIgnoreCase(".pdf"))
				{
					return files[i].getName().replace(".pdf", "");
				}
			}
		}
		throw new IOException("Unable to get RegName from: " + folderContainingREG);
	}
	
	public static void ensureDirectoyPermalinksAreCopied(String configLocation, String sourceDir, String targetDir, boolean overwrite) throws IOException
	{
		String sourcePermalink = "";
		if(!sourceDir.endsWith("\\"))
		{
			sourceDir = sourceDir + "\\";
		}
		sourcePermalink = sourceDir + "directory.permalink";
		
		String targetPermalink = "";
		if(!targetDir.endsWith("\\"))
		{
			targetDir = targetDir + "\\";
		}
		targetPermalink = targetDir + "directory.permalink";
		
		if(overwrite)
		{
			FileUtils.copyFile(new File(sourcePermalink), new File(targetPermalink), true);
		}
		else
		{
			if(!new File(targetPermalink).exists())
			{
				FileUtils.copyFile(new File(sourcePermalink), new File(targetPermalink), true);
			}
		}
		
		String nextSourceDir = sourceDir.substring(0, sourceDir.lastIndexOf("\\"));
		nextSourceDir = nextSourceDir.substring(0, nextSourceDir.lastIndexOf("\\"));
		String nextTargetDir = targetDir.substring(0, targetDir.lastIndexOf("\\"));
		nextTargetDir = nextTargetDir.substring(0, nextTargetDir.lastIndexOf("\\"));
		
		String nextTargetDirName = QPDirectory.getDirectoryName(nextTargetDir);
		if(!nextTargetDirName.trim().equalsIgnoreCase("live") & (!nextTargetDirName.trim().equalsIgnoreCase("live-test")))
		{
			ensureDirectoyPermalinksAreCopied(configLocation, nextSourceDir, nextTargetDir, overwrite);
		}
	}
	
	//This method creates the PDF ID based on if it is a REG or a REG PIT
	public static String createRegOrRegPITDocID(String fileLocation) throws IOException
	{
		String parrentDir = fileLocation.substring(0, fileLocation.lastIndexOf("\\"));
		String permalinkID = CommonMethods.getPermalinkID(parrentDir + "\\directory.permalink", QPDisplayDebugInfo.displayDebugInfo).toLowerCase();
		if(permalinkID.contains("_dir"))
		{
			return permalinkID.substring(0, permalinkID.lastIndexOf("_dir"));
		}
		else
		{
			if(fileLocation.toLowerCase().contains("point in time"))
			{
				String fileName = (new File(fileLocation)).getName();
				Matcher pitDateMatcher = pitDatePattern.matcher(fileName);
				String pitDate = "";
				if(pitDateMatcher.find())
				{
					pitDate = pitDateMatcher.group(1).toString();
				}
				return permalinkID + "_" + pitDate.replace("-", "_");
			}
			else
			{
				throw new IOException("Unable to determine if the file located in: " + fileLocation + " is a REG or a REGPit when trying to create a new ID for this file...");
			}
		}
	}
}
