package scaiev.util;

import java.util.HashMap;
import java.util.HashSet;

import scaiev.backend.BNode;
import scaiev.backend.CoreBackend;
import scaiev.coreconstr.Core;
import scaiev.frontend.SCAIEVInstr;

class Words {

	public static String module = "module"; 
	public static String endmodule = "endmodule";
	public static String wire = "wire"; 
	public static String reg = "reg";
	public static String assign = "assign"; 
	public static String assign_eq = "assign_eq";
	public static String logical_or = "logical_or"; 
	public static String logical_and = "logical_and";
	public static String bitwise_and = "bitwise_and"; 
	public static String bitwise_or = "bitwise_or";
	public static String bitsselectRight = "bitsselectRight"; 
	public static String bitsselectLeft = "bitsselectLeft";
	public static String bitsRange = "bitsRange"; 
	public static String in = "in";
	public static String out = "out"; 
	public static String  ifeq = "=="; 
	
}


public class GenerateText {
	public String currentFile;
	public CoreBackend coreBackend = new CoreBackend();
	
	public String clk = "";
	public String reset = "";
	
	public HashMap<String,String> dictionary = new HashMap<String,String>(){{ 
		put(Words.module, "");
		put(Words.endmodule, "");
		put(Words.endmodule, "");
		put(Words.assign, "");
		put(Words.wire, "");
		put(Words.reg, "");
		put(Words.assign,"");
		put(Words.assign_eq,"");
		put(Words.logical_or,"");
		put(Words.logical_and,"");
		put(Words.bitwise_or,"");
		put(Words.bitwise_and,"");
		put(Words.bitsselectRight,"");
		put(Words.bitsselectLeft,"");
		put(Words.bitsRange,"");
		put(Words.in,"");
		put(Words.out,"");
	}};
	
	
	
	/**
	 * 
	 * @param separator
	 * @param textBefore
	 * @param textAfter
	 * @param ISAXes
	 * @return
	 * Generates text like: textBefore+ISAXname_1+textAfter || textBefore+ISAXname_2+textAfter || textBefore+ISAXname_3+textAfter..
	 */
	public String allISAXNameText(String separator, String textBefore, String textAfter, HashSet<String> ISAXes) { // ISAXes array of ISAXes that are required, for exp in case of decoupled would be array with names of instr that are decoupled
		String returnString = "";
		for(String ISAXname : ISAXes) {
			if(!returnString.contentEquals(""))
				returnString += (separator);
			returnString += (textBefore+ISAXname+textAfter);
		}
		return returnString;
	}
	
	
	public String allSCAIEVInstrText(String separator, String textBefore, String textAfter, HashMap<String,SCAIEVInstr> ISAXes) { // ISAXes array of ISAXes that are required, for exp in case of decoupled would be array with names of instr that are decoupled
		String returnString = "";
		for(String ISAXName : ISAXes.keySet()) {
			SCAIEVInstr ISAX = ISAXes.get(ISAXName);
			if(!returnString.contentEquals(""))
				returnString += (separator);
			returnString += (textBefore+ISAX.GetName()+textAfter);
		}
		return returnString;
	}
	
	
	
	public String CreateNodeName (String operation, int stage, String instr) {
		String nodeName = "";
		String suffix = "_o";
		if(coreBackend.NodeIn(operation, stage))
			suffix = "_i";
		if(!instr.isEmpty())
			instr = "_"+instr;		
		nodeName = operation+instr+"_"+stage+suffix;
		return nodeName;
	}
	
	
	public String CreateLocalNodeName (String operation, int stage, String instr) {
		String nodeName = "";
		String suffix = "_s";
		if(!instr.isEmpty())
			instr = "_"+instr;		
		nodeName = operation+instr+"_"+stage+suffix;
		return nodeName;
	}
	
	
	public String CreateNodeName (String operation, int stage, String instr, boolean input) {
		String nodeName = "";
		String suffix = "_o";
		if(input)
			suffix = "_i";
		if(!instr.isEmpty())
			instr = "_"+instr;		
		nodeName = operation+instr+"_"+stage+suffix;
		return nodeName;
	}
	
	public String GetDict(String input) {
		return dictionary.get(input);
	}
	
