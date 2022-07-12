package scaiev.util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;

import scaiev.backend.BNode;
import scaiev.backend.CoreBackend;
import scaiev.frontend.SCAIEVInstr;

public class Bluespec extends GenerateText {
	FileWriter toFile = new FileWriter();
	public String tab = "    ";

	public Bluespec(FileWriter toFile, CoreBackend core) {
		// initialize dictionary 
		dictionary.put(Words.module,"module");
		dictionary.put(Words.endmodule,"endmodule");
		dictionary.put(Words.reg,"Reg");
		dictionary.put(Words.wire,"Wire");		
		dictionary.put(Words.assign,"");
		dictionary.put(Words.assign_eq,"<=");
		dictionary.put(Words.logical_or,"||");
		dictionary.put(Words.bitwise_or,"|");
		dictionary.put(Words.logical_and,"&&");
		dictionary.put(Words.bitwise_and,"&");
		dictionary.put(Words.bitsselectRight,"]");
		dictionary.put(Words.bitsselectLeft,"[");
		dictionary.put(Words.bitsRange,":");
		dictionary.put(Words.ifeq,"==");
		
		this.toFile = toFile;
		tab = toFile.tab;
		this.coreBackend = core;
	}
	
	@Override 
	public String getLang () {
		return Lang.Bluespec;		
	}
	
	public void  UpdateInterface(String top_module,String operation, String instr, int stage, boolean top_interface, boolean assigReg) {
		// Update interf bottom file
		System.out.println("INTEGRATE. DEBUG. stage = "+stage+" operation = "+operation);
		String bottom_module = coreBackend.NodeAssignM(operation, stage);
		String current_module = bottom_module;
		String prev_module = "";
		String instName = "";
		while(!prev_module.contentEquals(top_module)) {
			// Add interface OR local signal
			if(!current_module.contentEquals(top_module) || top_interface) {  // top file should just instantiate signal in module instantiation and not generate top interface if top_interface = false
				System.out.println("INTEGRATE. DEBUG. Inserting in interface file with prereq "+ current_module);
				String additional_text  = "(*always_enabled*) " ;
				if(current_module.contentEquals(top_module))
					additional_text = "(*always_enabled *)";
				this.toFile.UpdateContent(coreBackend.ModInterfFile(current_module), "endinterface",new ToWrite(additional_text+CreateMethodName(operation,stage,instr,false)+";\n",true,false,"interface",true));		
			} else if(current_module.contentEquals(top_module)) {
				if(assigReg)
					this.toFile.UpdateContent(coreBackend.ModFile(current_module), ");",new ToWrite(CreateDeclReg(operation, stage, instr),true,false, "module "+current_module+" "));	
				else
					this.toFile.UpdateContent(coreBackend.ModFile(current_module),");",new ToWrite(CreateDeclSig(operation, stage, instr),true,false, "module "+current_module+" "));	
			}
			
			// Find out inst name 
			if(!prev_module.isEmpty()) {
				FileInputStream fis;
				try {
					fis = new FileInputStream(coreBackend.ModFile(current_module));
					BufferedReader in = new BufferedReader(new InputStreamReader(fis));
					String currentLine;						
					while ((currentLine = in.readLine()) != null) {
						if(currentLine.contains(prev_module) && currentLine.contains("<-")) {
							int index = currentLine.indexOf(currentLine.trim());
							char first_letter = currentLine.charAt(index);
							String[]  words = currentLine.split("" + first_letter,2);
							String[]  words2 = words[1].split("<-",2);
							String[] words3 = words2[0].split(" ");
							
							instName = " ";
							int  i = 1;
							while(instName.length()<=1){
								instName = words3[i];
								i++;
						    }
						}
					}
					in.close();
				}	catch (IOException e) {
					System.out.println("ERROR. Error reading the file");
					e.printStackTrace();
				}	
			}
			// Assign OR Forward
			if(prev_module.contentEquals("")) { // no previous files => this is bottom file
				// Connect signals to top interface. Assigns
				String assignText = CreateMethodName(operation,stage,instr,false)+";\n";
				String assignValue = coreBackend.NodeAssign(operation, stage); 
				if(assignValue.isEmpty()) {
					assignValue = CreateLocalNodeName(operation,stage, instr);
					// Local signal definition 
					this.toFile.UpdateContent(coreBackend.ModFile(current_module), ");",new ToWrite(CreateDeclSig(operation, stage, instr),true,false,"module "+current_module+" "));
					
				}
				if(coreBackend.NodeIn(operation, stage))
					assignText += tab+assignValue +" "+ dictionary.get("assign_eq") + " x;\n";
				else 
					assignText += tab+"return "+assignValue +";\n";
				assignText += "endmethod\n";
				// Method definition
				this.toFile.UpdateContent(coreBackend.ModFile(current_module), "endmodule",new ToWrite(assignText,true,false,"module "+current_module+" ",true));
				
			} else if(!current_module.contentEquals(top_module) || top_interface){
				String forwardSig = CreateMethodName(operation,stage,instr,true)+" = "+instName + ".met_"+CreateNodeName(operation, stage,instr);
				if(coreBackend.NodeIn(operation, stage))
					forwardSig += "(x);\n";
				else 
					forwardSig += ";\n";
				this.toFile.UpdateContent(coreBackend.ModFile(current_module), "endmodule",new ToWrite(forwardSig,true,false,"module "+current_module+" ",true));				 
						
			}
			prev_module = current_module;
			if(!current_module.contentEquals(top_module) && !coreBackend.ModParentName(current_module).equals(""))
				current_module = coreBackend.ModParentName(current_module);	
			else 
				break;
		}
		
	}
	
