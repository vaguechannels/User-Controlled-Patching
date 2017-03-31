import java.util.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

import com.marimba.intf.application.*;
import com.marimba.intf.castanet.*;
import com.marimba.intf.packager.*;
import com.marimba.intf.util.*;

/*
 * InventoryServiceUrl - Scanner Service Channel Url
 * patchserviceurl - Patch Service Channel Url
 * patchgroupurl - PatchGroup Url
 * timeout - Operation wait time
 * preinstall.arguments - Patch Service Arguments to Deploy Patch Group
 * preuninstall.arguments - Channel Arguments to Delete Patch Group(-remove)
 */

public class UserControlledPatch implements IScript 
{
	IApplicationContext ctx;
	IWorkspace workspace;
	ILauncher launcher;
	IConfig tunerConfig;
	IConfig config;
	long to = 0;
	String criticalloopmsg = "Installations were skipped because a possible loop condition was detected.";	
	static boolean DEBUG, is64bit = false;
	String validation = "passed";	
	
	@Override
	public int invoke(IScriptContext context, String[] args) 
	{
		ctx = (IApplicationContext) context.getFeature("context");
		config = ctx.getConfiguration();
		launcher = (ILauncher) ctx.getFeature("launcher");
		workspace = (IWorkspace) ctx.getFeature("workspace");
		tunerConfig = (IConfig) ctx.getFeature("config");
		
		String phase = null;
		switch (context.getPhase()) 
		{
			case IScriptContext.SCRIPT_PREINST:
				phase = "preinstall";
				break;
			
			case IScriptContext.SCRIPT_PREUNINST:
				phase = "preuninstall";
				break;
		}
		
		if (phase != null)		
		{
			String arguments = config.getProperty(phase + ".arguments");
			String patchgroupurl = config.getProperty("patchgroupurl");
			String timeout = config.getProperty("timeout");
			String patchgroup = patchgroupurl.substring(patchgroupurl.lastIndexOf("/")+1);			
			String debugflag = tunerConfig.getProperty("marimba.usercontrolledpatch.debug.enabled");
			if (debugflag!= null && debugflag.equals("true")) DEBUG = true;
			if (DEBUG) System.out.println(LogInfo() + "DebugInfo# " + "Debug flag enabled, So we will print all debug messages");
			String[] argv = null;
			if (arguments != null) 
			{
				StringTokenizer tok = new StringTokenizer(arguments, " ");
				argv = new String[tok.countTokens()];
				for (int i = 0; tok.hasMoreTokens(); i++) 	{	argv[i] = tok.nextToken();		}
			}
			
			System.out.println(LogInfo() + "Channel running in " + phase + " phase");
			if (timeout != null) {	to = Integer.parseInt(timeout) * 60 * 1000;	}
			
			try
			{
				if(phase != "preuninstall")	
				{
					IChannel ipatchservice = getIChannel("patchserviceurl");
					IChannel iscannerservice = getIChannel("inventoryservice");
					
					if (DEBUG) System.out.println(LogInfo() + "DebugInfo# " + "Locating Patch Service channel to check its current status");
					
					//Prep PatchService for Deployment
					if (channelcurrentstate(ipatchservice) == "CH_RUNNING")
					{
						System.out.println(LogInfo() + "Patch Service channel currently running. Attempting to stop it.......");
						while (channelcurrentstate(ipatchservice) == "CH_RUNNING")	
						{	
							launcher.stop(ipatchservice.getURL());	
						}
						System.out.println("\n" + LogInfo() + "Patch Service is now stopped.");
					}
									
					//Critical Loop Check
					if (checkIfResetRequired()) 
				  	{
						if (channelcurrentstate(ipatchservice) == "CH_RUNNING")
					  	{
					  		System.out.println(LogInfo() + "Patch Service channel currently running. Attempting to stop it.......");
					  		while (channelcurrentstate(ipatchservice) == "CH_RUNNING")	
					  		{	
					  			launcher.stop(ipatchservice.getURL());	
					  		}
				  			System.out.println("\n" + LogInfo() + "Patch Service is now stopped.");
					  	}
					  	System.out.println(LogInfo() + "Resetting Patch Service .........");
	                    executeReset();
	                } 
					
					System.out.println(LogInfo() + "Patch Service is ready to deploy " + patchgroup);
					
					if(!checkIfComplianceCalculated())
					{
						//Start Launcher
						IActive active = launcher.launch(ipatchservice.getURL(), argv, false, null);
						System.out.println(LogInfo() + patchgroup + " Deployment Started");
						
						//Verify the presense of PatchGroupKey & Update if necessary
						if((querypatchkey(patchgroup)==null))	addRegistryentries();
						
						if (waitFor(active, to)) 
						{
							System.out.println(LogInfo() + patchgroup + " Deployment Completed. Computing Patch Group Compliance....");
							
							//Patch Group Compliance Validation
							Thread.sleep(60000);//Sleeping for a minute.
							
							while (!checkIfComplianceCalculated())
							{
								while (channelcurrentstate(iscannerservice) != "CH_SUBSCRIBED" || channelcurrentstate(ipatchservice) != "CH_SUBSCRIBED" )
								{
									System.out.println(LogInfo() + "Service channels are running. Sleeping for a minute.");
									Thread.sleep(60000);//Sleeping for a minute.
								}
								if (checkIfComplianceCalculated()) continue;
								
								System.out.println(LogInfo() + "Unable to validate patch group compliance during the initial run. Triggering fullscan to capture " + patchgroup + " compliance");
								executefullScan();
								
							}
							System.out.println(LogInfo() + "Patch Group Compliance calculated. Validation: Passed");
							return 0;
						} 
						else 
						{
							System.out.println(LogInfo() + patchgroup + " Deployment Timed-out");					
							return 2;
						}
					}
					else
					{
						System.out.println(LogInfo() + "Patch Group " + patchgroup + " is already deployed. No patches to install/remove");
						return 0;
					}
				}
				else
				{
					System.out.println(LogInfo() + patchgroup + " will be processed with " + arguments + " argument and can't wait beyond " + timeout + " minutes");
					System.out.println(LogInfo() + "Verifying workspace for the presence of " + patchgroup);
					IChannel channeltoremove = workspace.getChannel(patchgroupurl);
					if (channeltoremove!= null)
					{
						System.out.println(LogInfo() + "IChannel " + patchgroup + " found in Workspace, So will process " + arguments + " to it now......");
						
						IActive active = launcher.launch(patchgroupurl, argv, false, null);	
						if (waitFor(active, to)) 
						{
							//Removing Channel from Workspace
							launcher.remove(patchgroupurl);
							while (channeltoremove.getChannelStatus() == IChannel.CH_REMOVED) 
							{
								System.out.println(LogInfo() + patchgroup + " removed from Workspace");
							}
							System.out.println(LogInfo() + "Operation " + arguments + " On "+ patchgroup + " Completed Successfully");
						}
						else 
						{
							System.out.println(LogInfo()+ "timed out running " + channeltoremove);
							return 2;
						}
					}
					else
					{
						System.out.println(LogInfo() + "Channel " + patchgroup + " doesn't exist in Workspace, So moving on......");
					}
					
					System.out.println(LogInfo() + "Channel Operations Completed. Begin Validation.......... ");
					Thread.sleep(10000);//Sleeping for 10 seconds.
					//Channel Validation Loop
					IChannel channeltovalidate = workspace.getChannel(patchgroupurl);
					if (channeltovalidate!= null) 	{ validation = "failed"; }
					
					System.out.println(LogInfo() + "Overall Validation Status: " + validation);
					
					if (validation!=null && validation.equals("failed"))
					{
						System.out.println(LogInfo() + "Validation failed. Exiting Channel As " + phase + " Failure");
						return 1;
					}
					else
					{
						System.out.println(LogInfo() + "All listed Channels Already Removed From Tuner Workspace");
						System.out.println(LogInfo() + "Successfully Validated " + patchgroup + " removal. " + phase + " Phase Completed Successfully");
						return 0;
					}
					//Exiting Loop Successfully
				
				}
			}
			catch (NumberFormatException | InterruptedException e) {e.printStackTrace();}
			return 1;
		}
		else 
		{
			System.out.println(LogInfo() + "Channel not running in Install or Repair mode, exiting the channel now....");
			return 0;
		}
	}
	
	
	
