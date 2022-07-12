package scaiev.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;


/*
 * Class for writing files. 
 */
public class FileWriter {
	private HashMap<String, LinkedHashMap<ToWrite,String>> update_core = new HashMap<String, LinkedHashMap<ToWrite,String>>(); // HashMap<FileName, LinkedHashMap<Grep,ToWrite>>, order is important so that declarations of new signals will be before theser are used in assigns
	public String tab = "    ";
	public int nrTabs = 0;
	
	public void  UpdateContent(String file, String grep, ToWrite add_text) {
		add_text.AllignText(tab.repeat(nrTabs));
		UpdateCorePut(file, grep, add_text);
	}
	

	
	public void  UpdateContent(String file, String text) { // for files which do not grp/replace, but you just add text in a new file
		String grep = " ";
	    ToWrite add_text = new ToWrite(text, false, ""); // new file => no module => inmodule is true and before is false
	    add_text.AllignText(tab.repeat(nrTabs));
		UpdateCorePut(file, grep, add_text);
	}
	

	// the greped text is replaced
	public void  ReplaceContent(String file, String grep, ToWrite add_text) {
		add_text.replace = true;
		add_text.AllignText(tab.repeat(nrTabs));
		UpdateCorePut(file, grep,add_text);
	}

	private void UpdateCorePut (String file, String grep, ToWrite add_text) {
		if(update_core.containsKey(file))
			update_core.get(file).put(add_text,grep);
		else {
			LinkedHashMap<ToWrite,String> add = new LinkedHashMap<ToWrite,String>();
			add.put(add_text,grep);
			update_core.put(file,add);
			
		}
		
	}
	public void WriteFiles(String userLanguage) {
		for(String key : update_core.keySet()) {
			WriteFile(update_core.get(key),key,userLanguage);			
		}
	}

	private void WriteFile(LinkedHashMap<ToWrite,String> insert, String file, String userLanguage) {
		 System.out.println("INFO. FILEWRITER. Updating "+file);
		 File inFile = new File(file);
		 File outFile = new File("tempConfig.tmp");
		 HashMap <String,HashMap<String, String>> shortLanguageDict = CreateShortDict();
		 // If file does not exist, create it and add an empty character to be found by grep
	     if(!inFile.exists()) {
	    	 try {
				FileOutputStream fos_in = new FileOutputStream(inFile);
				PrintWriter write_to_in = new PrintWriter(fos_in);
				write_to_in.println(" ");
				write_to_in.flush();
				write_to_in.close();
	    	 } catch (IOException e) {
	 			System.out.println("ERROR. Error writing to the new file");
				e.printStackTrace();
			}
	     }
	    	 
		// input
		FileInputStream fis;
		try {
			fis = new FileInputStream(inFile);
			BufferedReader in = new BufferedReader(new InputStreamReader(fis));
			 // output         
			FileOutputStream fos = new FileOutputStream(outFile);
			PrintWriter out = new PrintWriter(fos);
			String currentLine;
					
			while ((currentLine = in.readLine()) != null) {
				boolean replace = false;
				ArrayList<String> before = new ArrayList<String>();
				ArrayList<String> after = new ArrayList<String>();
				// future optimization: make a map with strings to be inserted. Each time a string was inserted, make valid (take care if strings must be inserted multiple times. Stop searching if all strings inserted. 
				for(ToWrite key_text : insert.keySet()) {
					if(currentLine.contains(shortLanguageDict.get(userLanguage).get("module")) && !currentLine.contains(shortLanguageDict.get(userLanguage).get("endmodule"))  && !key_text.in_module.contentEquals("")) {
						if(currentLine.contains(" "+key_text.in_module+" ") || currentLine.contains(key_text.in_module+"(") || currentLine.contains(" "+key_text.in_module+"\n")|| currentLine.contains(" "+key_text.in_module+";") ) {// space to avoid grep stuff like searching for pico and finding picorv32_axi
							key_text.found_module = true;
						} else 
							key_text.found_module = false;
					}
					

					if(key_text.prereq)
						if(currentLine.contains(key_text.prereq_text) && (key_text.found_module)) {
							key_text.prereq_val = true;
						}
					
					if(key_text.text!="" && currentLine.contains(insert.get(key_text)) && key_text.prereq_val  && (key_text.found_module)) {
						key_text.prereq_val = false; // that's why no ""|| !key_text.prereq)"" in line above
						char[] chars = currentLine.toCharArray();
						/*
						char first_letter = ' ';
						for(int i=0;i<chars.length;i++) {
							if(!Character.isSpaceChar(chars[i]) && !Character.isWhitespace(chars[i])) {
									first_letter = chars[i];
									break;
							}
						}*/
						int index = currentLine.indexOf(currentLine.trim());
						char first_letter = currentLine.charAt(index);
						// char first_letter = insert.get(key_text).toCharArray()[0];	// was before
						String[] arrOfStr;
						if(first_letter==')')
							arrOfStr = currentLine.split("\\)", 2); 
						else if (first_letter=='(')
							arrOfStr = currentLine.split("\\(", 2); 
						else
							arrOfStr = currentLine.split(Character.toString(first_letter), 2); 
						if(key_text.before) {
							before.add(arrOfStr[0]+key_text.text.replaceAll("\n", "\n"+arrOfStr[0]));
						} else
							after.add(arrOfStr[0]+key_text.text.replaceAll("\n", "\n"+arrOfStr[0]));
						if(key_text.replace) // if any of the new textx actually wants to replace this line
							replace = true;	
						
					}
					if(currentLine.contains(shortLanguageDict.get(userLanguage).get("endmodule"))&& !key_text.in_module.contentEquals(""))	
						key_text.found_module = false;
					// If interface already exists flag err and exit
				}
				for(String text : before)
					out.println(text);
				if(!replace)
					out.println(currentLine);
				for(String text : after)
					out.println(text);		
				
			}
			
			
			out.flush();
			out.close();
			in.close();
		    
		    inFile.delete();
		    outFile.renameTo(inFile);
		} catch (FileNotFoundException e) {
			System.out.println("ERROR. File not found exception");
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("ERROR. Error reading the file");
			e.printStackTrace();
		}	
	}
	
	private HashMap <String,HashMap<String, String>>  CreateShortDict() {
		HashMap <String,HashMap<String, String>> dict = new HashMap <String,HashMap<String, String>> (); 
		HashMap<String, String> spinalDict = new HashMap<String, String>(); 
		spinalDict.put("module", "class");
		spinalDict.put("endmodule", "}");
		dict.put(Lang.SpinalHDL, spinalDict);
		
		HashMap<String, String> verilog = new HashMap<String, String>(); 
		verilog.put("module", "module");
		verilog.put("endmodule", "endmodule");
		dict.put(Lang.Verilog, verilog);
		
		HashMap<String, String> vhdl = new HashMap<String, String>(); 
		vhdl.put("module", "component");
		vhdl.put("endmodule", "end architecture");
		dict.put(Lang.VHDL, vhdl);
		
		HashMap<String, String> bluespec = new HashMap<String, String>(); 
		bluespec.put("module", "module");
		bluespec.put("endmodule", "endmodule");
		dict.put(Lang.Bluespec, bluespec);
		return dict;
	}
	
		
	
}