	/**
	 * Generates text like: Reg #(Bit#(1))  signalName    <- mkReg (0);
	 * signalName created from <operation,  stage,  instr>
	 */
	public String CreateDeclReg(String operation, int stage, String instr) {
		String decl = "";
		String size = "";
		String init = "0";
		String input = "_o";
		if(coreBackend.NodeIn(operation, stage))
			input = "_i";
		if(coreBackend.NodeDataT(operation, stage).contains("Bool") ) 
			init = "False";
		else  
			size += "#("+coreBackend.NodeSize(operation, stage)+")";
		
		decl = "Reg #("+coreBackend.NodeDataT(operation, stage) + size+")"+CreateNodeName(operation,stage,instr).replace(input,"_reg")+" <- mkReg("+init+");\n";
		return decl;
	}
	
	/**
	 * Generates text like: Wire #(Bit#(1))  signalName    <- mkDWire (0);
	 * signalName created from <operation,  stage,  instr>
	 */
	public String CreateDeclSig(String operation, int stage, String instr) {
		String decl = "";
		String size = "";
		String init = "0";
		if(coreBackend.NodeDataT(operation, stage).contains("Bool") ) 
			init = "False";
		else  
			size += "#("+coreBackend.NodeSize(operation, stage)+")";
		
		decl = "Wire #("+coreBackend.NodeDataT(operation, stage) + size+") "+CreateLocalNodeName(operation,stage,instr)+" <- mkDWire("+init+");\n";
		return decl;	
	}
	
	/**
	 * Declares all Regs required for spawn. Returns string with text of declarations
	 * @param ISAXes
	 * @param stage
	 * @return
	 */
	