	private boolean checkIfResetRequired() 
	{
		IChannel patchchn = getIChannel("patchserviceurl");
        if (patchchn != null) 
        {
        	if (DEBUG) System.out.println(LogInfo() + "DebugInfo# " + "Using Patch Service: " + patchchn.getURL());
            try 
            {
                String compValue = patchservicecompliance(patchchn);
                if (DEBUG) System.out.println(LogInfo() + "DebugInfo# " + "Compliance Value = " + compValue);
                if (compValue != null && compValue.equals(criticalloopmsg)) 
                {
                    System.out.println(LogInfo() + "Patch installations are stuck in a critical loop. We need to reset PatchService...");
                    return true;
                } 
                else 
                {
                    System.out.println(LogInfo() + "PatchService - No Critical Loop to report.");
                    return false;
                }
            } 
            catch (IOException e) {	e.printStackTrace();}
        } 
        else 
        {
            System.out.println(LogInfo() + "Patch Service couldn't be located in the tuner workspace.");
        }
        return false;
    }
	
	private boolean checkIfComplianceCalculated()
	{
		IChannel ipatchgroup = getIChannel("patchgroupurl");
		if (ipatchgroup != null) 
		{
			try 
            {
				String patchgrpcompliance = patchgroupcompliance(ipatchgroup);
				if (patchgrpcompliance != null) 
					return true; 
	            else 
	            	return false;
            }
			catch (IOException e) {	e.printStackTrace();}
		}
		else 
        {
			if (DEBUG) System.out.println(LogInfo() + "DebugInfo# " + "Patch Group couldn't be located in the tuner workspace.");            
        }
		return false;
	}
	