	public String AllignText(String allignment, String text) {
		String newText = allignment + text;
		newText = newText.replaceAll("(\\r|\\n)","\n"+allignment);
		text = newText;
		return text;
	}
	
	
	public String CreateAllEncoding(HashSet<String> lookAtISAX, HashMap <String,SCAIEVInstr>  allISAXes, String rdInstr) {
		String body = "";
		for (String ISAX  :  lookAtISAX) {
			if(!body.isEmpty())
				body +=  " "+dictionary.get(Words.logical_or)+" ";
			body += "(("+rdInstr+dictionary.get(Words.bitsselectLeft)+"6 "+dictionary.get(Words.bitsRange)+" 0"+dictionary.get(Words.bitsselectRight)+" "+dictionary.get(Words.ifeq)+" "+allISAXes.get(ISAX).GetEncodingOp(getLang())+")";
			if(!allISAXes.get(ISAX).GetEncodingF3(Lang.VHDL).contains("-"))
				body += " "+dictionary.get(Words.logical_and)+" ("+ rdInstr+dictionary.get(Words.bitsselectLeft)+"14 "+dictionary.get(Words.bitsRange)+" 12"+dictionary.get(Words.bitsselectRight)+" "+dictionary.get(Words.ifeq)+" "+allISAXes.get(ISAX).GetEncodingF3(getLang())+")";
			if(!allISAXes.get(ISAX).GetEncodingF7(Lang.VHDL).contains("-"))
				body += " "+dictionary.get(Words.logical_and)+" ("+ rdInstr+dictionary.get(Words.bitsselectLeft)+"31 "+dictionary.get(Words.bitsRange)+" 25"+dictionary.get(Words.bitsselectRight)+" "+dictionary.get(Words.ifeq)+" "+allISAXes.get(ISAX).GetEncodingF7(getLang())+")";
			body += ")";			
		}
		return body;		
	}
	