	public String CreateSpawnDeclareSigs(HashSet<String> ISAXes, int stage, String node, boolean withFire) {
		String declare = "// ISAX : Declarations of Signals required for spawn logic for "+node+"\n";
		String valid_node = node+"_valid";
		String addr_node = node+"_addr";
		if(node.contains(BNode.RdMem_spawn.split("d")[1])) {
			valid_node = BNode.Mem_spawn_valid;
			addr_node = BNode.Mem_spawn_addr;
		}
		for(String ISAX  :  ISAXes) {
			declare += CreateDeclReg(valid_node, stage,ISAX);
			if(!node.contains(BNode.RdMem_spawn))
				declare += CreateDeclReg(node, stage,ISAX); // read mem does not input any data which has to be stored in reg
			declare += CreateDeclReg(addr_node, stage,ISAX);
		}
		
		// stall fire signals
		if(withFire) {
			String ISAX_fire_r = BNode.ISAX_fire_regF_reg;
			String ISAX_fire_s = BNode.ISAX_fire_regF_s; 
			String ISAX_fire2_r = BNode.ISAX_fire2_regF_reg; 
			//String ISAX_sum_spawn_s = BNode.ISAX_sum_spawn_regF_s; 	
			String ISAX_spawnStall_s = BNode.ISAX_spawnStall_regF_s; 
			if(node.contains("Mem")) {
				ISAX_fire_r = BNode.ISAX_fire_mem_reg; 
				ISAX_fire_s = BNode.ISAX_fire_mem_s;
				ISAX_fire2_r = BNode.ISAX_fire2_mem_reg;
				//ISAX_sum_spawn_s = BNode.ISAX_sum_spawn_mem_s;
				ISAX_spawnStall_s = BNode.ISAX_spawnStall_mem_s; 
			}
			declare +=  "Reg #(Bool) "+ISAX_fire_r+" <- mkReg (False);\n";
			//declare +=  "Wire #(Bool) "+ISAX_fire_s+" <- mkDWire(False);\n";  // not required here, will be in rule with "let"
			declare +=  "Reg #(Bool) "+ISAX_fire2_r+" <- mkReg (False);\n";
			declare += "Wire #(Bool) "+ISAX_spawnStall_s+" <- mkDWire(False);\n";
			//String declareSumSpawn = "Wire #("+ISAX_sum_spawn_s+" <- <- mkDWire(False);\\n   // not required here, will be in rule with "let"
		}
		return declare;		
	}
	
	public String CreateSpawnRegsLogic(String instr, int spawnStage, String priority, String node) { // node has to be "mem_spawn" in case of mem as workaround
		String body = "";	
		String ISAX_fire2_r = BNode.ISAX_fire2_regF_reg;
		String valid_node = node+"_valid";
		String addr_node = node+"_addr";
		if(node.contains(BNode.RdMem_spawn.split("d")[1])) {
			ISAX_fire2_r = BNode.ISAX_fire2_mem_reg;
			valid_node = BNode.Mem_spawn_valid ;
			addr_node = BNode.Mem_spawn_addr;
		}
		if(!priority.isEmpty())
			priority = " "+this.dictionary.get(Words.logical_and)+" !("+priority+")"; 
		body += "if("+CreateLocalNodeName(valid_node, spawnStage,instr)+" ) begin \n"
				+ tab+CreateNodeName(valid_node, spawnStage,instr).replace("_i", "_reg")+" <= True; \n";
		if(!node.contentEquals(BNode.RdMem_spawn))
				body +=  tab+CreateNodeName(node, spawnStage,instr).replace("_i", "_reg")+" <= "+CreateLocalNodeName(node, spawnStage,instr)+"; \n";

		body +=  tab+CreateNodeName(addr_node, spawnStage,instr).replace("_i", "_reg")+" <= "+CreateLocalNodeName(addr_node, spawnStage,instr)+"; \n"
				+ "end  else if(("+ISAX_fire2_r+") "+priority+") \n"
				+ tab+CreateNodeName(valid_node, spawnStage,instr).replace("_i", "_reg")+" <= False; \n";

				
		return body;		
	}
	
