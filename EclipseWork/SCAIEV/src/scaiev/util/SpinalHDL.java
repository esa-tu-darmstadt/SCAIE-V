package scaiev.util;

import java.util.HashMap;

import scaiev.backend.BNode;
import scaiev.backend.CoreBackend;
import scaiev.frontend.SCAIEVInstr;

public class SpinalHDL extends GenerateText{
	FileWriter toFile = new FileWriter();
	public String tab = "    ";
	
	public SpinalHDL(FileWriter toFile, CoreBackend core) {
		// initialize dictionary 
		dictionary.put(Words.module,"class");
		dictionary.put(Words.endmodule,"}");
		dictionary.put(Words.reg,"Reg");
		dictionary.put(Words.wire,"UInt");		
		dictionary.put(Words.assign,"");
		dictionary.put(Words.assign_eq,":=");
		dictionary.put(Words.logical_or,"||");
		dictionary.put(Words.logical_and,"&&");
		dictionary.put(Words.bitwise_or,"|");
		dictionary.put(Words.in,"in");
		dictionary.put(Words.out,"out");
		this.toFile = toFile;
		tab = toFile.tab;
		this.coreBackend = core;
	}
	
	
	public void CloseBrackets () {
		this.toFile.nrTabs--;
		this.toFile.UpdateContent(this.currentFile, "}");		
	}
	

	public String CreateInterface(String operation, int stage, String instr) {
		String in = dictionary.get(Words.out);
		if(coreBackend.NodeIn(operation, stage))
			in =dictionary.get(Words.in);
		String nodeName = CreateNodeName(operation, stage,instr);
		String size = ""; 
		if(coreBackend.NodeSize(operation, stage)>0)
			size = coreBackend.NodeSize(operation, stage)+" bits";
		String interfaceText = "val "+nodeName+" = "+in+" "+coreBackend.NodeDataT(operation, stage)+"("+size+")\n";
		return interfaceText;
	}
	
	/**
	 * 
	 * @param operation - node name
	 * @param stage - stage nr
	 * @param instr - instruction Name (for IValid for example)
	 * @param conditional -  is it a "when(Signal) {assignSignal = True}" scenario?
	 * @return
	 */
	public String CreateAssignToISAX(String operation, int stage, String instr, boolean conditional) { 
		String assignText = "";
		String nodeName = CreateNodeName(operation, stage,instr);
		if(!coreBackend.NodeIn(operation, stage))
			assignText = "io."+ nodeName + " := "+ coreBackend.NodeAssign(operation, stage)+";\n";
		else 
			assignText = coreBackend.NodeAssign(operation, stage)+ " := "+ "io."+ nodeName +";\n";
		if(!instr.isEmpty() && operation.contains(BNode.RdIValid))
			assignText = "io."+ nodeName + " := "+coreBackend.NodeAssign(operation, stage).replaceAll("IS_ISAX","IS_"+instr)+"\n";
		if(conditional && coreBackend.NodeIn(operation, stage))
			assignText = "when(io."+nodeName+") {\n"+this.tab+coreBackend.NodeAssign(operation, stage)+ " := "+ "True;\n}\n";
		return assignText;
	}
	
	
	public String CreateClauseValid(HashMap<String,SCAIEVInstr> ISAXes, String operation, int stage, String pluginStage) {
		String clause = "";
		 boolean first = true;
		 for(String instructionName : ISAXes.keySet()) {
			 if(ISAXes.get(instructionName).HasNode(operation) && ISAXes.get(instructionName).GetSchedNodes().get(operation).GetStartCycle()==stage) { 
				 if(!first)
					 clause += " "+dictionary.get(Words.logical_or)+" ";
				 first = false;
				 if(!pluginStage.isEmpty())
					 clause += pluginStage+".";
				 if(!ISAXes.get(instructionName).GetNode(operation).GetValidInterf())
					 clause += "input(IS_"+instructionName+")";
				 else 
					 clause += "(input(IS_"+instructionName+") && io."+CreateNodeName(operation+"_valid",stage,"")+")";
			 }
		 }
		 return clause;
	}
	
	public String CreateClause(HashMap<String,SCAIEVInstr> ISAXes, String operation, int stage, String pluginStage) {
		String clause = "";
		 boolean first = true;
		 for(String instructionName : ISAXes.keySet()) {
			 if(ISAXes.get(instructionName).HasNode(operation) && ISAXes.get(instructionName).GetSchedNodes().get(operation).GetStartCycle()==stage) { 
				 if(!first)
					 clause += " "+dictionary.get(Words.logical_or)+" ";
				 first = false;
				 if(!pluginStage.isEmpty())
					 clause +=pluginStage+".";
				 clause += "input(IS_"+instructionName+")";
				}
		 }
		 return clause;
	}
	