	//Lookup Channels
	private IChannel getIChannel(String channelname) 
	{
		String channelurl = (config != null) ? config.getProperty(channelname) : null;
		if(DEBUG)System.out.println(LogInfo() + "DebugInfo# " + channelname + " retrived from property: " + channelurl);
		IChannel iChannelUrl = null;
		
		if (channelurl!= null)	
		{	
			iChannelUrl = workspace.getChannel(channelurl);	
			if(DEBUG)System.out.println(LogInfo() + "DebugInfo# " + channelname +  " Found in Workspace" + ":" + iChannelUrl);
		}
		
		if (iChannelUrl == null) 
		{
			if(DEBUG)System.out.println(LogInfo() + "DebugInfo# " + "Iterating the workspace to fetch " + channelname + " channel.....");
            IChannel[] channels = workspace.getChannels();
            for (int i = 0; i < channels.length; i++) 
        	{
        		if (channels[i].getPath().toLowerCase().endsWith(channelname)) 
	                {	iChannelUrl = channels[i];	}
        	}
            if(DEBUG)System.out.println(LogInfo() + "DebugInfo# " + channelname +  " Found in Workspace" + ":" + iChannelUrl);;
		}
		return iChannelUrl;		
	}
	
	//PatchGroup Compliance
	private String patchgroupcompliance(IChannel patchgroupurl)throws IOException
	{
		String compliancevalue = null;
		try
		{
			File wsRoot = new File(tunerConfig.getProperty("runtime.workspace.dir"));
            String num = patchgroupurl.getProperty("count");
            String patchgroup = patchgroupurl.getURL().substring(patchgroupurl.getURL().lastIndexOf("/") + 1);
            File chDir = new File(wsRoot, "ch." + num);
            File channel_txt = new File(chDir, "channel.txt");	
            if (channel_txt.exists()) 
            {
                BufferedReader reader = new BufferedReader(new FileReader(channel_txt));
                String line = null;
                while ((line = reader.readLine()) != null) 
            	{
            		if (line.startsWith("marimba.compliance.status.ComplianceLevel"))
	                {	
            			compliancevalue = line.substring(line.indexOf("=") + 1, line.length());	
            			System.out.println(LogInfo() + "ComplianceLevel of PatchGroup: " + patchgroup + " " + compliancevalue);
            		}
            	}
                	reader.close();
            }
            else 
        	{
            	System.out.println(LogInfo() + "channel.txt file is missing in the channel directory");
            	return compliancevalue;
        	}
		}
		catch (IOException io) {io.printStackTrace();}
		return compliancevalue;		
	}
	