	public String CommitSpawnFire (String node, String stallStage, String stageReady, int spawnStage, HashSet<String> WrISAXes, HashSet<String> RdISAXes) { // stageReady = (stage3.out.ostatus == OSTATUS_PIPE) for Piccolo
		String ISAX_fire_r = BNode.ISAX_fire_regF_reg;
		String ISAX_fire_s = BNode.ISAX_fire_regF_s; 
		String ISAX_fire2_r = BNode.ISAX_fire2_regF_reg; 
		String ISAX_sum_spawn_s = BNode.ISAX_sum_spawn_regF_s; 
		String ISAX_spawnStall_s = BNode.ISAX_spawnStall_regF_s;
		String valid_node = node+"_valid";
		String addr_node = node+"_addr";
		if(node.contains(BNode.RdMem_spawn.split("d")[1]) ) {
			ISAX_fire_r = BNode.ISAX_fire_mem_reg; 
			ISAX_fire_s = BNode.ISAX_fire_mem_s;
			ISAX_fire2_r = BNode.ISAX_fire2_mem_reg;
			ISAX_sum_spawn_s = BNode.ISAX_sum_spawn_mem_s;
			ISAX_spawnStall_s = BNode.ISAX_spawnStall_mem_s;
			valid_node = BNode.Mem_spawn_valid;
			addr_node = BNode.Mem_spawn_addr;
		}
		// Create stall logic 
		String stall3Text = "";
		String stallFullLogic = "";
		String stageReadyText = "";
		if (!stallStage.isEmpty() && !stageReady.isEmpty())
			stall3Text += " || ";
		if (!stallStage.isEmpty())
			stall3Text += "("+stallStage+")";
		if (!stageReady.isEmpty())
			stageReadyText = "("+stageReady+")";
		if(!stallStage.isEmpty() || !stageReady.isEmpty())
			stallFullLogic = "&& ("+stageReadyText+" "+stall3Text+")";
		
		String default_logic = "";
		String additionalText = "";
		HashSet<String> allISAXes = new HashSet<String>(); 
		allISAXes.addAll(WrISAXes);
		
		allISAXes.addAll(RdISAXes);
		if(allISAXes.size() >1)
			additionalText = " || "+ISAX_fire2_r+" ";
		default_logic += "// ISAX SPAWN LOGIC // \n"
				+ "rule rule_"+ISAX_spawnStall_s+node+" ;\n"
				+ tab + "let "+ISAX_fire_s+"  = "+this.allISAXNameText( " || ",valid_node+"_","_"+spawnStage+"_s",allISAXes)+";\n"
				+ tab + ISAX_spawnStall_s+" <= (("+ISAX_fire_s+" ||  "+ISAX_fire_r+") "+stallFullLogic+" )"+additionalText+" ;\n"
				+ "endrule\n";
		
		default_logic += "rule rule_"+ISAX_fire2_r+" ; \n"
				+ tab + "let "+ISAX_fire_s+"  = "+this.allISAXNameText(" || ",valid_node+"_","_"+spawnStage+"_s",allISAXes)+"; \n"
				+ tab + "let "+ISAX_sum_spawn_s+" = "+this.allISAXNameText(" + ","pack("+valid_node+"_","_"+spawnStage+"_reg)",allISAXes)+";   \n"
				+ tab + "if("+ISAX_fire_r+" "+stallFullLogic+" )\n"
				+ tab + tab + ISAX_fire_r+" <=False;\n"
				+ tab + "else if("+ISAX_fire_s+")\n"
				+ tab + tab + ISAX_fire_r+" <=True;\n"
				+ tab + "\n"
				+ tab + "if(("+ISAX_sum_spawn_s+"==1) && "+ISAX_fire2_r+")\n"
				+ tab + tab + ISAX_fire2_r+" <=  False; \n"
				+ tab + "else if("+ISAX_fire_r+" "+stallFullLogic+")\n"
				+ tab + tab + ISAX_fire2_r+" <=  True;\n"
				+ tab + "\n";


		String priority   = "";
		String spawnRegs  = "";
		for(String ISAX : allISAXes) {
			if(node.contains(BNode.RdMem_spawn.split("d")[1] )) {
				if(RdISAXes.contains(ISAX))
					node = BNode.RdMem_spawn;
				else 
					node = BNode.WrMem_spawn;
			}
			spawnRegs   += this.CreateSpawnRegsLogic(ISAX, spawnStage, priority, node);
			if(!priority.isEmpty())
				priority += " || ";
			priority   += this.CreateNodeName(valid_node, spawnStage,ISAX).replace("_i", "_reg");
		}
		default_logic += spawnRegs+"\n"
				+ "endrule\n";
		
				
		return default_logic;
	}
	
	
	
