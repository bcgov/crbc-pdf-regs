package gov.ca.qp.eplibraries.builder.crbc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import gov.ca.qp.eplibraries.builder.crbc.common.CommonMethods;
import gov.ca.qp.eplibraries.builder.crbc.common.ReferenceObject;
import qputility.config.QPConfigManager;
import qputility.exceptions.QPGenericException;
import qputility.execute.QPExecute;
import qputility.io.QPDirectory;
import qputility.io.QPReader;

public class FileServerChecker
{
	public static void main(String[] args) throws Exception
	{
		
		String configLocation = args[0];
		
		FileServerChecker checker = new FileServerChecker();
		checker.check(configLocation, false);
	}
	
	/**
	 * This function checks the File Server Server's logs for any new updates in the CRBC folder (currently named "LegCouncil.txt")
	 * If updates are found, it starts the process to sync the files between the File Server server and the KP File Server's mirror directory
	 * @param configLocation
	 * @throws Exception
	 */
	public void check(String configLocation, boolean forceRun) throws Exception
	{
		//Determining if we should run now or wait.  Testing the last log entry date against the current.
		//This log file currently named "Leg Council.txt". 
		
		String fileServerLogLocation = QPConfigManager.getInstance(configLocation).getStringValue("fileServerLogLocation");
		String[] fileServerLogLines = QPReader.readFileLineByLine(fileServerLogLocation);
		if(fileServerLogLines.length <=1)
		{
			ApplicationController.sendEmailNotification("Error in CRBC-Scheduled Process", "For the run started at: " + ApplicationController.startTimeString + System.getProperty("line.separator") + "No lines were found in the logfile located at: " + fileServerLogLocation, configLocation, ApplicationController.buildLogLocation);
			System.out.println("No lines in log file.  Exiting...");
			throw new QPGenericException("No lines in log file.  Exiting...");
		}
		else
		{
			Pattern getLogEntryDatePattern = Pattern.compile("Date:([^\\s]+)\\s");
			String lastfileServerLogEntryLine = fileServerLogLines[fileServerLogLines.length-2];
			
			Matcher m = getLogEntryDatePattern.matcher(lastfileServerLogEntryLine);
			m.find();
			String lastLogEntryDate = m.group(1);
			
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy_HH:mm:ss:SSS");
			DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("dd/MM/yyyy_HH:mm:ss:SS");
			DateTimeFormatter formatter3 = DateTimeFormatter.ofPattern("dd/MM/yyyy_HH:mm:ss:S");
			LocalDateTime dateOfLastLogEntry = null;
			
			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
			Date pushedDate = null;
			
			try
			{
				dateOfLastLogEntry = LocalDateTime.parse(lastLogEntryDate, formatter);
			}
			catch (Exception e)
			{
				try
				{
					dateOfLastLogEntry = LocalDateTime.parse(lastLogEntryDate, formatter2);
				}
				catch (Exception e1)
				{
					try
					{
						dateOfLastLogEntry = LocalDateTime.parse(lastLogEntryDate, formatter3);
					}
					catch (Exception e2)
					{
						ApplicationController.sendEmailNotification("Error in CRBC-Scheduled Process", "For the run started at: " + ApplicationController.startTimeString + System.getProperty("line.separator") + "Unable to parse date format in  File Server Logs. Error: " + e2.getMessage(), configLocation, ApplicationController.buildLogLocation);
						System.out.println("Unable to parse date format in File Server Logs. Error: " + e2.getMessage());
						throw new Exception("Unable to parse date format in  File Server Logs. Error: " + e2.getMessage());
					}
				}
			}
			
			LocalDateTime runTime = LocalDateTime.now();
			Long minutes = ChronoUnit.MINUTES.between(dateOfLastLogEntry, runTime);
			
			if(minutes >= Long.parseLong(QPConfigManager.getInstance(configLocation).getStringValue("numberOfMinutesSinceLastLogEntry")))
			{
				//enough time has passed.  Run now.
				System.out.println("running..");			
				
				//Getting the log entry for the last successful run
				String crbcLastSuccessLogLocation = QPConfigManager.getInstance(configLocation).getStringValue("crbcLastSuccessLog");		
				String lastSuccessfulLogEntry = QPConfigManager.getInstance(crbcLastSuccessLogLocation).getStringValue("lastSuccesfulLine");
				
				//determining where the last successful log entry is in the  File Server log file.
				int lastSuccesfullLogEntryLineNumber = -1;
				if(lastSuccessfulLogEntry.trim().equalsIgnoreCase("") || forceRun == true)
				{
					lastSuccesfullLogEntryLineNumber = 0;
				}
				else
				{
					lastSuccesfullLogEntryLineNumber = findLastSuccesfullLogEntry(fileServerLogLocation, lastSuccessfulLogEntry, fileServerLogLines); 
				}
				
				String currentCRBCProcessLog = QPConfigManager.getInstance(configLocation).getStringValue("crbcProcessLogDir") + "CRBCProcessLog-" + (new SimpleDateFormat("yyyy_MM")).format(new Date()) + ".xml";
				 
				ArrayList<String> pendingLogLinesToProcess = new ArrayList<String>();
				
				for(int i = lastSuccesfullLogEntryLineNumber; i<fileServerLogLines.length; i++)
				{
					if(!fileServerLogLines[i].trim().equalsIgnoreCase(";;"))
					{
						pendingLogLinesToProcess.add(fileServerLogLines[i]);
						lastfileServerLogEntryLine = fileServerLogLines[i];
					}
				}
				
				System.out.println(pendingLogLinesToProcess.size() + " lines of logfile to process-------------");
				
				if(pendingLogLinesToProcess.size() > 0)
				{
					System.out.println("Gathering existing reference for deletion...");
					ArrayList<ReferenceObject> mirroredReferences = new ArrayList<ReferenceObject>();
					mirroredReferences = CommonMethods.getReferences(new File(QPConfigManager.getInstance(configLocation).getStringValue("mirrorDirectory")), mirroredReferences, QPConfigManager.getInstance(configLocation).getStringValue("liveDirectory"), QPConfigManager.getInstance(configLocation).getStringValue("mirrorDirectory"), true);
					System.out.println("Found " + mirroredReferences.size() + " CRBC.reference files.");
					if(mirroredReferences.size() > 0)
					{
						System.out.println("Removing existing CRBC.references...");
						CommonMethods.removeReferences(mirroredReferences);
						System.out.println();
						System.out.println("All references removed...");
					}
					System.out.println();
					System.out.println("---Starting Robocopy");
					
					//robocopy from the OLC dir to the mirrored Dir, and use the robocopy log to determine what files have changed/are new, then copy those to the Live dir on L
					final String roboCopyLogLocation = QPConfigManager.getInstance(configLocation).getStringValue("binDirectory") + "robocopyForFileServerSync.log";
					
					//Robocopying to the mirror dir
					//final int exitCode = QPExecute.runCMD("\"" + QPConfigManager.getInstance(configLocation).getStringValue("robocopyLocation") + "\" \"" + QPConfigManager.getInstance(configLocation).getStringValue("OLCFileDropDirectory") + "\" \"" + QPConfigManager.getInstance(configLocation).getStringValue("mirrorDirectory") + "\" /mir /NDL /ETA /LOG:\"" + roboCopyLogLocation + "\" /UNICODE", true, true, false, true);
					
					   String command = "cmd.exe /C chcp 65001 && \"" 
				                + QPConfigManager.getInstance(configLocation).getStringValue("robocopyLocation") + "\" \"" 
				                + QPConfigManager.getInstance(configLocation).getStringValue("OLCFileDropDirectory") + "\" \"" 
				                + QPConfigManager.getInstance(configLocation).getStringValue("mirrorDirectory") + "\" /mir /NDL /ETA /TS /UNICODE";

				        Runtime rt = Runtime.getRuntime();
				        Process proc = rt.exec(command);

				        // Capture the output and write it to a log file in UTF-8
				        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), "UTF-8"));
				        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(roboCopyLogLocation), "UTF-8"));
				        String line;
				        while ((line = reader.readLine()) != null) {
				            writer.write(line);
				            writer.newLine();
				        }

				        writer.close();
				        reader.close();

				        int exitCode = proc.waitFor();
					
					
					if(exitCode > 3)
					{
						throw new Exception("Robocopy failed with exit code = " + exitCode);
					}	
					
					String[] roboCopyLogLines = QPReader.readFileLineByLine(roboCopyLogLocation);
				
					Pattern pathPattern = Pattern.compile(".+?\\\\(--\\s[A-Z]\\s--.+?.(pdf|reference))");
										
					for(int i=0; i<roboCopyLogLines.length; i++)
					{
						String currentLine = roboCopyLogLines[i].trim();
						System.out.println(currentLine);
						
						Matcher pathMatcher = pathPattern.matcher(currentLine);
						if((currentLine.toLowerCase().startsWith("new file") || (currentLine.toLowerCase().startsWith("newer")) || currentLine.toLowerCase().startsWith("*extra file") || (currentLine.toLowerCase().startsWith("older"))) && (currentLine.toLowerCase().contains(".pdf")))  
						{	
							pathMatcher.find();
							String sourceFile = (QPConfigManager.getInstance(configLocation).getStringValue("mirrorDirectory") + "\\" + pathMatcher.group(1)).replace("/", "\\");
							String sourceDir = sourceFile.substring(0, sourceFile.lastIndexOf("\\")) + "\\";
							String pathToLiveVersion = (QPConfigManager.getInstance(configLocation).getStringValue("liveDirectory") + "\\" + pathMatcher.group(1)).replace("/Consolidated Regulations of British Columbia (PDF)", "").replace("/", "\\");
							String pathToLiveDirWithRegDir = (pathToLiveVersion.substring(0, pathToLiveVersion.lastIndexOf("\\")));
							String parentOfTargetDir = QPDirectory.getDirectoryName(pathToLiveDirWithRegDir);
							String fileName = sourceFile.substring(sourceFile.lastIndexOf("\\")).replace("\\", "");
							String pathToLiveDir = "";
							
							boolean isNewPITFile = false;
							
							//replacing the numbered dir for the regulation with the regulation title.
							if(parentOfTargetDir.equalsIgnoreCase("point in time") | parentOfTargetDir.equalsIgnoreCase("90_point in time"))
							{
								pathToLiveDir = pathToLiveDirWithRegDir.substring(0, pathToLiveDirWithRegDir.lastIndexOf("\\"));
								
								String pitDir = (sourceDir.substring(0, sourceDir.lastIndexOf("\\")));
								String folderContainingREG = (pitDir.substring(0, pitDir.lastIndexOf("\\"))); 
								pathToLiveDir = pathToLiveDir.substring(0, pathToLiveDir.lastIndexOf("\\")) + "\\" + CommonMethods.getREGNameFromPDFInDir(folderContainingREG) + "\\zz_Point in Time\\";
								isNewPITFile = true;
							}
							else
							{
								pathToLiveDir = pathToLiveDirWithRegDir.substring(0, pathToLiveDirWithRegDir.lastIndexOf("\\")) + "\\" + fileName.replace(".pdf", "") + "\\"; 
							}
							
							if((currentLine.toLowerCase().startsWith("new file") || (currentLine.toLowerCase().startsWith("newer")) || (currentLine.toLowerCase().startsWith("older"))) && (currentLine.toLowerCase().contains(".pdf")))  
							{	
								FileUtils.copyFile(new File(sourceFile), new File(pathToLiveDir + fileName), true);
								FileUtils.copyFile(new File(sourceDir + "directory.permalink"), new File(pathToLiveDir + "directory.permalink"), true);
							
								//test here for the existence of PIT files, and copy them along.  This is in case there was a rename to the Reg.
								if(!isNewPITFile)
								{
									boolean foundPITFiles = false;
									File[] files = new File(sourceDir).listFiles();
									for(int f=0;f<files.length;f++)
									{
										//this should be a PIT Dir
										if(files[f].isDirectory())
										{
											File[] pitFiles = files[f].listFiles();
											
											//Looking for any PDFs in the PIT dir
											for(int pf=0;pf<pitFiles.length;pf++)
											{
												if(pitFiles[pf].getPath().toLowerCase().contains(".pdf"))
												{
													//found a PIT file.  Make sure we copy the PIT dir
													foundPITFiles = true;
												}
											}
											if(foundPITFiles)
											{
												System.out.println("Copying PIT files from:");
												System.out.println(files[f]);
												System.out.println("to:");
												System.out.println(pathToLiveDir + "zz_Point in Time\\");
												FileUtils.copyDirectory(files[f], new File(pathToLiveDir + "zz_Point in Time\\"));
											}
										}
									}
								}
								
								CommonMethods.ensureDirectoyPermalinksAreCopied(configLocation, sourceDir, pathToLiveDir, false);
								
								pushedDate = new Date();
								System.out.println(dateFormat.format(pushedDate));
								System.out.println("Successfully pushed new file and dir to: " + pathToLiveVersion);
							}
							else
							{
								if(currentLine.toLowerCase().startsWith("*extra file") && (currentLine.toLowerCase().contains(".pdf")))
								{
									//test in here if the file being deleted is a reg.  If so remove the PITs as well.  This is to handle reg name changes.
									System.out.println("Deleting:");
									System.out.println(pathToLiveDir);
									if(!parentOfTargetDir.equalsIgnoreCase("point in time") && !parentOfTargetDir.equalsIgnoreCase("90_point in time")) {
										FileUtils.deleteDirectory(new File(pathToLiveDir));
									} else {
										//If the file being deleted is a Point in Time(PIT) PDF, just delete the file and not it's parent folder as there may be other PITs in the folder
									
										String filePath = pathToLiveDir + fileName;
										File toDelete = new File(filePath);
										
										boolean deleted = toDelete.delete();
										if (!deleted) {
											throw new IOException("File could not be deleted: " + filePath);
										}
									}
							
									System.out.println("Cleaning up orphaned dirs in:");
									System.out.println(pathToLiveDirWithRegDir.substring(0, pathToLiveDirWithRegDir.lastIndexOf("\\")));
									CommonMethods.testAndDeleteOrphanedDirs(pathToLiveDir);
									CommonMethods.testAndDeleteOrphanedDirs(pathToLiveDirWithRegDir.substring(0, pathToLiveDirWithRegDir.lastIndexOf("\\")));
								}
							}
						}
					}
					
					System.out.println("Gathering CRBC.reference files to resolve...");
					mirroredReferences = new ArrayList<ReferenceObject>();
					mirroredReferences = CommonMethods.getReferences(new File(QPConfigManager.getInstance(configLocation).getStringValue("mirrorDirectory")), mirroredReferences, QPConfigManager.getInstance(configLocation).getStringValue("liveDirectory"), QPConfigManager.getInstance(configLocation).getStringValue("mirrorDirectory"), false);
					System.out.println("Found " + mirroredReferences.size() + " CRBC.reference files.");
					System.out.println();
					System.out.println("Resolving the CRBC.references...");
					
					CommonMethods.resolveReferences(mirroredReferences, configLocation, pathPattern);
					
					System.out.println();
					System.out.println("All references resolved...");
					System.out.println();
					
					//if successful, log the lines above.
					QPConfigManager.getInstance(currentCRBCProcessLog, true).addListConfigEntry(runTime.toString(), pendingLogLinesToProcess);
					QPConfigManager.getInstance(crbcLastSuccessLogLocation).changeStringConfigValue("lastSuccesfulLine", lastfileServerLogEntryLine);
					QPConfigManager.getInstance(crbcLastSuccessLogLocation).changeStringConfigValue("lastSuccessfulDateTime", runTime.toString());
					System.out.println("All log lines processed successfully");
				}
				else
				{
					//ApplicationController.sendEmailNotification("CRBC-ADMIN - No new files to process", "For the run started at: " + ApplicationController.startTimeString + System.getProperty("line.separator") + System.getProperty("line.separator") + "There are no new files to process." + System.getProperty("line.separator"), configLocation, ApplicationController.buildLogLocation, true);
					System.out.println("No new log lines to process.  Exiting...");
					throw new QPGenericException("No new log lines to process.  Exiting...");
				}
				System.out.println("---Robocopy Complete");
				
				System.out.println("Ensuring static resouces are present...");
				CommonMethods.ensureStaticResourcesArePresent(configLocation);
			}
			else
			{
				ApplicationController.sendEmailNotification("CRBC-ADMIN - Not enough time since last File Server Commit", "For the run started at: " + ApplicationController.startTimeString + System.getProperty("line.separator") + "Not enough time has elapsed since the last file was added to the File Server site.  DateOfLastLogEntry=" + dateOfLastLogEntry, configLocation, ApplicationController.buildLogLocation, true);
				//exit as there hasn't been enough time since the last log entry.
				System.out.println("Not enough time has passed since the last log entry");
				System.out.println("dateOfLastLogEntry: " + dateOfLastLogEntry);
				System.out.println("Current Date:       " + runTime);
				System.out.println("...exiting.");
				throw new QPGenericException("Not enough time has passed since the last log entry");
			}
		}	
	}

	//determining where in the log file the last successful log entry sits.
	private int findLastSuccesfullLogEntry(String fileServerLogLocation, String lastSuccessfulLogEntry, String[] logLines) throws Exception
	{	
		for(int i = 0; i<logLines.length; i++)
		{
			if (logLines[i].equals(lastSuccessfulLogEntry))
			{
				return i + 1;
			}
		}
		return 0;
	}	
}
