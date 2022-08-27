package scaiev.util;

import java.util.HashMap;
import java.util.HashSet;

import scaiev.backend.BNode;
import scaiev.backend.CoreBackend;

public class Verilog extends GenerateText {

	FileWriter toFile = new FileWriter();
	public String tab = "    ";
	public Verilog(FileWriter toFile, CoreBackend core) {
		// initialize dictionary 
		dictionary.put(DictWords.module,"module");
		dictionary.put(DictWords.endmodule,"endmodule");
		dictionary.put(DictWords.reg,"reg");
		dictionary.put(DictWords.wire,"wire");		
		dictionary.put(DictWords.assign,"assign");
		dictionary.put(DictWords.assign_eq,"=");
		dictionary.put(DictWords.logical_or,"||");
		dictionary.put(DictWords.bitwise_or,"|");
		dictionary.put(DictWords.logical_and,"&&");
		dictionary.put(DictWords.bitwise_and,"&");
		dictionary.put(DictWords.bitsselectRight,"]");
		dictionary.put(DictWords.bitsselectLeft,"[");
		dictionary.put(DictWords.ifeq,"==");
		dictionary.put(DictWords.bitsRange,":");
		dictionary.put(DictWords.in,"input");
		dictionary.put(DictWords.out,"output");
		this.toFile = toFile;
		tab = toFile.tab;
		this.coreBackend = core;
	}
	
	@Override 
	public Lang getLang () {
		return Lang.Verilog;		
	}
	public void  UpdateInterface(String top_module,String operation, String instr, int stage, boolean top_interface, boolean instReg) {
		// Update interf bottom file
		System.out.println("INTEGRATE. DEBUG. stage = "+stage+" operation = "+operation);
	
		// Update interf bottom file
		String assign_lineToBeInserted = "";
		
		// Add top interface	

		String sig_name = this.CreateNodeName(operation, stage, instr);
		String bottom_module = coreBackend.NodeAssignM(operation, stage);
		if(top_module.contentEquals(bottom_module) && !top_interface)
			sig_name = this.CreateLocalNodeName(operation, stage, instr);
		String current_module = bottom_module;
		String prev_module = "";
		while(!prev_module.contentEquals(top_module)) {
			if(!current_module.contentEquals(top_module) || top_interface) {  // top file should just instantiate signal in module instantiation and not generate top interface
				this.toFile.UpdateContent(coreBackend.ModFile(current_module),");",new ToWrite(CreateTextInterface(operation,stage,instr),true,false,"module "+current_module+" ",true,current_module));								
			} else if(current_module.contentEquals(top_module)) {
				this.toFile.UpdateContent(coreBackend.ModFile(current_module),");",new ToWrite(CreateDeclSig(operation, stage, instr,instReg),true,false,"module "+current_module+" ",current_module));
			}
			
			if(prev_module.contentEquals("")) {
				// Connect signals to top interface
				if(!coreBackend.NodeAssign(operation, stage).contentEquals("")) {
					if(coreBackend.NodeDataT(operation, stage).contains("reg"))
						assign_lineToBeInserted += "always@(posedge  "+clk+")\n"+sig_name+" <= "+coreBackend.NodeAssign(operation, stage)+"; \n";
					else 
						assign_lineToBeInserted += "assign "+sig_name+" = "+coreBackend.NodeAssign(operation, stage)+"; \n";
					this.toFile.UpdateContent(coreBackend.ModFile(current_module),"endmodule", new ToWrite(assign_lineToBeInserted,true,false,"module "+current_module+" ",true,current_module));	
				} /*
				else {
					assign_lineToBeInserted += this.CreateDeclSig(operation, stage, instr,coreBackend.NodeDataT(operation, stage).contains("reg"))+" \n";
					this.toFile.UpdateContent(coreBackend.ModFile(current_module),");", new ToWrite(assign_lineToBeInserted,true,false,"module "+current_module+" ",current_module));	
				}*/
			} else {
				String instance_sig = "";
				
				if(current_module.contentEquals(top_module) && !top_interface)
					instance_sig = "."+sig_name+" ( "+this.CreateLocalNodeName(operation, stage, instr)+"),\n";
				else
					instance_sig = "."+sig_name+" ( "+sig_name+"),\n";
				this.toFile.UpdateContent(coreBackend.ModFile(current_module),");", new ToWrite(instance_sig,true,false,prev_module+" ",true,current_module));
			}
			prev_module = current_module;
			if(!current_module.contentEquals(top_module) && !coreBackend.ModParentName(current_module).equals(""))
				current_module = coreBackend.ModParentName(current_module);	
			else 
				break;				
		}
		
	}

	
	/**
	 * Generates text like : signal signalName_s  :  std_logic_vector(1 : 0);
	 * signalName created from <operation,  stage,  instr>
	 */
	public String CreateDeclSig(String operation, int stage, String instr,boolean reg) {
		String decl = "";
		String size = "";
		if(coreBackend.NodeSize(operation,stage) != 1 ) 
			size += dictionary.get(DictWords.bitsselectLeft)+" "+coreBackend.NodeSize(operation, stage)+" -1 : 0 "+dictionary.get(DictWords.bitsselectRight);
		String wire = "wire";
		if(reg)
			wire = "reg";
		decl = wire+" "+size+" "+CreateLocalNodeName(operation,stage,instr)+";\n";
		return decl;	
	}
	