	public String CreateSpawnCommitWrRD(String instr, int stage, String priority) {
		String body = "";
		
		if(!priority.isEmpty())
			priority = " && ("+priority+")"; 
		body += "if(("+CreateNodeName(BNode.WrRD_spawn_valid, stage,instr).replace("_i", "_reg")+")"+priority+")\n"
				+ CreateLocalNodeName(BNode.commited_rd_spawn, stage,"")+" <= "+CreateNodeName(BNode.WrRD_spawn_addr, stage,instr).replace("_i", "_reg")+";\n";
		return body;		
	}
	
	public String CreateSpawnLogic(String instr, int spawnStage, String commit_stage, String priority,String node) {
		String body = "";
		String ISAX_fire2_r = BNode.ISAX_fire2_regF_reg; 
		String valid_node = node+"_valid";
		String addr_node = node+"_addr";
		if(node.contains(BNode.RdMem_spawn.split("d")[1]) ) {
			valid_node = BNode.Mem_spawn_valid;
			addr_node = BNode.Mem_spawn_addr;
		}
		if(node.contentEquals(BNode.RdMem_spawn) || node.contentEquals(BNode.WrMem_spawn)) 
			ISAX_fire2_r = BNode.ISAX_fire2_mem_reg;
		if(!priority.isEmpty())
			priority = " && !("+priority+")"; 
		body += "if(("+CreateNodeName(valid_node, spawnStage,instr).replace("_i", "_reg")+")"+priority+")\n";
		String addr = "";
		addr +=CreateNodeName(addr_node, spawnStage,instr).replace("_i", "_reg");
		if(node.contentEquals(BNode.WrMem_spawn) || node.contentEquals(BNode.WrRD_spawn)) {
			if (node.contentEquals(BNode.WrRD_spawn))
				body += commit_stage+".met_"+CreateNodeName(valid_node, spawnStage,"")+"("+addr+","+CreateNodeName(node, spawnStage,instr).replace("_i", "_reg")+","+ISAX_fire2_r+");\n";
			else 
				body += commit_stage+".met_"+CreateNodeName(valid_node, spawnStage,"")+"("+addr+","+CreateNodeName(node, spawnStage,instr).replace("_i", "_reg")+","+ISAX_fire2_r+", False);\n";
			
		} else 
			body += commit_stage+".met_"+CreateNodeName(valid_node, spawnStage,"")+"("+addr+", 0 ,"+ISAX_fire2_r+", True);\n";
		
		return body;		
	}
	
	public String CreateSpawnCommitMem(String instr, int stage, String priority, String node, String valid, String data) {
		String body = "";
		
		if(!priority.isEmpty())
			priority = " && !("+priority+")"; 
		body += "if(("+CreateNodeName(BNode.Mem_spawn_valid, stage,instr).replace("_i", "_reg")+")"+priority+") begin \n";
		if(node.contains(BNode.WrMem_spawn))
			body += CreateLocalNodeName(BNode.WrMem_spawn_valid, stage,instr)+" <= "+BNode.ISAX_fire2_mem_reg+";\n";
		else {
			body += CreateLocalNodeName(BNode.RdMem_spawn_valid, stage,instr)+" <= "+BNode.ISAX_fire2_mem_reg+" && "+valid+";\n";
			body += CreateLocalNodeName(BNode.RdMem_spawn, stage,instr)+" <= "+data+";\n";
		}	
		body += "end\n";
		return body;
		
	}
	