	public String  CreateAddrEncoding(HashSet<String> lookAtISAX, HashMap <String,SCAIEVInstr>  allISAXes, String rdInstr, String operation) {
		String body = "";
		for (String ISAX  :  lookAtISAX) {
			if(allISAXes.get(ISAX).GetSchedNodes().get(operation).GetAddrInterf()) {
				if(!body.isEmpty())
					body += " "+dictionary.get(Words.logical_or)+" ";
				body += "(("+rdInstr+dictionary.get(Words.bitsselectLeft)+"6 "+dictionary.get(Words.bitsRange)+" 0"+dictionary.get(Words.bitsselectRight)+" "+dictionary.get(Words.ifeq)+" "+allISAXes.get(ISAX).GetEncodingOp(getLang())+")";
				if(!allISAXes.get(ISAX).GetEncodingF3(Lang.VHDL).contains("-"))
					body += " "+dictionary.get(Words.logical_and)+" ("+ rdInstr+dictionary.get(Words.bitsselectLeft)+"14 "+dictionary.get(Words.bitsRange)+" 12"+dictionary.get(Words.bitsselectRight)+" "+dictionary.get(Words.ifeq)+" "+allISAXes.get(ISAX).GetEncodingF3(getLang())+")";
				if(!allISAXes.get(ISAX).GetEncodingF7(Lang.VHDL).contains("-"))
					body += " "+dictionary.get(Words.logical_and)+" ("+ rdInstr+dictionary.get(Words.bitsselectLeft)+"31 "+dictionary.get(Words.bitsRange)+" 25"+dictionary.get(Words.bitsselectRight)+" "+dictionary.get(Words.ifeq)+" "+allISAXes.get(ISAX).GetEncodingF7(getLang())+")";
				body += ")";
			}
		}
		return body;		
	}
	
	
	public String  CreateValidEncoding(HashSet<String> lookAtISAX, HashMap <String,SCAIEVInstr>  allISAXes, String rdInstr, String operation) {
		String body = "";
		for (String ISAX  :  lookAtISAX) {
			if(!body.isEmpty())
				body += " "+dictionary.get(Words.logical_or)+" ";
			body += "(("+rdInstr+dictionary.get(Words.bitsselectLeft)+"6 "+dictionary.get(Words.bitsRange)+" 0"+dictionary.get(Words.bitsselectRight)+" "+dictionary.get(Words.ifeq)+" "+allISAXes.get(ISAX).GetEncodingOp(getLang())+")";
			if(!allISAXes.get(ISAX).GetEncodingF3(Lang.VHDL).contains("-"))
				body += " "+dictionary.get(Words.logical_and)+" ("+ rdInstr+dictionary.get(Words.bitsselectLeft)+"14 "+dictionary.get(Words.bitsRange)+" 12"+dictionary.get(Words.bitsselectRight)+" "+dictionary.get(Words.ifeq)+" "+allISAXes.get(ISAX).GetEncodingF3(getLang())+")";
			if(!allISAXes.get(ISAX).GetEncodingF7(Lang.VHDL).contains("-"))
				body += " "+dictionary.get(Words.logical_and)+" ("+ rdInstr+dictionary.get(Words.bitsselectLeft)+"31 "+dictionary.get(Words.bitsRange)+" 25"+dictionary.get(Words.bitsselectRight)+" "+dictionary.get(Words.ifeq)+" "+allISAXes.get(ISAX).GetEncodingF7(getLang())+")";
			
			
			if(allISAXes.get(ISAX).GetSchedNodes().get(operation).GetValidInterf()) {
				body += " "+dictionary.get(Words.logical_and)+" "+this.CreateLocalNodeName(operation+"_valid", allISAXes.get(ISAX).GetSchedNodes().get(operation).GetStartCycle(), "")+")";
			} else 
				body += ")";
		}
		return body;		
	}
	
	
	public String  CreateValidEncodingIValid(HashSet<String> lookAtISAX, HashMap <String,SCAIEVInstr>  allISAXes, int stage, String operation) {
		String body = "";
		for (String ISAX  :  lookAtISAX) {
			if(!body.isEmpty())
				body += " "+dictionary.get(Words.logical_or)+" ";
			if(allISAXes.get(ISAX).GetSchedNodes().containsKey(BNode.RdIValid) && (allISAXes.get(ISAX).GetSchedNodes().get(BNode.RdIValid).GetStartCycle()==stage))
				body += "("+this.CreateNodeName(BNode.RdIValid, stage, ISAX);
			else 
				body += "("+this.CreateLocalNodeName(BNode.RdIValid, stage, ISAX);
			if(allISAXes.get(ISAX).GetSchedNodes().get(operation).GetValidInterf()) {
				body += " "+dictionary.get(Words.logical_and)+" "+this.CreateNodeName(operation+"_valid", allISAXes.get(ISAX).GetSchedNodes().get(operation).GetStartCycle(), "")+")";
			} else 
				body += ")";
		}
		return body;		
	}
	
	public String OpIfNEmpty(String text, String op) {
		if(!text.isEmpty())
			text += " "+op+" ";
		return text;
	}
	
	public String getLang(){
		return  "";
	}
	
