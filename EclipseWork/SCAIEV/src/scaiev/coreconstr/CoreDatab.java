package scaiev.coreconstr;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Scanner;
import java.util.Set;

import scaiev.frontend.FNode;




public class CoreDatab {
	Boolean debug = true;
	HashMap <String, Core> cores = new HashMap<String, Core>();
	Set<String> supported_nodes = FNode.GetAllFrontendNodes();
	
	public CoreDatab(Set<String> supported_nodes) {
		System.out.println("To constrain HLS:\n"
				+ "- write constrain file \n"
				+ "- import it using ReadAvailCores\n"
				+ "OR\n"
				+ "- use AddCore() to add it manually in java \n");
	}
	
	public void SetDebug(boolean debug) {
		this.debug = debug;
	}
	
	public void ReadAvailCores (String path) {
		 try {
			 	File dir = new File(path);
			 	File[] directoryListing = dir.listFiles();
			 	if (directoryListing != null) {
			 		for (File core_file : directoryListing) {
			 			  int maxStage = 0;
					      Scanner myReader = new Scanner(core_file);
					      Boolean first_line = true;
					      String core_name = "";
					      HashMap<String,Node> nodes_of1_core = new HashMap<String,Node>();
					      Core new_core = new Core();
					      
					      while (myReader.hasNextLine()) {
					        String data = myReader.nextLine();
					        if(first_line)	{
					        	core_name = data;
					        	new_core.PutName(core_name);
					        	first_line = false;
					        }
					        if(debug)
					        	System.out.println("INFO. CoreDatab. Line in current constraint file is: "+data);
					        if(data.contains("Node")) {
					        	String[] parts = data.split(" ");
					        	String nodeName = parts[0].split("_")[1]; 
					        	if(!supported_nodes.contains(nodeName)) {
					        		if(!nodeName.contains("stall") && !nodeName.contains("flush")) {
						        		System.out.println("ERROR. CoreDatab. Node named: "+parts[0]+" not supported. Supported nodes are Node_* : "+supported_nodes.toString());
								 		System.exit(1);
					        		}
					        	}
					        	if(parts.length <5) {
					        		System.out.println("ERROR. CoreDatab. Node named: "+parts[0]+" does not have enough constraints (earliest, delay,latest,cost)");
					        		System.exit(1);
					        	}
					        	
					        	if(nodeName.contains("stall")) {
					        		nodes_of1_core.put(FNode.WrStall, new Node(Integer.parseInt(parts[1]),Integer.parseInt(parts[2]),Integer.parseInt(parts[3]),Integer.parseInt(parts[4]), parts[0].split("_")[1]));
					        		nodes_of1_core.put(FNode.RdStall, new Node(Integer.parseInt(parts[1]),Integer.parseInt(parts[2]),Integer.parseInt(parts[3]),Integer.parseInt(parts[4]), parts[0].split("_")[1]));					            
					        	}
					        	else if(nodeName.contains("flush")) {
					        		nodes_of1_core.put(FNode.WrFlush, new Node(Integer.parseInt(parts[1]),Integer.parseInt(parts[2]),Integer.parseInt(parts[3]),Integer.parseInt(parts[4]), parts[0].split("_")[1]));
					        		nodes_of1_core.put(FNode.RdFlush, new Node(Integer.parseInt(parts[1]),Integer.parseInt(parts[2]),Integer.parseInt(parts[3]),Integer.parseInt(parts[4]), parts[0].split("_")[1]));					            
					        	} else
					        		nodes_of1_core.put(nodeName, new Node(Integer.parseInt(parts[1]),Integer.parseInt(parts[2]),Integer.parseInt(parts[3]),Integer.parseInt(parts[4]), parts[0].split("_")[1]));
					            if(Integer.parseInt(parts[3]) > maxStage) {
					            	maxStage = Integer.parseInt(parts[3]) ;
					            }
					            	
					        }
					        if(data.contains("Flush")){
					        	String[] parts = data.split(" ");
					        	Boolean flush_of1_core = Boolean.parseBoolean(parts[1]);
					        	new_core.PutFlush(flush_of1_core);
					        }
					        if(data.contains("Datahaz")){
					        	String[] parts = data.split(" ");
					        	Boolean datahaz_of1_core = Boolean.parseBoolean(parts[1]);
					        	new_core.PutDatahaz(datahaz_of1_core);
					        }
					        
					        if(data.contains("Stall")){
					        	String[] parts = data.split(" ");
					        	int nr_stages = Integer.parseInt(parts[1]);
					        	int stalls_of1_core[] =  new int[nr_stages];
					        	for(int i=2;i<nr_stages+2;i++)
					        		stalls_of1_core[i-2]= Integer.parseInt(parts[i]);
					        	new_core.PutStall(stalls_of1_core);
					        }
					        	
					      }
					      new_core.PutNodes(nodes_of1_core);
					      new_core.maxStage = maxStage;
					      cores.put(core_name,new_core);
					      myReader.close();					    	  	
			 		}
			 	} else {
			 		System.out.println("CONSTRAIN. ERROR. Directory not found.");
			 		System.exit(1);
			 	}
		      
		    } catch (FileNotFoundException e) {
		      System.out.println("ERROR. CoreDatab. FileNotFoundException when reading core constraints from file.");
		      e.printStackTrace();
		    }
	}
	
	// Add new core with corresponding constraints
	public void AddCore (HashMap<String,Node> nodes,Boolean flush, Boolean datahaz, int[] stall, String name) {
		Core new_core =  new Core(nodes,flush,datahaz,stall,name);
		cores.put(name,new_core);
	}
	
	// Add new core with corresponding constraints & Write File in the given Path
	// 
	public void AddCoreWrFile (String path, HashMap<String,Node> nodes,Boolean flush, Boolean datahaz, int[] stall, String name) {
		Core new_core =  new Core(nodes,flush,datahaz,stall,name);
		cores.put(name,new_core);
		try {
			FileWriter myWriter = new FileWriter(path+"/Constraints_"+name);
			myWriter.write(name+"\n");
			for (String key : nodes.keySet()) {
				myWriter.write(key+" "+nodes.get(key).toString()+"\n");
				System.out.println("key = "+key+" value "+nodes.get(key));
			}
			myWriter.write("Flush "+flush+"\n");
			myWriter.write("Datahaz "+datahaz+"\n");
			myWriter.write("Stall "+stall.length+" ");
			for(int i=0;i<stall.length;i++)
				myWriter.write(stall[i]+" ");
			myWriter.close();
			System.out.println("INFO. CoreDatab. Successfully wrote constraints file for core: "+name);
	    } catch (IOException e) {
	    	System.out.println("ERROR. CoreDatab. An error occurred while writing constraints file.");
	    	e.printStackTrace();
		}
	}
		
	// Get constraints of a certain Core
	public Core GetCore (String name) {
		return cores.get(name);
	}
	
	
	public String  GetCoreNames () {
		String coreNames = "SCAIE-V supported cores are: \n";
		for(String name : cores.keySet())
			coreNames += "- "+name+"\n";
		return coreNames;
	}
	
}