	public String CreateMethodName(String operation, int stage, String instr, boolean shortForm) {	
		String methodName = "method ";
		String size  ="";
		if(coreBackend.NodeDataT(operation, stage).contains("Bit") || coreBackend.NodeDataT(operation, stage).contains("Int"))
			size += "#("+coreBackend.NodeSize(operation, stage)+")";
		if(coreBackend.NodeIn(operation, stage))
			methodName += "Action ";
		else 
			methodName += "("+coreBackend.NodeDataT(operation, stage)+size+") ";
		methodName += "met_"+CreateNodeName(operation, stage,instr);
		
		if(coreBackend.NodeIn(operation, stage)) {
			if(shortForm) 
				methodName += "(x)";
			else 
				methodName += "("+coreBackend.NodeDataT(operation, stage)+size+" x)";
		}
		return methodName;
	}
	
	// TODO to be moved in piccolo, as it is piccolo specific
	public String CreateMemStageFrwrd(boolean bypass, boolean flush) {
		String rdVal = "";
		String no = "no_";
		String isTrue = "False";
		String ostatus = "OSTATUS_PIPE";
		String comment = "//";
		if(bypass) {
			rdVal = "bypass.bypass_state = BYPASS_RD;";
			isTrue = "True";
			no = "";
			comment = "";
		}
		if(flush) {
			ostatus = "OSTATUS_EMPTY";
			no = "no";
			isTrue = "False";
			comment = "//";
		}
		String body = "let data_to_stage3 = data_to_stage3_base; \n"
				+ "data_to_stage3.rd_valid = "+isTrue+"; \n"
				+ " \n"
				+ comment+"let bypass = bypass_base; \n"
				+ rdVal +"\n"
				+ "output_stage2 = Output_Stage2 {ostatus         : OSTATUS_PIPE, \n"
				+ "    trap_info       : ?, \n"
				+ "    data_to_stage3  : data_to_stage3, \n"
				+ "    bypass          : "+no+"bypass \n"
				+ "`ifdef ISA_F \n"
				+ "		, fbypass       : "+no+"fbypass \n"
				+ "`endif \n"
				+ "		}; \n"
				+ "end";
		return body;
	}
	

	
	public String CreateAllNoMemEncoding(HashSet<String> lookAtISAX, HashMap <String,SCAIEVInstr>  allISAXes, String rdInstr) {
		String body = "";
		for (String ISAX : lookAtISAX) {
			if(!allISAXes.get(ISAX).GetSchedNodes().containsKey(BNode.RdMem) && !allISAXes.get(ISAX).GetSchedNodes().containsKey(BNode.WrMem)) {
			if(!body.isEmpty())
				body += " || ";
			body += "(("+rdInstr+"[6:0] == "+allISAXes.get(ISAX).GetEncodingOp(Lang.Bluespec)+")";
			if(!allISAXes.get(ISAX).GetEncodingF3(Lang.Bluespec).contains("-"))
				body += " && ("+ rdInstr+"[14:12] == "+allISAXes.get(ISAX).GetEncodingF3(Lang.Bluespec)+")";
			if(!allISAXes.get(ISAX).GetEncodingF7(Lang.Bluespec).contains("-"))
				body += " && ("+ rdInstr+"[31:25] == "+allISAXes.get(ISAX).GetEncodingF7(Lang.Bluespec)+")";
			body += ")";
			}
		}
		return body;		
	}
	

	
	public String CreatePutInRule(	String ruleBody, String operation,int stage, String instr) {
		String text ="rule rule_"+CreateLocalNodeName(operation, stage, instr)+";\n"+tab+this.AllignText(tab, ruleBody)+"\nendrule\n" ;
		return text;		
	}
	
	

	

	
}
