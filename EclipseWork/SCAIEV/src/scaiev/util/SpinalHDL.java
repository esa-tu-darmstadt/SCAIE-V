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
		dictionary.put("module","class");
		dictionary.put("endmodule","}");
		dictionary.put("reg","Reg");
		dictionary.put("wire","UInt");		
		dictionary.put("assign","");
		dictionary.put("assign_eq",":=");
		dictionary.put("logical_or","||");
		dictionary.put("bitwise_or","|");
		dictionary.put("input","in");
		dictionary.put("output","out");
		this.toFile = toFile;
		this.coreBackend = core;
	}
	
	
	public void CloseBrackets () {
		this.toFile.nrTabs--;
		this.toFile.UpdateContent(this.currentFile, "}");		
	}
	

	public String CreateInterface(String operation, int stage, String instr) {
		String in = dictionary.get("output");
		if(coreBackend.NodeIn(operation, stage))
			in =dictionary.get("input");
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
					 clause += " "+dictionary.get("logical_or")+" ";
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
					 clause += " "+dictionary.get("logical_or")+" ";
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
						 clause += " "+dictionary.get("logical_or")+" ";
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
	
	public String CreateSpawnLogicWrRD(String instr, int stage) {
		String body = "";
		body += "when("+this.CreateNodeName(BNode.WrRD_spawn_valid, stage, instr).replace("_i","_reg")+") {\n"
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
			wrMemData= tab.repeat(tabNr+1)+this.CreateNodeName(BNode.WrMem_spawn, stage, instr)+":= io."+CreateNodeName(BNode.WrMem_spawn,stage,instr)+"\n";
		body =      tab.repeat(tabNr)+"when(io."+CreateNodeName(BNode.Mem_spawn_valid,stage,instr)+"){\n"
				+	tab.repeat(tabNr+1)+CreateNodeName(BNode.Mem_spawn_addr,stage,instr).replace("_i","_reg")+" := io."+CreateNodeName(BNode.Mem_spawn_addr,stage,instr)+"\n"
				+	tab.repeat(tabNr+1)+CreateNodeName(BNode.Mem_spawn_valid,stage,instr).replace("_i","_reg")+" := io."+CreateNodeName(BNode.Mem_spawn_valid,stage,instr)+"\n"
				+	wrMemData 
				+	tab.repeat(tabNr+1)+ "fire_mem := True\n"
				+   tab.repeat(tabNr)+"}\n";
		return body;		
	}
	
	public String CreateSpawnCMDMem(String operation,int stage, String instr, int tabNr) {
		String body = "";
		String write = "True";
		if(operation.contains(BNode.RdMem_spawn))
			write = "False";
		String wrMemData = "";
		if(operation.contains(BNode.WrMem_spawn))
			wrMemData= tab.repeat(tabNr+1)+"dBusAccess.cmd.data  := "+CreateNodeName(BNode.WrMem_spawn,stage,instr).replace("_i","_reg")+"\n";
		body = 	  tab.repeat(tabNr)  +"when("+ CreateNodeName(BNode.Mem_spawn_valid,stage,instr).replace("_i","_reg")+" && fire_mem_2){\n"
				+ tab.repeat(tabNr+1)+"dBusAccess.cmd.valid := True \n"
				+ tab.repeat(tabNr+1)+"dBusAccess.cmd.size := 2\n"
		 		+ tab.repeat(tabNr+1)+"dBusAccess.cmd.write := "+write+"\n"
		 		+ wrMemData
		 		+ tab.repeat(tabNr+1)+"dBusAccess.cmd.address :="+CreateNodeName(BNode.Mem_spawn_addr,stage,instr).replace("_i","_reg")+"\n"
		 		+ tab.repeat(tabNr)  +"}\n";
		 return body;
	}
	
	public String CreateSpawnCMDRDYMem(String operation,int stage, String instr, int tabNr) {
		String body = "";
		String write = "True";
		if(operation.contains(BNode.WrMem_spawn))
			body =    tab.repeat(tabNr)  +"when("+ CreateNodeName(BNode.Mem_spawn_valid,stage,instr).replace("_i","_reg")+" && fire_mem_2) {\n"
					+ tab.repeat(tabNr+1)+"state := State.CMD\n"
					+ tab.repeat(tabNr+1)+this.CreateNodeName(BNode.WrMem_spawn_valid, stage, instr)+"<= True\n"
					+ tab.repeat(tabNr+1)+"when(sum_mem==1) {\n"
					+ tab.repeat(tabNr+2)+"state := State.IDLE\n"
					+ tab.repeat(tabNr+2)+"fire2 := False\n"
					+ tab.repeat(tabNr+1)+"}\n"
					+ tab.repeat(tabNr+1)+this.CreateNodeName(BNode.Mem_spawn_valid, stage, instr).replace("_i", "_reg")+" := False\n"
					+ tab.repeat(tabNr)  +"}\n";
		else 
			body =    tab.repeat(tabNr)  +"when("+this.CreateNodeName(BNode.Mem_spawn_valid, stage, instr).replace("_i", "_reg")+" && fire_mem_2) {\n"
					+ tab.repeat(tabNr+1)+"state := State.RESPONSE\n"
					+ tab.repeat(tabNr)  +"}\n";
		 return body;
	}
	public String CreateSpawnRSPRDYMem(int stage, String instr, int tabNr) {
		String body = "";
		body = tab.repeat(tabNr)+"when("+this.CreateNodeName(BNode.Mem_spawn_valid, stage, instr).replace("_i", "_reg") +" && fire_mem_2) {\n"
				    + tab.repeat(tabNr+1)+this.CreateNodeName(BNode.RdMem_spawn, stage, instr) +"<= dBusAccess.rsp.data\n"
				    + tab.repeat(tabNr+1)+this.CreateNodeName(BNode.RdMem_spawn_valid, stage, instr) +" <= True\n"
					+ tab.repeat(tabNr+1)+"state := State.CMD\n"
					+ tab.repeat(tabNr+1)+"when(sum_mem==1) {\n"
					+ tab.repeat(tabNr+2)+"state := State.IDLE\n"
					+ tab.repeat(tabNr+2)+"fire2 := False\n"
					+ tab.repeat(tabNr+1)+"}\n"
					+ tab.repeat(tabNr+1)+this.CreateNodeName(BNode.Mem_spawn_valid, stage, instr).replace("_i", "_reg") +" := False\n"
					+ tab.repeat(tabNr)+"}\n";
		 return body;
	}
	

	
}