	public String CreateClauseAddr(HashMap<String,SCAIEVInstr> ISAXes, String operation, int stage, String pluginStage) {
		String clause = "";
		 boolean first = true;
		 for(String instructionName : ISAXes.keySet()) {
			 if(ISAXes.get(instructionName).HasNode(operation) && ISAXes.get(instructionName).GetSchedNodes().get(operation).GetStartCycle()==stage) { 
				 if(ISAXes.get(instructionName).GetNode(operation).GetAddrInterf()) {
					 if(!first)
						 clause += " "+dictionary.get(Words.logical_or)+" ";
					 if(!pluginStage.isEmpty())
						 clause += pluginStage+".";
					 clause += "input(IS_"+instructionName+")";
					 first = false;
				 }
			 }
		 }
		 return clause;
	}
	

	public String CreateDeclReg(String operation, int stage, String instr) {
		String decl = "";
		String size = "";
		String init = "0";
		String input = "_o";
		if(coreBackend.NodeIn(operation, stage))
			input = "_i";
		if(coreBackend.NodeSize(operation, stage)>1)
			size = coreBackend.NodeDataT(operation, stage) + "("+coreBackend.NodeSize(operation, stage)+" bits)"; 
		else { //1b (Bool?)
			size =  coreBackend.NodeDataT(operation, stage);
			init = "False"; // considering Bool...
		}
		decl = "val "+CreateNodeName(operation,stage,instr).replace(input,"_reg")+" = Reg("+size+") init("+init+");\n";
		return decl;
	}
	
	public String CreateDeclSig(String operation, int stage, String instr) {
		String decl = "";
		String size = "";
		if(coreBackend.NodeSize(operation, stage)>1)
			size = coreBackend.NodeDataT(operation, stage) + "("+coreBackend.NodeSize(operation, stage)+" bits)"; 
		else 
			size =  coreBackend.NodeDataT(operation, stage);	
		decl = "val "+CreateNodeName(operation,stage,instr)+" = "+size+";\n";
		return decl;		
	}
	
	public String CreateSpawnLogicWrRD(String instr, int stage, String priority) {
		String body = "";
		if(!priority.isEmpty())
			priority = " "+this.dictionary.get(Words.logical_and)+" ! ("+priority+")"; 
		body += "when("+this.CreateNodeName(BNode.WrRD_spawn_valid, stage, instr).replace("_i","_reg")+" && "+BNode.ISAX_fire2_regF_reg+priority+") {\n"
			 +	tab+ "writeStage.arbitration.isRegFSpawn := True\n"
			 +  tab+ "writeStage.output(REGFILE_WRITE_DATA) := "+this.CreateNodeName(BNode.WrRD_spawn, stage, instr).replace("_i","_reg")+"\n" 
			 +  tab+ this.CreateNodeName(BNode.WrRD_spawn_valid, stage, instr).replace("_i","_reg")+" := False\n"
			 +  tab+"io.commited_rd_spawn_"+stage+"_o :=  "+this.CreateNodeName(BNode.WrRD_spawn_addr, stage, instr).replace("_i","_reg")+";\n"
			 +  tab+"writeStage.output(INSTRUCTION) := ((11 downto 7) ->"+this.CreateNodeName(BNode.WrRD_spawn_addr, stage, instr).replace("_i","_reg")+", default -> false)\n"
			 + "}\n"; 
		return body;
		
	}
	
	
	public String CreateSpawnTrigMem(String operation,int stage, String instr, int tabNr ) {
		String body = "";
		String wrMemData = "";
		if(operation.contains(BNode.WrMem_spawn))
			wrMemData= tab.repeat(tabNr+1)+this.CreateNodeName(BNode.WrMem_spawn, stage, instr).replace("_i", "_reg")+":= io."+CreateNodeName(BNode.WrMem_spawn,stage,instr)+"\n";
		body =      tab.repeat(tabNr)+"when(io."+CreateNodeName(BNode.Mem_spawn_valid,stage,instr)+"){\n"
				+	tab.repeat(tabNr+1)+CreateNodeName(BNode.Mem_spawn_addr,stage,instr).replace("_i","_reg")+" := io."+CreateNodeName(BNode.Mem_spawn_addr,stage,instr)+"\n"
				+	tab.repeat(tabNr+1)+CreateNodeName(BNode.Mem_spawn_valid,stage,instr).replace("_i","_reg")+" := io."+CreateNodeName(BNode.Mem_spawn_valid,stage,instr)+"\n"
				+	wrMemData 
				+	tab.repeat(tabNr+1)+ BNode.ISAX_fire_mem_reg+" := True\n"
				+   tab.repeat(tabNr)+"}\n";
		return body;		
	}
	
