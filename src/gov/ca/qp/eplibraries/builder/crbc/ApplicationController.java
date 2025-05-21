package gov.ca.qp.eplibraries.builder.crbc;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.mail.MessagingException;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import qputility.config.QPConfigManager;
import qputility.email.QPEmail;
import qputility.exceptions.QPConfig_Exception;
import qputility.exceptions.QPGenericException;
import qputility.execute.QPExecute;
import qputility.io.QPDirectory;

public class ApplicationController
{

	//entry point for the application.  Verify Args then run the thing!
	public static String startTimeString = "";
	public static String buildLogLocation = "";
	public static void main(String[] args) throws Exception
	{
		if (args.length < 1)
		{
			System.out.println("ERROR: Expected arguments of:");
			System.out.println("{path to buildConfigFile}");
		}
		else
		{
			try
			{
				//redirecting outputstreams to the log file.
				PrintStream orgStream = null;
				PrintStream fileStream = null;
				LocalDateTime startTime = LocalDateTime.now();
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
				startTimeString = startTime.format(formatter);
				
				buildLogLocation = QPDirectory.validatePath(QPConfigManager.getInstance(args[0]).getStringValue("binDirectory")) + "CRBC_RunLogs\\CRBC_Run_" + startTimeString.replace(":", ".") +".log";
				
				//sending out Start notification
				StringBuilder sb = new StringBuilder();
				sb.append("<h2>CRBC-Scheduled FileServer Checker</h2><hr/><h3>" + startTimeString + "</h3>");
				sb.append("<br/>");
				sb.append("<br/>Config file: " + args[0]);
				//sendEmailNotification("CRBC ADMIN - Starting Scheduled Run: " + startTimeString, sb.toString(), args[0], buildLogLocation, true);
				
				
				fileStream = new PrintStream(new FileOutputStream(buildLogLocation, false));
				orgStream = System.out;
				System.setOut(fileStream);
				System.setErr(fileStream);
				
				//This argument is used by the crbc-pdf-regs app run done by content editors (CE). Since their changes are not registered
				//by the FileServer folder, this app doesn't run by default when CEs make a change. So setting this argument forces the app to run.
				boolean forceRun = false;
				if (args.length >= 2 && args[1] != null) {
					if (args[1].trim().equalsIgnoreCase("forceRun")) {
						forceRun = true;
					}
				}
				System.out.println("Checking Logs for changes...");

				//Starting to do the work
				FileServerChecker checker = new FileServerChecker();
				checker.check(args[0], forceRun);
				System.out.println("Sync is done......");
				
				System.out.println("Pausing...");
				Thread.sleep(20000);
				System.out.println("Resumed...");
				System.out.println("______________________");
				
				System.out.println("Adding IDs to new or updated PDFs, and building the lookuptable...");
				CRBCBuildLookupTableAndAddIDs cp = new CRBCBuildLookupTableAndAddIDs();
				cp.createXMLStructure(args[0], startTimeString);
				
				System.out.println("-- All pre-processing complete.  Starting content build to TEST...");
				
				String optionalCommand = QPConfigManager.getInstance(args[0]).getStringValue("crbcBatFileForContentBuild");
				if(optionalCommand.toLowerCase().contains(".bat"))
				{
					optionalCommand = "call " + optionalCommand;
				}
				
				System.out.println("optionalCommand:");
				System.out.println(optionalCommand);
				
				final int exitCode = QPExecute.runCMD(optionalCommand, true, true, false, false);
				
				System.out.println("CRBC has completed with exit code: " + exitCode);
				
				ApplicationController.sendEmailNotification("CRBC-Scheduled Process has completed successfully", "For the run started at: " + ApplicationController.startTimeString + System.getProperty("line.separator") + "Please build the STATREG collection to test.bclaws to establish the regulation links, and then notify Legislative Council that they are free to test their CRBC Regs and links from the STATREG regulations on TEST. ", args[0], ApplicationController.buildLogLocation);
				
				System.setOut(orgStream);
				fileStream.close();
				System.out.println("CRBC has completed.");
			}
			catch (QPGenericException qpg)
			{
				System.out.println(qpg.getMessage());
				System.exit(0);
			}
			catch (Exception e)
			{
				System.out.println("\n\n -------- Something went wrong :( ----------\n");
				
				e.printStackTrace();

				ApplicationController.sendEmailNotification("Error in CRBC-Scheduled Process", "For the run started at: " + ApplicationController.startTimeString + System.getProperty("line.separator") + "Error Message: " + e.getMessage(), args[0], ApplicationController.buildLogLocation);
				
				System.exit(-1);
			}
		}
	}
	
	public static void sendEmailNotification(String title, String message, String configLocation, String buildLogLocation) throws MessagingException, QPConfig_Exception, ParserConfigurationException, SAXException, IOException
	{	
		sendEmailNotification(title, message, configLocation, buildLogLocation, false);
	}
	
	public static void sendEmailNotification(String title, String message, String configLocation, String buildLogLocation, boolean sendToAdmin) throws MessagingException, QPConfig_Exception, ParserConfigurationException, SAXException, IOException
	{
		message = message + System.getProperty("line.separator") + System.getProperty("line.separator") + System.getProperty("line.separator") + "<br/><strong>See the log located at: <a href=\"" + buildLogLocation + "\">" + buildLogLocation + "</a> for more details.</strong>";
		if(sendToAdmin)
		{
			QPEmail.sendMail(QPConfigManager.getInstance(configLocation).getStringValue("CRBCEmailFromAddress"), QPConfigManager.getInstance(configLocation).getListValue("CRBCAdminEmailRecipients"), title, message);
		}
		else
		{	
			QPEmail.sendMail(QPConfigManager.getInstance(configLocation).getStringValue("CRBCEmailFromAddress"), QPConfigManager.getInstance(configLocation).getListValue("emailRecipients"), title, message);
		}
	}
}