	public void GenerateAllInterfaces (String topModule,HashMap<String, HashMap<Integer,HashSet<String>>> op_stage_instr, HashMap <String,SCAIEVInstr>  ISAXes, Core core, String specialCase ) {
		boolean mem_addr = false;
        boolean reg_addr = false;
		for(int stage = 0;stage<=core.maxStage;stage++) {
			 for (String operation: op_stage_instr.keySet())	 
				 if(op_stage_instr.get(operation).containsKey(stage)) {
					 String instructionName = "";
					 boolean valid = false;
					 boolean data = false;
					 for(String instruction : op_stage_instr.get(operation).get(stage))  {
						 if(operation.equals(BNode.RdIValid)) {				 
							 instructionName = instruction;
							 data = false;
					  	 } 
						 if(!data && !operation.equals(specialCase)) {
							  UpdateInterface(topModule,operation, instructionName,stage,true,false);	
							 data = true;
						 }
						 if(ISAXes.get(instruction).GetNode(operation).GetValidInterf() && !operation.contains("spawn") && !valid ) {
							 UpdateInterface(topModule,operation+"_valid", instructionName,stage,true,false);	
							 valid = true;
						 }
						 if(ISAXes.get(instruction).GetNode(operation).GetAddrInterf() && !operation.contains("spawn")) {
							 if(operation.contains("Mem") && !mem_addr) {
								 UpdateInterface(topModule,BNode.Mem_addr, instructionName,stage,true,false);	
								 mem_addr = true;
							 } else if(operation.contains(BNode.WrRD) && !reg_addr) {
								 UpdateInterface(topModule,BNode.WrRD_addr, instructionName,stage,true,false);	
								 reg_addr = true;
							 }
						 }
						 
					 }
				 }
			 
				 
			 // Memory data read valid 
			 if(op_stage_instr.containsKey(BNode.RdMem) && op_stage_instr.get(BNode.RdMem).containsKey(stage))
				 UpdateInterface(topModule,BNode.RdMem_valid, "",stage,true,false);			
			 // Spawn interfaces
			 int spawnStage = core.maxStage+1;
			 if(stage == core.GetNodes().get(BNode.RdMem).GetLatest() && op_stage_instr.containsKey(BNode.RdMem_spawn)) {
				 for(String instruction : op_stage_instr.get(BNode.RdMem_spawn).get(core.maxStage+1)) {
					 UpdateInterface(topModule,BNode.RdMem_spawn, instruction,spawnStage,true,false);
					 UpdateInterface(topModule,BNode.RdMem_spawn_valid, instruction,spawnStage,true,false);
					 UpdateInterface(topModule,BNode.Mem_spawn_valid, instruction,spawnStage,true,false);
					 UpdateInterface(topModule,BNode.Mem_spawn_addr, instruction,spawnStage,true,false);
				 }
			 }
			 if(stage == core.GetNodes().get(BNode.WrMem).GetLatest() && op_stage_instr.containsKey(BNode.WrMem_spawn)) {
				 for(String instruction : op_stage_instr.get(BNode.WrMem_spawn).get(core.maxStage+1)) {
					 UpdateInterface(topModule,BNode.WrMem_spawn, instruction,spawnStage,true,false);
					 UpdateInterface(topModule,BNode.WrMem_spawn_valid, instruction,spawnStage,true,false);
					 UpdateInterface(topModule,BNode.Mem_spawn_valid, instruction,spawnStage,true,false);
					 UpdateInterface(topModule,BNode.Mem_spawn_addr, instruction,spawnStage,true,false);
				 }
			 }
			 
			 if(stage == core.GetNodes().get(BNode.WrRD).GetLatest() && (op_stage_instr.containsKey(BNode.WrRD_spawn))) {
				 for(String instruction : op_stage_instr.get(BNode.WrRD_spawn).get(core.maxStage+1)) {
					 UpdateInterface(topModule,BNode.WrRD_spawn, instruction,spawnStage,true,false);
					 UpdateInterface(topModule,BNode.WrRD_spawn_valid, instruction,spawnStage,true,false);
					 UpdateInterface(topModule,BNode.WrRD_spawn_addr, instruction,spawnStage,true,false);
				 }
				 UpdateInterface(topModule,BNode.commited_rd_spawn_valid, "",spawnStage,true,false);
				 UpdateInterface(topModule,BNode.commited_rd_spawn, "",spawnStage,true,false);
				 if(!(op_stage_instr.containsKey(BNode.RdStall) && op_stage_instr.get(BNode.RdStall).containsKey(core.GetNodes().get(BNode.RdRS1).GetEarliest()) ))
					 UpdateInterface(topModule,BNode.RdStall, "", core.GetNodes().get(BNode.RdRS1).GetEarliest(),true,false);
			 }
			 
			 // WrPC spawn 
			 if(stage == core.GetNodes().get(BNode.WrPC).GetEarliest() && op_stage_instr.containsKey(BNode.WrPC_spawn)) {
				 UpdateInterface(topModule,BNode.WrPC_spawn, "",spawnStage,true,false);
				 UpdateInterface(topModule,BNode.WrPC_spawn_valid, "",spawnStage,true,false);
				 
			 }
			
		 }
	}
	
	// TODO to be implemented by sub-class, if the subclass Language wants to use the GenerateAllInterfaces function
	public void UpdateInterface(String top_module,String operation, String instr, int stage, boolean top_interface, boolean assigReg) {		
		System.out.println("ERR Function called, but not implemented by the language subclass");
	}
	
}
