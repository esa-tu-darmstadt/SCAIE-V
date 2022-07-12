package scaiev.util;

import java.util.HashMap;
import java.util.HashSet;

import scaiev.backend.BNode;
import scaiev.backend.CoreBackend;

public class Verilog extends GenerateText {

	FileWriter toFile = new FileWriter();
	public String tab = "    ";
	public String clk = "clk";
	public Verilog(FileWriter toFile, CoreBackend core) {
		// initialize dictionary 
		dictionary.put(Words.module,"module");
		dictionary.put(Words.endmodule,"endmodule");
		dictionary.put(Words.reg,"reg");
		dictionary.put(Words.wire,"wire");		
		dictionary.put(Words.assign,"assign");
		dictionary.put(Words.assign_eq,"=");
		dictionary.put(Words.logical_or,"||");
		dictionary.put(Words.bitwise_or,"|");
		dictionary.put(Words.logical_and,"&&");
		dictionary.put(Words.bitwise_and,"&");
		dictionary.put(Words.bitsselectRight,"]");
		dictionary.put(Words.bitsselectLeft,"[");
		dictionary.put(Words.ifeq,"==");
		dictionary.put(Words.bitsRange,":");
		dictionary.put(Words.in,"input");
		dictionary.put(Words.out,"output");
		this.toFile = toFile;
		tab = toFile.tab;
		this.coreBackend = core;
	}
	
	@Override 
	public String getLang () {
		return Lang.Verilog;		
	}
	public void  UpdateInterface(String top_module,String operation, String instr, int stage, boolean top_interface, boolean instReg) {
		// Update interf bottom file
		System.out.println("INTEGRATE. DEBUG. stage = "+stage+" operation = "+operation);
		HashMap<String, HashMap<String,ToWrite>> update_orca = new HashMap<String, HashMap<String,ToWrite>>();
		
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
			HashMap<String,ToWrite> insert = new HashMap<String,ToWrite>();
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
			size += dictionary.get(Words.bitsselectLeft)+" "+coreBackend.NodeSize(operation, stage)+" -1 : 0 "+dictionary.get(Words.bitsselectRight);
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
			size += dictionary.get(Words.bitsselectLeft)+" "+coreBackend.NodeSize(operation, stage)+" -1 : 0 "+dictionary.get(Words.bitsselectRight);
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
	public String CreateSpawnDeclareRegs(HashSet<String> ISAXes, int stage) {
		String declare = "";
		for(String ISAX  :  ISAXes) {
			declare += CreateDeclReg(BNode.WrRD_spawn_valid, stage,ISAX);
			declare += CreateDeclReg(BNode.WrRD_spawn, stage,ISAX);
			declare += CreateDeclReg(BNode.WrRD_spawn_addr, stage,ISAX);
		}
		return declare;		
	}
	
	public String CreateSpawnRegsWrRD(String instr, int stage, String priority, String ISAX_fire2_regF_r) {
		String body = "";	
		if(!priority.isEmpty())
			priority = " "+this.dictionary.get(Words.logical_and)+" !("+priority+")"; 
		body += "always @(posedge clk) begin\n "
				+ "if("+CreateNodeName(BNode.WrRD_spawn_valid, stage,instr)+") begin \n"
				+ tab+CreateNodeName(BNode.WrRD_spawn_valid, stage,instr).replace("_i", "_reg")+" <= 1'b1; \n"
				+ tab+CreateNodeName(BNode.WrRD_spawn, stage,instr).replace("_i", "_reg")+" <= "+CreateNodeName(BNode.WrRD_spawn, stage,instr)+"; \n"
				+ tab+CreateNodeName(BNode.WrRD_spawn_addr, stage,instr).replace("_i", "_reg")+" <= "+CreateNodeName(BNode.WrRD_spawn_addr, stage,instr)+"; \n"
				+ "end  else if(("+ISAX_fire2_regF_r+") "+priority+")\n"
				+ tab+CreateNodeName(BNode.WrRD_spawn_valid, stage,instr).replace("_i", "_reg")+" <= 1'b0; \n"
				+ "if (!resetn) \n"
		        + tab +CreateNodeName(BNode.WrRD_spawn_valid, stage,instr).replace("_i", "_reg") + "<= 1'b0; \n"
		        + "end";
				
		return body;		
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
		String sig_in = this.dictionary.get(Words.out);
		if(coreBackend.NodeIn(operation, stage))
			sig_in = this.dictionary.get(Words.in);
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
	
	public String CreateInAlways(boolean clk, String text) {
		int i = 1;
		String sensitivity = "*";
		if(clk) {
			sensitivity = "posedge clk";
			i++;
		}
		String body ="always@("+sensitivity+") begin // ISAX Logic\n "+AllignText(tab.repeat(i),text)+"\n"+"end;\n" ;
		return body;		
	}
	
	

	

	
}

	
	