	public String CreateSpawnCMDMem(String operation,int stage, String instr, int tabNr, String priority) {
		String body = "";
		String write = "True";
		if(operation.contains(BNode.RdMem_spawn))
			write = "False";
		String wrMemData = "";
		if(!priority.isEmpty())
			priority = " "+this.dictionary.get(Words.logical_and)+" ! ("+priority+")"; 
		if(operation.contains(BNode.WrMem_spawn))
			wrMemData= tab.repeat(tabNr+1)+"dBusAccess.cmd.data  := "+CreateNodeName(BNode.WrMem_spawn,stage,instr).replace("_i","_reg")+"\n";
		body = 	  tab.repeat(tabNr)  +"when("+ CreateNodeName(BNode.Mem_spawn_valid,stage,instr).replace("_i","_reg")+" && "+BNode.ISAX_fire2_mem_reg+priority+"){\n"
				+ tab.repeat(tabNr+1)+"dBusAccess.cmd.valid := True \n"
				+ tab.repeat(tabNr+1)+"dBusAccess.cmd.size := 2\n"
		 		+ tab.repeat(tabNr+1)+"dBusAccess.cmd.write := "+write+"\n"
		 		+ wrMemData
		 		+ tab.repeat(tabNr+1)+"dBusAccess.cmd.address :="+CreateNodeName(BNode.Mem_spawn_addr,stage,instr).replace("_i","_reg")+"\n"
		 		+ tab.repeat(tabNr)  +"}\n";
		 return body;
	}
	
	public String CreateSpawnCMDRDYMem(String operation,int stage, String instr, int tabNr, String priority) {
		String body = "";
		String write = "True";
		if(!priority.isEmpty())
			priority = " "+this.dictionary.get(Words.logical_and)+" !("+priority+")"; 
		if(operation.contains(BNode.WrMem_spawn))
			body =    tab.repeat(tabNr)  +"when("+ CreateNodeName(BNode.Mem_spawn_valid,stage,instr).replace("_i","_reg")+" && "+BNode.ISAX_fire2_mem_reg+priority+") {\n"
					+ tab.repeat(tabNr+1)+"state := State.CMD\n"
					+ tab.repeat(tabNr+1)+"io."+CreateNodeName(BNode.WrMem_spawn_valid, stage, instr)+":= True\n"
					+ tab.repeat(tabNr+1)+"when("+BNode.ISAX_sum_spawn_mem_s+" === 1) {\n"
					+ tab.repeat(tabNr+2)+"state := State.IDLE\n"
					+ tab.repeat(tabNr+2)+BNode.ISAX_fire2_mem_reg+" := False\n"
					+ tab.repeat(tabNr+1)+"}\n"
					+ tab.repeat(tabNr+1)+this.CreateNodeName(BNode.Mem_spawn_valid, stage, instr).replace("_i", "_reg")+" := False\n"
					+ tab.repeat(tabNr)  +"}\n";
		else 
			body =    tab.repeat(tabNr)  +"when("+this.CreateNodeName(BNode.Mem_spawn_valid, stage, instr).replace("_i", "_reg")+" && "+BNode.ISAX_fire2_mem_reg+priority+") {\n"
					+ tab.repeat(tabNr+1)+"state := State.RESPONSE\n"
					+ tab.repeat(tabNr)  +"}\n";
		 return body;
	}
	public String CreateSpawnRSPRDYMem(int stage, String instr, int tabNr, String priority) {
		String body = "";
		if(!priority.isEmpty())
			priority = " "+this.dictionary.get(Words.logical_and)+" !("+priority+")"; 
		body = tab.repeat(tabNr)+"when("+this.CreateNodeName(BNode.Mem_spawn_valid, stage, instr).replace("_i", "_reg") +" && "+BNode.ISAX_fire2_mem_reg+priority+") {\n"
				    + tab.repeat(tabNr+1)+"io."+CreateNodeName(BNode.RdMem_spawn, stage, instr) +":= dBusAccess.rsp.data\n"
				    + tab.repeat(tabNr+1)+"io."+CreateNodeName(BNode.RdMem_spawn_valid, stage, instr) +" := True\n"
					+ tab.repeat(tabNr+1)+"state := State.CMD\n"
					+ tab.repeat(tabNr+1)+"when("+BNode.ISAX_sum_spawn_mem_s+" === 1) {\n"
					+ tab.repeat(tabNr+2)+"state := State.IDLE\n"
					+ tab.repeat(tabNr+2)+ BNode.ISAX_fire2_mem_reg+" := False\n"
					+ tab.repeat(tabNr+1)+"}\n"
					+ tab.repeat(tabNr+1)+this.CreateNodeName(BNode.Mem_spawn_valid, stage, instr).replace("_i", "_reg") +" := False\n"
					+ tab.repeat(tabNr)+"}\n";
		 return body;
	}
	

	
}
