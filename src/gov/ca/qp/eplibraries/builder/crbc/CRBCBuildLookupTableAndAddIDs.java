package gov.ca.qp.eplibraries.builder.crbc;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.itextpdf.text.DocumentException;

import gov.ca.qp.eplibraries.builder.crbc.common.CommonMethods;
import qputility.config.QPConfigManager;
import qputility.enums.QPDisplayDebugInfo;
import qputility.exceptions.QPConfig_Exception;
import qputility.exceptions.QPGenericException;

public class CRBCBuildLookupTableAndAddIDs
{
	
	
	//entry point for the application
	//public void createXMLStructure(String rootDirectory, String outputLocation, QPDisplayDebugInfo displayDebugInfo) throws IOException, DocumentException
	public void createXMLStructure(String configFileLocation, String startTimeString) throws Exception
	{	
		BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(QPConfigManager.getInstance(configFileLocation).getStringValue("liveDirectory") + "\\hidden_crbcLookup.xml"),"UTF-8"));
		out.write(createXML(configFileLocation, QPConfigManager.getInstance(configFileLocation).getStringValue("liveDirectory"), QPDisplayDebugInfo.displayDebugInfo, startTimeString).toString());
		out.flush();
		out.close();
	}
	
	//creates the header info and calls out to start the creation of the XML lookup document.
	private StringBuilder createXML(String configFileLocation, String rootDirectory, QPDisplayDebugInfo displayDebugInfo, String startTimeString) throws Exception
	{
		StringBuilder sb = new StringBuilder();
		
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		sb.append("<?xml-stylesheet type=\"text/xsl\" href=\"http://styles.qp.gov.bc.ca/CRBC/crbc_currentPDFs.xsl\"?>");
		sb.append("<root id='crbcLookup' runTime='" + startTimeString + "'>");
		populateXML(configFileLocation, sb, new File(rootDirectory), displayDebugInfo);
		sb.append("</root>");
		
		return sb;
	}
	
	private StringBuilder populateXML(String configFileLocation, StringBuilder sb, File dir, QPDisplayDebugInfo displayDebugInfo) throws QPConfig_Exception, ParserConfigurationException, SAXException, Exception
	{
		File[] files = dir.listFiles();
		for(int i = 0; i < files.length; i ++)
		{
			File file = files[i];
	
			String fPath = file.getPath();
			if(!file.isFile())
			{
				String dirID = CommonMethods.getPermalinkID(fPath + "\\directory.permalink", QPDisplayDebugInfo.displayDebugInfo);
				if(dirID == null || dirID.toLowerCase().trim().equals(""))
				{
					throw new IOException("Unable to find an ID in directory.permalink file: " + fPath + "\\directory.permalink. Please ensure that the file exists and that the id format is id=\"xyz\"");
				}
				else
				{
					sb.append("<directory path=\"" + fPath + "\" id=\"" + dirID + "\">");
					
					sb = populateXML(configFileLocation, sb, file, displayDebugInfo);
					sb.append("</directory>");
				}
			}
			else
			{
				sb = handleDocuments(sb, file, fPath, QPConfigManager.getInstance(configFileLocation).getStringValue("pdfPassword"), displayDebugInfo);
			}
		}
		return sb;
	}

	private StringBuilder handleDocuments(StringBuilder sb, File file, String fPath, String pdfPassword, QPDisplayDebugInfo displayDebugInfo) throws Exception
	{
		String extension = fPath.toLowerCase().substring(fPath.lastIndexOf("."));
		String id = "";
		if(extension.equalsIgnoreCase(".pdf"))
		{
			id = CommonMethods.getOrSetPDFID(file.getPath(), pdfPassword, displayDebugInfo, null);
			return sb.append("<file path=\"" + fPath + "\" id=\"" + id + "\" lastModified=\"" + (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(file.lastModified()))) + "\" title=\"" + file.getName() + "\"/>");
		}
		else
		{
			return sb;
		}		
	}
}