	/**
	 * Generates text like : signal signalName_reg  :  std_logic_vector(1 : 0);
	 * signalName created from <operation,  stage,  instr>
	 */
	public String CreateDeclReg(String operation, int stage, String instr) {
		String decl = "";
		String size = "";
		if(coreBackend.NodeSize(operation,stage) != 1 ) 
			size += dictionary.get(DictWords.bitsselectLeft)+" "+coreBackend.NodeSize(operation, stage)+" -1 : 0 "+dictionary.get(DictWords.bitsselectRight);
		String regName = "";
		if(coreBackend.NodeIn(operation, stage))
			regName = CreateNodeName(operation,stage,instr).replace("_i", "_reg");
		else 
			regName = CreateNodeName(operation,stage,instr).replace("_o", "_reg");
		decl = "reg "+size+" "+regName+";\n";
		return decl;	
	}
	
	/**
	 * Declares all Regs required for spawn. Returns string with text of declarations
	 * @param ISAXes
	 * @param stage
	 * @return text with declarations
	 * 
	 */
	// TODO
	public String CreateSpawnDeclareSigs(HashSet<String> ISAXes, int stage,  String node, boolean withFire, int sizeOtherNode) {
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
			String ISAX_sum_spawn_s = BNode.ISAX_sum_spawn_regF_s; 	
			String ISAX_spawnStall_s = BNode.ISAX_spawnStall_regF_s; 
			if(node.contentEquals(BNode.RdMem_spawn) || node.contentEquals(BNode.WrMem_spawn)) {
				ISAX_fire_r = BNode.ISAX_fire_mem_reg; 
				ISAX_fire_s = BNode.ISAX_fire_mem_s;
				ISAX_fire2_r = BNode.ISAX_fire2_mem_reg;
				ISAX_sum_spawn_s = BNode.ISAX_sum_spawn_mem_s;
				ISAX_spawnStall_s = BNode.ISAX_spawnStall_mem_s; 
			}
			declare += "reg "+ISAX_fire_r+" ;\n";
			declare += "wire "+ISAX_fire_s+" ;\n";
			declare += "reg "+ISAX_fire2_r+" ;\n";
			declare += "wire "+ISAX_spawnStall_s+";\n";
			
			String declareSumSpawn = "wire  ";
			if((ISAXes.size()+sizeOtherNode)==1)
				declareSumSpawn += " ";
			else 
				declareSumSpawn += "["+((int)  (Math.ceil( Math.log(ISAXes.size()+sizeOtherNode+1) / Math.log(2) ) )  ) +"-1 : 0] ";
			declareSumSpawn += ISAX_sum_spawn_s+";\n";
			declare += declareSumSpawn;
		}
		return declare;			
	}
	
	public String CreateSpawnRegsLogic(String instr, int stage, String priority, String node, String mem_state) {
		String body = "";	
		String ISAX_fire2_r = BNode.ISAX_fire2_regF_reg;
		String valid_node = node+"_valid";
		String addr_node = node+"_addr";
		String check_mem_state = "";
		
		if(node.contains(BNode.RdMem_spawn.split("d")[1])) {
			ISAX_fire2_r = BNode.ISAX_fire2_mem_reg;
			valid_node = BNode.Mem_spawn_valid ;
			addr_node = BNode.Mem_spawn_addr;
			check_mem_state = " && ("+mem_state+")";
			
		}
		if(!priority.isEmpty())
			priority = " "+this.dictionary.get(DictWords.logical_and)+" !("+priority+")"; 
		body += "if("+CreateNodeName(valid_node, stage,instr)+" ) begin \n"
				+ tab+CreateNodeName(valid_node, stage,instr).replace("_i", "_reg")+" <= 1'b1; \n";
		if(!node.contentEquals(BNode.RdMem_spawn))
				body +=  tab+CreateNodeName(node, stage,instr).replace("_i", "_reg")+" <= "+CreateNodeName(node, stage,instr)+"; \n";
		body +=  tab+CreateNodeName(addr_node, stage,instr).replace("_i", "_reg")+" <= "+CreateNodeName(addr_node, stage,instr)+"; \n"
				+ "end else if(("+ISAX_fire2_r+" ) "+priority+check_mem_state+") \n"
				+ tab+CreateNodeName(valid_node, stage,instr).replace("_i", "_reg")+" <= 1'b0; \n"
				+ "if ("+reset+" )\n"
		        + tab +CreateNodeName(valid_node, stage,instr).replace("_i", "_reg") + "<= 1'b0; \n";
				
		return body;		
	}
	
	public String CommitSpawnFire (String node, String stallStage, String stageReady, int spawnStage, String mem_state) {
		String ISAX_fire_r = BNode.ISAX_fire_regF_reg;
		String ISAX_fire_s = BNode.ISAX_fire_regF_s; 
		String ISAX_fire2_r = BNode.ISAX_fire2_regF_reg; 
		String ISAX_sum_spawn_s = BNode.ISAX_sum_spawn_regF_s; 
		String ISAX_spawnStall_s = BNode.ISAX_spawnStall_regF_s;
		String check_mem_state = "";
		String neg_mem_state = "";
		if(node.contains(BNode.RdMem_spawn.split("d")[1])) {
			ISAX_fire_r = BNode.ISAX_fire_mem_reg; 
			ISAX_fire_s = BNode.ISAX_fire_mem_s;
			ISAX_fire2_r = BNode.ISAX_fire2_mem_reg;
			ISAX_sum_spawn_s = BNode.ISAX_sum_spawn_mem_s;
			ISAX_spawnStall_s = BNode.ISAX_spawnStall_mem_s;
			check_mem_state = " && ("+mem_state+")";
		
		}
		
		// Create stall logic 
		String stall3Text = "";
		String stallFullLogic = "";
		String stageReadyText = "";
		if (!stallStage.isEmpty() && !stageReady.isEmpty())
			stall3Text += " || ";
		if (!stallStage.isEmpty())
			stall3Text += stallStage;
		if (!stageReady.isEmpty())
			stageReadyText = stageReady;
		if(!stallStage.isEmpty() || !stageReady.isEmpty())
			stallFullLogic = "&& ("+stageReadyText+" "+stall3Text+")";
		
		String sumRes = "1";
		String default_logic = " // ISAX : Spawn fire logic\n"
				+ "always @ (posedge "+clk+")  begin\n"
				+ "     if("+ISAX_fire_s+"  )  \n"
				+ "         "+ISAX_fire_r+" <=  1'b1; \n"
				+ "     else if(("+ISAX_fire_r+" ) "+stallFullLogic+")   \n"
				+ "         "+ISAX_fire_r+" <=  1'b0; \n"
				+ "     if ("+reset+") \n"
				+ "          "+ISAX_fire_r+" <= 1'b0; \n"
				+ "end \n"
				+ "   \n"
				+ "always @ (posedge "+clk+")  begin\n"
				+ "     if(("+ISAX_fire_r+") "+stallFullLogic+")    \n"
				+ "          "+ISAX_fire2_r+" <=  1'b1; \n"
				+ "     else if("+ISAX_fire2_r+" && ("+ISAX_sum_spawn_s+" == "+sumRes+") "+check_mem_state+")    \n"
				+ "          "+ISAX_fire2_r+" <= 1'b0; \n"
				+ "     if ("+reset+") \n"
				+ "          "+ISAX_fire2_r+" <= 1'b0; \n"
				+ "  end \n"
				+ "\n ";
		
				
		return default_logic;
	}
	
	public String CreateSpawnCommitWrRD(String instr, int stage, String priority) {
		String body = "";
		/*
		if(!priority.isEmpty())
			priority = " and !("+priority+")"; 
		body += "if(("+CreateNodeName(BNode.WrRD_spawn_valid, stage,instr).replace("_i", "_reg")+")"+priority+")\n"
				+ CreateLocalNodeName(BNode.commited_rd_spawn, stage,"")+" <= "+CreateNodeName(BNode.WrRD_spawn_addr, stage,instr).replace("_i", "_reg")+";\n";
				*/
		return body;		
	}
	
	public String CreateSpawnCommitMem(String instr, int stage, String priority, String node, String ready, String rdata ) {
		String body = "";
		
		if(!priority.isEmpty())
			priority = " && !("+priority+")"; 
		body += "if(("+CreateNodeName(BNode.Mem_spawn_valid, stage,instr).replace("_i", "_reg")+")"+priority+") begin \n";
		if(node.contains(BNode.WrMem_spawn)) {
			body += CreateNodeName(BNode.WrMem_spawn_valid, stage,instr)+" = "+BNode.ISAX_fire2_mem_reg+" && "+ready+";\n";
		}
		else {
			body += CreateNodeName(BNode.RdMem_spawn_valid, stage,instr)+" = "+BNode.ISAX_fire2_mem_reg+" && "+ready+";\n";
			body += CreateNodeName(BNode.RdMem_spawn, stage,instr)+" = "+rdata+";\n";
		}	
		body += "end else begin  \n";
		if(node.contains(BNode.WrMem_spawn)) {
			body += CreateNodeName(BNode.WrMem_spawn_valid, stage,instr)+" = 0;\n";
		}
		else {
			body += CreateNodeName(BNode.RdMem_spawn_valid, stage,instr)+" = 0;\n";
			body += CreateNodeName(BNode.RdMem_spawn, stage,instr)+" = 0 ;\n";
		}	
		body += "end \n";
		return body;
		
	}
	
	public String CreateSpawnFSMMem(String instr, int stage, String priority, String node, String wrmem, String wrdata, String addr ) {
		String body = "";
		
		if(!priority.isEmpty())
			priority = " && !("+priority+")"; 
		body += "if(("+CreateNodeName(BNode.Mem_spawn_valid, stage,instr).replace("_i", "_reg")+")"+priority+") begin \n";
		if(node.contains(BNode.WrMem_spawn)) {
			body += wrmem + " = 1;\n";
			body += wrdata + " =  "+ CreateNodeName(BNode.WrMem_spawn, stage,instr).replace("_i", "_reg")+";\n";			
		}
		else {
			body += wrmem + " = 0;\n";
		}	
		body += addr+ " =  "+ CreateNodeName(BNode.Mem_spawn_addr, stage,instr).replace("_i", "_reg")+";\n";
		body += "end\n";
		return body;
		
	}
	
	
	public String CreateSpawnLogicWrRD(HashSet<String> ISAXes, int stage, String ISAX_execute_to_rf_data_s, String ISAX_fire2_regF_r) {
		String body = "";
		String condition = "if";
		for(String ISAX : ISAXes) {
			body += condition + "("+ISAX_fire2_regF_r+" && "+CreateNodeName(BNode.WrRD_spawn_valid, stage,ISAX).replace("_i", "_reg")+" )\n"+tab+ISAX_execute_to_rf_data_s+" <= "+CreateNodeName(BNode.WrRD_spawn, stage,ISAX).replace("_i", "_reg")+";\n";
			condition = "else if";
		}
		return body;		
	}
	
	public String CreateTextInterface(String operation, int stage, String instr) {
		String interf_lineToBeInserted = "";
		String sig_name = this.CreateNodeName(operation, stage, instr);
		String sig_in = this.dictionary.get(DictWords.out);
		if(coreBackend.NodeIn(operation, stage))
			sig_in = this.dictionary.get(DictWords.in);
		String size = "";
		if(coreBackend.NodeSize(operation, stage)> 1 ) 
			size += "["+coreBackend.NodeSize(operation, stage)+" -1 : 0]";
		// Add top interface	
		interf_lineToBeInserted = sig_in + " " + coreBackend.NodeDataT(operation, stage) + " "+size +" "+ sig_name+",// ISAX\n";
		return interf_lineToBeInserted;
	}
	
	
	public String CreateMemStageFrwrd(boolean bypass) {
		String rdVal = "";
		String isTrue = "False";
		String body ="";
		/*
		if(bypass) {
			rdVal = "bypass.bypass_state = BYPASS_RD_RDVAL;";
			isTrue = "True";
		}
		String body = "let data_to_stage3 = data_to_stage3_base; \n"
				+ "data_to_stage3.rd_valid = "+isTrue+"; \n"
				+ " \n"
				+ "let bypass = bypass_base; \n"
				+ rdVal +"\n"
				+ "output_stage2 = Output_Stage2 {ostatus          :  OSTATUS_PIPE, \n"
				+ "    trap_info        :  ?, \n"
				+ "    data_to_stage3   :  data_to_stage3, \n"
				+ "    bypass           :  bypass \n"
				+ "`ifdef ISA_F \n"
				+ "		, fbypass        :  no_fbypass \n"
				+ "`endif \n"
				+ "		}; \n"
				+ "end";
				*/
		return body;
	}
	
	public String CreateTextISAXorOrig(String if_clause, String new_signal, String ISAX_signal, String orig_signal) {
		String text ="";
		text += "always@(*) begin \n"
				+ tab+"if( "+if_clause+" ) \n"
				+ tab.repeat(2)+new_signal+" <= "+ISAX_signal+"; \n"
				+ tab+"else \n"
				+ tab.repeat(2)+new_signal+" <= "+orig_signal+";\n"
				+"end;\n\n";
		return text;
	}
	public String CreateTextRegReset(String signal_name, String signal_assign) {
		String text ="";
		text += "always@(posedge clk) begin\n"
				+ tab+"if (!resetn)\n"
				+ tab.repeat(2)+signal_name+" <= 1'b0;\n"
				+ tab+"else \n"
				+ tab.repeat(2)+signal_name+" <= "+signal_assign+";\n"
				+ "end;\n\n";
		return text;
	}
	
	public String CreateText1or0(String new_signal, String condition) {
		String text = "assign "+ new_signal + " = ("+condition+") ? 1'b1 : 1'b0;\n";
		return text;
	}
	
	public String CreateInAlways( boolean with_clk ,String text) {
		int i = 1;
		String sensitivity = "*";
		if(with_clk) {
			sensitivity = "posedge "+clk;
			i++;
		}
		String body ="always@("+sensitivity+") begin // ISAX Logic\n "+AllignText(tab.repeat(i),text)+"\n"+"end\n" ;
		return body;		
	}
	
	

	

	
}

	
	