	//PatchService Critical Loop Check
	private String patchservicecompliance(IChannel patchserviceurl)throws IOException
	
	{
		String compliancevalue = null;
		try
		{
			File wsRoot = new File(tunerConfig.getProperty("runtime.workspace.dir"));
            String num = patchserviceurl.getProperty("count");
            File chDir = new File(wsRoot, "ch." + num);
            File application_txt = new File(chDir, "application.txt");	
            if (application_txt.exists()) 
            {
                BufferedReader reader = new BufferedReader(new FileReader(application_txt));
                String line = null;
                while ((line = reader.readLine()) != null) 
            	{
            		if (line.startsWith("compliance"))
	                {	
            			compliancevalue = line.substring(line.indexOf("=") + 1, line.length());	
            			if(DEBUG) System.out.println(LogInfo() + "DebugInfo# " + "Retrieved Patch Service Compliance Value: " + compliancevalue);
            		}
            	}
                reader.close();
            }
            else 
        	{
            	System.out.println(LogInfo() + "Application.txt file is missing in the channel directory");
            	return compliancevalue;
        	}
		}
		catch (IOException io) {io.printStackTrace();}
		return compliancevalue;		
	}
	
	//Registry Entries For Reporting
	public static String RegMacro() 
	{
		// Windows OS Verification
		String Macro = null;
		if (System.getProperty("os.name").contains("Windows")) 
		{
			is64bit = (System.getenv("ProgramFiles(x86)") != null);
			if (is64bit == false) 
			{	
				if(DEBUG)System.out.println(LogInfo() + "DebugInfo# " + "OS Architecture: 32-bit OS");
				Macro = "HKLM\\Software\\";
			} 
			else 
			{	
				if(DEBUG)System.out.println(LogInfo() + "DebugInfo# " + "OS Architecture: 64-bit OS");
				Macro = "HKLM\\Software\\Wow6432Node\\";
			}
		}
		else 
		{
			is64bit = (System.getProperty("os.arch").indexOf("64") != -1);
		}
		return Macro;
	}
	
	//Validate the presence of  PatchGroup Information
	public static String querypatchkey(String Key) 
	{
		String key = "" , out = "";
		String Node = RegMacro() + "Marimba\\UserControlledPatch";
		String[] checkregkey = {"REG", "QUERY", Node, "/v" , Key	};
		
		ProcessBuilder pb = new ProcessBuilder(checkregkey);
		Process process;
		try 
		{
			process = pb.start();
			BufferedReader in = new BufferedReader( new InputStreamReader( process.getInputStream() ) );
	        while ( ( out = in.readLine() ) != null ) 
	        {
	        	if(DEBUG)System.out.println(LogInfo() + "DebugInfo# " + "Input & Output Information readLine " + out);
	        	if (out.matches("(.*)\\s+REG_(.*)")) { break; }
	        }
	        in.close();
	        
	        String str[] = out.split(" ");
	        //Capture the Reg Query Output
	        if(DEBUG)System.out.println(LogInfo() + "DebugInfo# " + "Reg Query Output:" + out);
	        
	        //Retrieve Registry Value to enumerate Patch Group
	        int b = 0;
	        for (int a=0; a < str.length; a++) 
	        {
	            if ( str[a].matches("\\S+") ) 
	            {
	                switch (b) 
	                {	
	                	case 0: key = str[a]; break;	
	                }
	                b++;
	            }
	        }
	        if(DEBUG)	System.out.println(LogInfo() + "DebugInfo# " + "Retrieved PatchGroup Registry Key:" + key);
	        System.out.println(LogInfo() + "PatchGroup Registry Key already exists.");
	        return key;
	        
		} 
		catch(NullPointerException | IOException e)
        {
        	System.out.println(LogInfo() + "PatchGroup Registry Key doesn't exist. Updating now....");
        	return null;
        }
    }
	
	//Update Registryentries for Patch Group Information
	private void addRegistryentries()
	{
		String patchgroupurl = config.getProperty("patchgroupurl");
		String patchgroup = patchgroupurl.substring(patchgroupurl.lastIndexOf("/") + 1);
		String currenttime = new java.text.SimpleDateFormat("dd/MMM/yyyy h:mm:ss a z ").format(new Date());
		String[] addregkey = {"REG", "ADD", RegMacro() + "Marimba\\UserControlledPatch", "/v", patchgroup, "/d", currenttime + "; " + System.getProperty("user.name"), "/f"};
		//Command To Update Registry Entries
		if(DEBUG)	
			{	
				System.out.print(LogInfo() + "DebugInfo# " + "Add Registry Key Command: " );
				for (String element : addregkey) {System.out.print(element + " ");}
				System.out.println();
			}
		
		ProcessBuilder pb = new ProcessBuilder(addregkey);
		try	{pb.start();} 
		catch (IOException e)	{e.printStackTrace();}
		
		System.out.println(LogInfo() + "Registry entries updated with currently logged-on user & time");
	}
	
	//Full Scan
	private void executefullScan()
	{
		String scannerServiceUrl = getIChannel("inventoryservice").getURL();
		String [] Arg = {"-fullScan"};
		IActive scannerServiceInst = launcher.start(scannerServiceUrl, Arg, false);
		if (waitFor(scannerServiceInst, to)) 
        {
    		System.out.println(LogInfo() + "Scanner service ran successfully in fullScan mode");
        }
	}
	
	//Reset PatchService
	private void executeReset() 
	{
        String patchServiceUrl = getIChannel("patchserviceurl").getURL();
        String [] resetArg = {"-reset"};
        IActive patchServiceInst = launcher.start(patchServiceUrl, resetArg, false);
        if (waitFor(patchServiceInst, to)) 
        {
    		System.out.println(LogInfo() + "Patch service Resetted successfully");
        }
    }
	
	//Check Channel State
	private String channelcurrentstate(IChannel url)
	{
		String currentstate = null;
		int currentvalue = url.getChannelStatus();
		switch (currentvalue)
		{
			case 3100: currentstate = "CH_SUBSCRIBING"; 	break;
			case 3101: currentstate = "CH_UNSUBSCRIBED";	break;
			case 3102: currentstate = "CH_UPDATING";		break;
			case 3103: currentstate = "CH_CHECKING";		break;
			case 3104: currentstate = "CH_SUBSCRIBED"; 		break;
			case 3105: currentstate = "CH_RUNNING";			break;
			case 3106: currentstate = "CH_REMOVED";			break;		
		}
		return currentstate;
	}
	
	//Wait for the IActive to die.
	private boolean waitFor(IActive active, long timeout) 
	{
		try 
		{
			Thread timer = new Thread(new Timer(timeout, Thread.currentThread()));
			timer.start();
			while (active.getApplicationStatus() != IActive.APP_DEAD) 
			{	Thread.sleep(1000);	}
			timer.interrupt();
		} 
		// bad news: we’ve timed out!
		catch (InterruptedException e) 
		{
			active.kill();
			return false;
		}
		return true;
	}
		
	//Timestamp Logging
	public static String LogInfo() 
	{
		String timestamp = new java.text.SimpleDateFormat("[dd/MMM/yyyy HH:mm:ss Z] ").format(new Date()); 
		String log = timestamp	+ "- " + System.getProperty("user.name") + " ";
		return log;
	}

	class Timer implements Runnable 
	{
		private long to;
		private Thread client;
		public Timer(long to, Thread client) 
		{
			this.to = to;
			this.client = client;
		}
		public void run() 
		{
			try 
			{	Thread.sleep(to);	} 
			// the client thread will interrupt us when done
			catch (InterruptedException e) 
			{	return;		}
			// the client thread hasn’t awakened yet, so interrupt it.
			client.interrupt();
		}
	}
	
}
