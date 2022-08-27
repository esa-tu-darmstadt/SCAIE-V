package scaiev.backend;

import java.util.HashMap;
import java.util.HashSet;

import scaiev.coreconstr.Core;
import scaiev.frontend.SCAIEVInstr;
import scaiev.util.FileWriter;
import scaiev.util.Lang;
import scaiev.util.ToWrite;
import scaiev.util.VHDL;

class Parse {
	public static String declare = "architecture"; 
	public static String behav  = "begin";
	//public String behav = "end architecture";
}
public class Orca extends CoreBackend {
	public String tab = "    ";
	HashSet<String> AddedIValid = new HashSet<String>();
	
	public  String 			pathORCA = "CoresSrc/orca/ip/orca/hdl";
	private Core 			orca_core;
	private HashMap <String,SCAIEVInstr>  ISAXes;
	private HashMap<String, HashMap<Integer,HashSet<String>>> op_stage_instr;
	private FileWriter 		toFile = new FileWriter();
	private String 			extension_name;
	private VHDL            language = new VHDL(toFile,this);
	private String 			topModule = "orca";
	private int lastJumpStage = 0;
	private int nrTabs = 0;
	public void Generate (HashMap <String,SCAIEVInstr> ISAXes, HashMap<String, HashMap<Integer,HashSet<String>>> op_stage_instr, String extension_name, Core core) { // core needed for verification purposes
		// Set variables
		this.orca_core = core;
		this.ISAXes = ISAXes;
		this.op_stage_instr = op_stage_instr;
		this.extension_name = extension_name;
		ConfigOrca();
		language.clk = "clk";
		language.reset = "reset";
		
		
		IntegrateISAX_Defaults();
		 // Only for orca, make instr in stage 2 visible, if not used logic will be redundant but also removed as it has no driver 
		 if(!(op_stage_instr.containsKey(BNode.RdInstr) && op_stage_instr.get(BNode.RdInstr).containsKey(2)))
			 language.UpdateInterface(topModule,BNode.RdInstr, "",2,false,false);	
		IntegrateISAX_IOs(topModule);
		IntegrateISAX_NoIllegalInstr();
		IntegrateISAX_RdIvalid();
		IntegrateISAX_WrStall();
		IntegrateISAX_WrFlush();
		IntegrateISAX_RdFlush();
		IntegrateISAX_WrRD();
		IntegrateISAX_Spawn(BNode.WrRD_spawn, true);
		IntegrateISAX_Spawn(BNode.WrMem_spawn,false); // false = don't re-declare isax_fire signals
		IntegrateISAX_Spawn(BNode.RdMem_spawn,true); 
		IntegrateISAX_Mem();
		IntegrateISAX_WrPC();
		//IntegrateISAX_Mem();
		toFile.WriteFiles(language.GetDictModule(),language.GetDictEndModule());
	
	}
	
	private void IntegrateISAX_Defaults() {
		for(int i=0;i<=orca_core.maxStage;i++)
			if (this.ContainsOpInStage(BNode.WrPC, i))
				lastJumpStage = i;
	
		for(int i=0;i<=orca_core.maxStage;i++) {
			if(i>lastJumpStage) {
				toFile.UpdateContent(this.ModFile("orca_core"),Parse.behav,	  new ToWrite("ISAX_to_pc_correction_valid_"+i+"_s <= '0';\n",false,true,""));
				toFile.UpdateContent(this.ModFile("orca_core"), Parse.declare, new ToWrite("signal ISAX_to_pc_correction_valid_"+i+"_s : std_logic;",false,true,""));
			}
			if(!this.ContainsOpInStage(BNode.WrFlush, i)) {
				toFile.UpdateContent(this.ModFile("orca_core"),Parse.behav,	  new ToWrite(language.CreateLocalNodeName(BNode.WrFlush, i, "") + " <= '0';\n",false,true,""));
				toFile.UpdateContent(this.ModFile("orca_core"), Parse.declare, new ToWrite(language.CreateDeclSig(BNode.WrFlush, i, ""),false,true,""));
			}
		}
	}
	
	private void IntegrateISAX_IOs(String topModule) {
		language.GenerateAllInterfaces(topModule,op_stage_instr,ISAXes,  orca_core ,"");			
	
	
	}
	
	private void IntegrateISAX_RdFlush() {
		if(op_stage_instr.containsKey(BNode.RdFlush)) {
			for(int stage : op_stage_instr.get(BNode.RdFlush).keySet()) {
				if(!this.ContainsOpInStage(BNode.WrFlush, stage)) {
					toFile.UpdateContent(this.ModFile(NodeAssignM(BNode.WrFlush, stage)),Parse.behav,	  new ToWrite(language.CreateLocalNodeName(BNode.WrFlush, stage, "") + " <= '0';\n",false,true,""));
				}
			}
		}
	}
	private void IntegrateISAX_WrFlush() {
		if(op_stage_instr.containsKey(BNode.WrFlush))
		for(int stage : op_stage_instr.get(BNode.WrFlush).keySet()) {
			toFile.UpdateContent(this.ModFile(NodeAssignM(BNode.WrFlush, stage)), Parse.declare, new ToWrite(language.CreateDeclSig(BNode.WrFlush, stage, ""),false,true,""));
			if(stage ==1)
				toFile.ReplaceContent(this.ModFile(NodeAssignM(BNode.WrFlush, stage)),"to_decode_valid                    => ", new ToWrite("to_decode_valid                    => to_decode_valid or "+language.CreateLocalNodeName(BNode.WrFlush, stage,"")+",", false, true , "") );
			if(stage ==2 && !(this.ContainsOpInStage(BNode.WrPC_valid, 3) || this.ContainsOpInStage(BNode.WrPC_valid, 4))) // TODO, to simulate this
				toFile.ReplaceContent(this.ModFile(NodeAssignM(BNode.WrFlush, stage)),"quash_decode       => ", new ToWrite("quash_decode       => to_pc_correction_valid or "+language.CreateLocalNodeName(BNode.WrFlush, stage,"")+",", false, true , "") );
			if(stage ==3)
				toFile.ReplaceContent(this.ModFile(NodeAssignM(BNode.WrFlush, stage))," to_execute_valid            => to_execute_valid,", new ToWrite(" to_execute_valid            => to_execute_valid or "+language.CreateLocalNodeName(BNode.WrFlush, stage,"")+",", false, true , "") );
		}
		
	}
	private void IntegrateISAX_NoIllegalInstr() {
		String isISAXSignal = "ISAX_isisax";
		String textIllegal = "if ("+isISAXSignal+" = '1') then\n"
				+ tab+ "from_opcode_illegal <= '0';\n"
				+ "else\n"
				+ tab+"from_opcode_illegal <= '1';\n"
				+ "end if;";
		toFile.ReplaceContent(this.ModFile("execute"), "from_opcode_illegal <= '1';",new ToWrite(textIllegal,true,false,"when others =>"));
		String aluSelect = "if ("+isISAXSignal+" = '0') then  --  otherwise all asaxe will be considered ALU and will write to regfile \n"
				+ tab + "alu_select <= '1';\n"
				+ "else\n"
				+ tab + "alu_select <= '0';\n"
				+ "end if;";
		toFile.ReplaceContent(this.ModFile("execute"), "alu_select <= '1';",new ToWrite(aluSelect,true,false,"when others =>"));
		toFile.UpdateContent(this.ModFile("execute"),Parse.declare,  new ToWrite("signal "+isISAXSignal+": std_logic;\n",false,true,""));
		toFile.ReplaceContent(this.ModFile("execute"),"process (opcode) is",new ToWrite("process (opcode,ISAX_isisax) is",false,true,""));
		
		HashSet<String> allISAXes =  new HashSet<String>();
		allISAXes.addAll(ISAXes.keySet());
		language.CreateText1or0(isISAXSignal,language.CreateAllEncoding(allISAXes, ISAXes, "to_execute_instruction"));
	}
	
	
	
	// new ToWrite(language.CreateDeclReg(nodeName,1,""),false,true,"")
	private void IntegrateISAX_RdIvalid(){
		if(op_stage_instr.containsKey(BNode.RdIValid)) {
			for(int stage : op_stage_instr.get(BNode.RdIValid).keySet()) {
				String assignInstr = this.NodeAssign(BNode.RdInstr, stage);
				int actualAssigStage = stage;
				if(assignInstr.isEmpty())
					actualAssigStage = stage-1;
				String flushSig = language.CreateNodeName(BNode.RdFlush, actualAssigStage, "");
				if(!this.ContainsOpInStage(BNode.RdFlush, actualAssigStage)) {
					flushSig = language.CreateLocalNodeName(BNode.RdFlush, actualAssigStage, "");
					toFile.UpdateContent(this.ModFile(NodeAssignM(BNode.RdFlush, stage)),Parse.behav,	  new ToWrite(flushSig +" <= "+this.NodeAssign(BNode.RdFlush, stage)+";\n",false,true,""));				
					toFile.UpdateContent(this.ModFile(NodeAssignM(BNode.RdFlush, actualAssigStage)), Parse.declare, new ToWrite(language.CreateDeclSig(BNode.RdFlush, actualAssigStage, ""),false,true,""));
					
				}
				flushSig = " and ("+flushSig+" = '0')";
				for(String instrName : op_stage_instr.get(BNode.RdIValid).get(stage)) {
					if(assignInstr.isEmpty()) {
						String  assignIValid = language.CreateLocalNodeName(BNode.RdIValid, actualAssigStage, instrName);
						toFile.UpdateContent(this.ModFile(NodeAssignM(BNode.RdIValid, stage)), Parse.declare, new ToWrite(language.CreateDeclSig(BNode.RdIValid, actualAssigStage, instrName),false,true,"")); // it currently is impossible to request rdivalid in 2 stages, so by default in stage-1 we have _s, no _o
						toFile.UpdateContent(this.ModFile(NodeAssignM(BNode.RdIValid, stage)),Parse.behav,	  new ToWrite(language.CreateTextRegReset(language.CreateNodeName(BNode.RdIValid, stage, instrName),assignIValid),false,true,""));
						toFile.UpdateContent(this.ModFile(NodeAssignM(BNode.RdIValid, stage)),Parse.behav,    new ToWrite(language.CreateText1or0(assignIValid, language.CreateAllEncoding(new HashSet<String>() {{add(instrName);}}, ISAXes, this.NodeAssign(BNode.RdInstr, actualAssigStage) )+ flushSig ),false,true,""));
					} 
					else {
						String addText = language.CreateText1or0( language.CreateNodeName(BNode.RdIValid, stage, instrName), language.CreateAllEncoding(new HashSet<String>() {{add(instrName);}}, ISAXes, this.NodeAssign(BNode.RdInstr, stage) )+flushSig );
						toFile.UpdateContent(this.ModFile(NodeAssignM(BNode.RdIValid, stage)),Parse.behav,    new ToWrite(addText,false,true,""));
					}
				}
			}
		}
	}
		
	private void IntegrateISAX_WrStall() {
		HashMap <Integer, String> readySigs = new HashMap <Integer, String>();
		readySigs.put(0, "decode_to_ifetch_ready");
		readySigs.put(1, "decode_to_ifetch_ready");
		readySigs.put(2, "execute_to_decode_ready");
		HashMap <Integer, String> readyReplace = new HashMap <Integer, String>();
		readyReplace.put(0, "to_ifetch_ready             =>");
		readyReplace.put(1, "to_ifetch_ready             =>");
		readyReplace.put(2, "to_decode_ready              =>");
		
		if(op_stage_instr.containsKey(BNode.WrStall)) {
			for(int stage = 2; stage>0 ; stage--) {
				if(op_stage_instr.get(BNode.WrStall).containsKey(stage)) { // not necessary to make stal_0 as composition of stall_1, stall_2...this is implicitly done in core
					String addStallClause = "not ("+language.CreateNodeName(BNode.WrStall, stage ,"")+") and ";
					if(op_stage_instr.containsKey(BNode.WrRD_spawn) && (stage==this.orca_core.maxStage+1))
						addStallClause += "(not ISAX_spawn_regF_stall_s) and";
					toFile.UpdateContent(this.ModFile(NodeAssignM(BNode.WrStall, stage)),Parse.behav,    new ToWrite("ISAX_to_"+stage+"_ready <= "+addStallClause+" "+readySigs.get(stage)+";",false,true,""));
					toFile.UpdateContent(this.ModFile(NodeAssignM(BNode.WrStall, stage)),Parse.declare,  new ToWrite("signal ISAX_to_"+stage+"_ready: std_logic\n",false,true,""));
					toFile.ReplaceContent(this.ModFile(NodeAssignM(BNode.WrStall, stage)), readyReplace.get(stage),new ToWrite(readyReplace.get(stage)+"ISAX_to_"+stage+"_ready,\n",false,true,""));
				}
			}
		}
		if(op_stage_instr.containsKey(BNode.WrStall) && op_stage_instr.get(BNode.WrStall).containsKey(3))
			toFile.UpdateContent(this.ModFile(NodeAssignM(BNode.WrStall, 3)), "((not vcp_select) or vcp_ready)))",new ToWrite("(not WrStall_3_i) and",false,true,"", true));
		
	}
	private void IntegrateISAX_WrRD(){
		String decl_lineToBeInserted = "";
		String assign_lineToBeInserted = "";
	    String clause_addr= "";
		String clause_valid = "";
		String clause_no_valid ="";	
		// Connect signals to top interface (a little more complicated logic than a simple assign)
		int stage = this.orca_core.GetNodes().get(BNode.WrRD).GetLatest();
		if(op_stage_instr.containsKey(BNode.WrRD) && op_stage_instr.get(BNode.WrRD).containsKey(stage)) {	
			for(String instr_name : op_stage_instr.get(BNode.WrRD).get(stage)) {
				SCAIEVInstr instr = ISAXes.get(instr_name);
				 String rdIValid = language.CreateNodeName(BNode.RdIValid, stage-1, instr_name);
				 if(!(instr.HasNode(BNode.RdIValid) && instr.GetNode(BNode.RdIValid).GetStartCycle()== (stage-1)))
				 {						
					 rdIValid = language.CreateLocalNodeName(BNode.RdIValid, stage-1, instr_name);
					 if (!(stage == 4 && instr.HasNode(BNode.RdIValid) && instr.GetNode(BNode.RdIValid).GetStartCycle()== (stage)))
						 decl_lineToBeInserted += language.CreateDeclSig(BNode.RdIValid, stage-1, instr_name);
					 String instr_field_name  = this.NodeAssign(BNode.RdInstr, stage-1); // TODO this works only considering that in stage 3 there is an instr sig in the same file. Would not work if assig Instr signal would be in another subfile for exp
					 assign_lineToBeInserted += language.CreateText1or0(rdIValid, language.CreateAllEncoding(new HashSet<String>() {{add(instr_name);}}, ISAXes, instr_field_name)); 
				 }						 
				 if(instr.GetNode(BNode.WrRD).GetAddrInterf()) {
					 if (clause_addr!="") {
						 clause_addr += " or ";
					 }
					 clause_addr += rdIValid;
				 }
				 if(instr.GetNode(BNode.WrRD).GetValidInterf()) {
					 if (clause_valid!="") {
						 clause_valid += " or ";
					 }
					 clause_valid += rdIValid;
				 } else {
					 if (clause_no_valid!="") {
						 clause_no_valid += " or ";
					 }
					 clause_no_valid += rdIValid;
				 }									 					 
			}
		}
		String wrrdDataBody = "";
		String wrrdSelectBody = "";
		String wrrdNoValidBody = "";
		String wrrdValidBody = "";
		String clause_rf_valid = "";
		String ISAX_execute_to_rf_data_s = "ISAX_execute_to_rf_data_s";
		String ISAX_execute_to_rf_valid_s = "ISAX_execute_to_rf_valid_s";
		String ISAX_execute_to_rf_select_s = "ISAX_execute_to_rf_select_s";

		//decl_lineToBeInserted += language.CreateSpawnDeclareRegs(op_stage_instr.get(BNode.WrRD_spawn).get(this.orca_core.maxStage+1), this.orca_core.maxStage+1);
		if(op_stage_instr.containsKey(BNode.WrRD_spawn)) {
			wrrdDataBody += language.CreateSpawnLogicWrRD(op_stage_instr.get(BNode.WrRD_spawn).get(this.orca_core.maxStage+1),this.orca_core.maxStage+1, ISAX_execute_to_rf_data_s) + "\nels";
			clause_rf_valid += " ("+BNode.ISAX_fire2_regF_reg+"  = '1' ) ";
			wrrdSelectBody += language.CreateSpawnLogicWrRDAddr(op_stage_instr.get(BNode.WrRD_spawn).get(this.orca_core.maxStage+1),this.orca_core.maxStage+1,ISAX_execute_to_rf_select_s)+ "\nels";
			
		}
		if(ContainsOpInStage(BNode.WrRD,stage)) {
			
			if(!clause_no_valid.isEmpty()) {
				wrrdDataBody += "if( ISAX_wrReg_noValidSig_s = '1' ) then\n"
					+ tab+ISAX_execute_to_rf_data_s+" <= "+language.CreateNodeName(BNode.WrRD, stage, "")+"; \n"
					+ "    els";
				wrrdNoValidBody += language.CreateTextRegReset("ISAX_wrReg_noValidSig_s",clause_no_valid);
				decl_lineToBeInserted += "signal ISAX_wrReg_noValidSig_s : std_logic;\n";
				if(!clause_rf_valid.isEmpty())
					clause_rf_valid += " or ";
				clause_rf_valid += " ( ISAX_wrReg_noValidSig_s   = '1' ) ";
			}
			if(!clause_valid.isEmpty()) {
				wrrdDataBody += "if( ISAX_wrReg_withValidSig_s = '1' and "+language.CreateNodeName(BNode.WrRD_valid, stage, "")+" = '1' ) then\n"
						+ tab+ISAX_execute_to_rf_data_s+" <= "+language.CreateNodeName(BNode.WrRD, stage, "")+"; \n"
						+ "    els";
				
				wrrdValidBody += language.CreateTextRegReset("ISAX_wrReg_withValidSig_s",clause_valid);
				decl_lineToBeInserted += "signal ISAX_wrReg_withValidSig_s : std_logic;\n";
				if(!clause_rf_valid.isEmpty())
					clause_rf_valid += " or ";
				clause_rf_valid += " ( ISAX_wrReg_withValidSig_s   = '1' ) ";
			}
			
			if(!clause_addr.isEmpty()) {
				wrrdSelectBody += "if("+clause_addr+")\n"
						+ tab+ ISAX_execute_to_rf_select_s+" <= "+language.CreateNodeName(BNode.WrRD_addr, stage, "")+";\n"
						+ "els";				
			}
			
			// Datahaz. ISAX to standard 
			toFile.ReplaceContent(this.ModFile("execute"),"from_alu_data  => from_alu_data,",new ToWrite("from_alu_data  => ISAX_from_alu_data,",false,true,""));
			toFile.UpdateContent(this.ModFile("execute"),Parse.declare,new ToWrite("signal ISAX_from_alu_data : std_logic_vector(REGISTER_SIZE-1 downto 0);",false,true,""));
			toFile.UpdateContent(this.ModFile("execute"),Parse.behav, new ToWrite("from_alu_data <= "+language.CreateNodeName(BNode.WrRD, stage, "")+" when "+language.CreateNodeName("ISAX_REG", stage, "")+" = '1' else ISAX_from_alu_data;",false,true,""));
			String fwdclause = "";
			if(!clause_no_valid.isEmpty())
				fwdclause += "ISAX_wrReg_noValidSig_s";
			if(!clause_valid.isEmpty())
				fwdclause += language.OpIfNEmpty(fwdclause,"or") + "ISAX_wrReg_withValidSig_s";
			toFile.UpdateContent(this.ModFile("orca_core"),Parse.behav,new ToWrite(language.CreateLocalNodeName("ISAX_REG", stage, "")+" <= "+fwdclause+";\n",false,true,""));
			language.UpdateInterface("orca_core","ISAX_REG", "",stage,false,false);
			
		} 
		if(!wrrdDataBody.isEmpty()) {
			wrrdDataBody += "e \n"
					+ tab+ISAX_execute_to_rf_data_s+" <= execute_to_rf_data;\n"
					+ "    end if;";
			wrrdSelectBody += "e\n"
					+ tab+ ISAX_execute_to_rf_select_s+" <= execute_to_rf_select;\n"
					+ "end if;";
		}
	
		
		if(op_stage_instr.containsKey(BNode.WrRD_spawn) || op_stage_instr.containsKey(BNode.WrRD)) {
			decl_lineToBeInserted += "signal "+ISAX_execute_to_rf_valid_s+" : std_logic;\n";
			decl_lineToBeInserted += "signal "+ISAX_execute_to_rf_data_s+" : std_logic_vector(REGISTER_SIZE-1 downto 0);\n";
			decl_lineToBeInserted += "signal "+ISAX_execute_to_rf_select_s+" : std_logic_vector(5-1 downto 0);\n";
			assign_lineToBeInserted = wrrdValidBody + wrrdNoValidBody;
			// write in file 
			toFile.UpdateContent(this.ModFile("orca_core"),Parse.declare,new ToWrite(decl_lineToBeInserted,false,true,""));
			toFile.UpdateContent(this.ModFile("orca_core"),Parse.behav, new ToWrite(assign_lineToBeInserted,false,true,""));
			toFile.UpdateContent(this.ModFile("orca_core"),Parse.behav, new ToWrite(language.CreateInProc(false, wrrdDataBody)+ language.CreateInProc(false, wrrdSelectBody),false,true,""));
			toFile.UpdateContent(this.ModFile("orca_core"),Parse.behav, new ToWrite(language.CreateTextISAXorOrig(clause_rf_valid,ISAX_execute_to_rf_valid_s,"'1'","execute_to_rf_valid"),false,true,""));
			toFile.ReplaceContent(this.ModFile("orca_core"),"to_rf_select => execute_to_rf_select", new ToWrite("to_rf_select => "+ISAX_execute_to_rf_select_s+",",true,false,"D : decode"));
			toFile.ReplaceContent(this.ModFile("orca_core"),"to_rf_data   =>", new ToWrite("to_rf_data => "+ISAX_execute_to_rf_data_s+",",true,false,"D : decode"));
			toFile.ReplaceContent(this.ModFile("orca_core"),"to_rf_valid  =>", new ToWrite("to_rf_valid => "+ISAX_execute_to_rf_valid_s+",",true,false,"D : decode"));
			
			if(op_stage_instr.containsKey(BNode.WrRD)) {
				String allwrrd = clause_no_valid;
				if(!clause_no_valid.isEmpty() && !clause_valid.isEmpty())
					allwrrd += " or ";
				allwrrd += clause_valid;
				String frwrd = language.CreateLocalNodeName("ISAX_FWD_ALU", stage, "")+" <= "+ allwrrd+";\n";
				toFile.UpdateContent(this.ModFile("orca_core"),Parse.behav, new ToWrite(frwrd,false,true,""));
				
				toFile.ReplaceContent(this.ModFile("execute"),"if to_alu_valid = '1' and from_alu_ready = '1'", new ToWrite("if ( to_alu_valid = '1' and from_alu_ready = '1') or (ISAX_FWD_ALU_4_i = '1') then",false,true,""));				
				language.UpdateInterface("orca_core","ISAX_FWD_ALU", "",4,false,false);
			}
			if(op_stage_instr.containsKey(BNode.WrRD_spawn) ) {
				int spawnStage = this.orca_core.maxStage+1;		

				if(op_stage_instr.get(BNode.WrRD_spawn).get(spawnStage).size() >1)
					toFile.UpdateContent(this.ModFile("orca_core"),Parse.behav, new ToWrite(BNode.ISAX_sum_spawn_regF_s + " <= ("+language.allISAXNameText(" + ", "(unsigned'('0' & "+BNode.WrRD_spawn_valid+"_", "_"+spawnStage+"_reg))", op_stage_instr.get(BNode.WrRD_spawn).get(spawnStage) )+");\n",false, true, ""));
				else 
					toFile.UpdateContent(this.ModFile("orca_core"),Parse.behav, new ToWrite(BNode.ISAX_sum_spawn_regF_s + " <= "+language.allISAXNameText(" + ", BNode.WrRD_spawn_valid+"_", "_"+spawnStage+"_reg", op_stage_instr.get(BNode.WrRD_spawn).get(spawnStage) )+";\n",false, true, ""));
					
				toFile.UpdateContent(this.ModFile("orca_core"),Parse.behav, new ToWrite( BNode.ISAX_fire_regF_s + " <= "+language.allISAXNameText(" or ", BNode.WrRD_spawn_valid+"_", "_"+spawnStage+"_i", op_stage_instr.get(BNode.WrRD_spawn).get(spawnStage) )+";\n",false, true, ""));
			
			}
		}
	}	

	
	private void IntegrateISAX_Spawn(String node, boolean withFire) { // some sigs are already handled & declared in wrrd function
		int spawnStage = this.orca_core.maxStage+1;
		String priority = "";
		String spawnRegs = "";
		String toDeclare = "";
		
		if(op_stage_instr.containsKey(node)) {
			// First, let's declare our new signals
			int sizeOtherNode = 0; 
			if(node.contains(BNode.RdMem_spawn) && op_stage_instr.containsKey(BNode.WrMem_spawn))
				sizeOtherNode = this.op_stage_instr.get(BNode.WrMem_spawn).get(spawnStage).size();
			else if(node.contains(BNode.WrMem_spawn) && op_stage_instr.containsKey(BNode.RdMem_spawn))
				sizeOtherNode = this.op_stage_instr.get(BNode.RdMem_spawn).get(spawnStage).size();
			toDeclare += language.CreateSpawnDeclareSigs(this.op_stage_instr.get(node).get(spawnStage), spawnStage,node,withFire,sizeOtherNode);
			toFile.UpdateContent(this.ModFile("orca_core"),Parse.declare, new ToWrite(toDeclare,false,true,""));
			
			// Secondly let's store in regs the values we get from a valid spawn request. 
			String exprSpawnReady = "";
			if( node.contains(BNode.WrMem_spawn))
				exprSpawnReady = "(lsu_oimm_waitrequest = '0') ";
			if(node.contains(BNode.RdMem_spawn) )
				exprSpawnReady = "not(mem_spawn_valid_COSSIN_5_reg = '1') and (lsu_oimm_readdatavalid = '1')";
			for(String ISAX : op_stage_instr.get(node).get(spawnStage)) {
				spawnRegs   += language.CreateSpawnRegsLogic(ISAX, spawnStage, priority,node,exprSpawnReady);
				if(!priority.isEmpty())
					priority += " or ";
				priority   += "("+language.CreateNodeName(node+"_valid", spawnStage,ISAX).replace("_i", "_reg") + " = '1')";
			}
			toFile.UpdateContent(this.ModFile("orca_core"),Parse.behav, new ToWrite(language.CreateInProc(true, spawnRegs),false,true,""));
		
			// Thirdly, we need the logic for Fire_RegF
			String stall3 = "";
			if (ContainsOpInStage(BNode.WrStall,3))
				stall3 = language.CreateNodeName(BNode.WrStall, 3, "");
			
			boolean moreThanOne = false;
			if(node.contains(BNode.RdMem_spawn.split("d")[1])) {
				if ( (this.op_stage_instr.get(BNode.WrMem_spawn).get(spawnStage).size() + this.op_stage_instr.get(BNode.RdMem_spawn).get(spawnStage).size())>1) 
					moreThanOne = true;
			} else 
				if(this.op_stage_instr.get(node).get(spawnStage).size() >1)
					moreThanOne = true;
			if(withFire) {
				exprSpawnReady = "";
				if(node.contains(BNode.RdMem_spawn) || node.contains(BNode.WrMem_spawn))
					exprSpawnReady = "(((lsu_oimm_waitrequest = '0') and (isax_spawnRdReq = '0')) or (lsu_oimm_readdatavalid = '1'))";
				String default_logic = language.CommitSpawnFire(node, stall3, "execute_to_decode_ready", spawnStage,moreThanOne,exprSpawnReady);
				toFile.UpdateContent(this.ModFile("orca_core"),Parse.behav, new ToWrite(default_logic,false,true,""));
			}
			
		}
	}
	
	private void IntegrateISAX_Mem() {
		int stage = this.orca_core.GetNodes().get(BNode.RdMem).GetLatest(); // stage for Mem trasnfers
		boolean readRequired = op_stage_instr.containsKey(BNode.RdMem) && op_stage_instr.get(BNode.RdMem).containsKey(stage);
		boolean writeRequired = op_stage_instr.containsKey(BNode.WrMem) && op_stage_instr.get(BNode.WrMem).containsKey(stage);
		// If reads or writes to mem are required...
		if( readRequired || writeRequired)  {
			// Add in files logic for Memory reads
			// Generate text to select load store unit like :
			//		if(if((to_execute_instruction(6 downto 0) = "1011011" and to_execute_instruction(14 downto 12) = "010")) then -- ISAX load 
	        //			lsu_select <= '1';
	        //		end if;
			String textToAdd = "";
			if(readRequired) {
				textToAdd =  "if("+language.CreateValidEncoding(op_stage_instr.get(BNode.RdMem).get(stage), ISAXes, "to_execute_instruction", BNode.RdMem)+") then -- ISAX load \n" // CreateValidEncoding generates text to decode instructions in op_stage_instr.get(BNode.RdMem).get(stage)
								+ tab+"lsu_select <= '1';\n"
								+"end if;\n";
				toFile.UpdateContent(this.ModFile("load_store_unit"),Parse.declare,new ToWrite("signal read_s :std_logic; -- ISAX, signaling a read", false, true,""));
				toFile.UpdateContent(this.ModFile("load_store_unit"),Parse.behav,new ToWrite(language.CreateText1or0("read_s", language.CreateValidEncoding(op_stage_instr.get(BNode.RdMem).get(stage), ISAXes, "instruction", BNode.RdMem)), false, true, ""));
				toFile.ReplaceContent(this.ModFile("load_store_unit"),"process (opcode, func3) is", new ToWrite("process (opcode, func3, read_s) is", false,true,""));
				toFile.ReplaceContent(this.ModFile("load_store_unit"),"if opcode(5) = LOAD_OP(5) then", new ToWrite("if read_s = '1' then", false,true,""));
				toFile.ReplaceContent(this.ModFile("load_store_unit"),"oimm_readnotwrite <= '1' when opcode(5) = LOAD_OP(5)",new ToWrite("oimm_readnotwrite <= '1' when (read_s  = '1') else '0';",false,true,""));
			}
			if(writeRequired) { 
				textToAdd += "if("+language.CreateValidEncoding(op_stage_instr.get(BNode.WrMem).get(stage), ISAXes, "to_execute_instruction", BNode.WrMem)+") then -- ISAX store \n"
								+ tab+"lsu_select <= '1';\n"
								+"end if;\n";
				toFile.UpdateContent(this.ModFile("execute"),Parse.declare, new ToWrite("signal ISAX_rs2_data : std_logic_vector(31 downto 0) ;",false,true,""));
				toFile.UpdateContent(this.ModFile("execute"),Parse.behav,new ToWrite("ISAX_rs2_data <= "+language.CreateNodeName(BNode.WrMem, stage, "")+" when ("+language.CreateAllEncoding(this.op_stage_instr.get(BNode.WrMem).get(stage), ISAXes, "to_execute_instruction")+") else rs2_data;",false,true,"")); 
				toFile.ReplaceContent(this.ModFile("execute"),"rs2_data       => rs2_data,",new ToWrite(" rs2_data       => ISAX_rs2_data, -- ISAX",true,false,"ls_unit : load_store_unit"));
				
			}
			// Add text in file of module "execute" before line "end process;". Don't add it unless you already saw line "lsu_select <= '1';"  = requries prerequisite. 
			toFile.UpdateContent(this.ModFile("execute"),"end process;", new ToWrite(textToAdd,true,false,"lsu_select <= '1';",true)); //ToWrite (text to add, requries prereq?, start value for prereq =false if prereq required, prepreq text, text has to be added BEFORE grepped line?)		
		
			// Address logic 

			String addrClause = language.CreateAddrEncoding(op_stage_instr.get(BNode.RdMem).get(stage), ISAXes, "instruction", BNode.RdMem);
			if(!addrClause.isEmpty())
				addrClause += " or ";
			addrClause += language.CreateAddrEncoding(op_stage_instr.get(BNode.WrMem).get(stage), ISAXes, "instruction", BNode.WrMem);
			if(!addrClause.isEmpty()) {
				toFile.UpdateContent(this.ModFile("load_store_unit"),Parse.declare,new ToWrite("signal isax_addr_s : std_logic;", false, true, ""));
				toFile.UpdateContent(this.ModFile("load_store_unit"),Parse.behav,new ToWrite(language.CreateText1or0("isax_addr_s", addrClause), false, true, ""));
				toFile.ReplaceContent(this.ModFile("load_store_unit"),"address_unaligned <= std_logic_vector", new ToWrite("address_unaligned <= Mem_addr_3_i when (isax_addr_s = '1') else std_logic_vector(unsigned(sign_extension(REGISTER_SIZE-12-1 downto 0) & imm)+unsigned(base_address));-- Added ISAX support ", false, true, ""));
				toFile.ReplaceContent(this.ModFile("load_store_unit"),"imm)+unsigned(base_address));", new ToWrite(" ",false,true,""));
			}
		}
		
		if(this.op_stage_instr.containsKey(BNode.WrMem_spawn) ||this.op_stage_instr.containsKey(BNode.RdMem_spawn) ) {
			HashSet<String> allMemSpawn = new HashSet<String>();
			int spawnStage = this.orca_core.maxStage+1;
			allMemSpawn.addAll(this.op_stage_instr.get(BNode.WrMem_spawn).get(spawnStage));
			allMemSpawn.addAll(this.op_stage_instr.get(BNode.RdMem_spawn).get(spawnStage));
			
			if(allMemSpawn.size()>1)
					toFile.UpdateContent(this.ModFile("orca_core"),Parse.behav, new ToWrite(BNode.ISAX_sum_spawn_mem_s + " <= ("+language.allISAXNameText(" + ", "(unsigned'('0' & "+BNode.Mem_spawn_valid+"_", "_"+spawnStage+"_reg))", allMemSpawn)+");\n",false, true, ""));
			else		
				toFile.UpdateContent(this.ModFile("orca_core"),Parse.behav, new ToWrite(BNode.ISAX_sum_spawn_mem_s + " <= "+language.allISAXNameText(" + ", BNode.Mem_spawn_valid+"_", "_"+spawnStage+"_reg", allMemSpawn)+";\n",false, true, ""));
			toFile.UpdateContent(this.ModFile("orca_core"),Parse.behav, new ToWrite( BNode.ISAX_fire_mem_s + " <= "+language.allISAXNameText(" or ", BNode.Mem_spawn_valid+"_", "_"+spawnStage+"_i", allMemSpawn)+";\n",false, true, ""));
			
			toFile.ReplaceContent(this.ModFile("orca_core"),"lsu_oimm_address       => lsu_oimm_address,", new ToWrite("lsu_oimm_address       => isax_lsu_oimm_address,",false,true,""));
			toFile.ReplaceContent(this.ModFile("orca_core"),"lsu_oimm_byteenable    => lsu_oimm_byteenable,", new ToWrite("lsu_oimm_byteenable    => isax_lsu_oimm_byteenable,",false,true,""));
			toFile.ReplaceContent(this.ModFile("orca_core"),"lsu_oimm_requestvalid  => lsu_oimm_requestvalid,", new ToWrite("lsu_oimm_requestvalid  => isax_lsu_oimm_requestvalid,",false,true,""));
			toFile.ReplaceContent(this.ModFile("orca_core"),"lsu_oimm_readnotwrite  => lsu_oimm_readnotwrite,", new ToWrite("lsu_oimm_readnotwrite  => isax_lsu_oimm_readnotwrite,",false,true,""));
			toFile.ReplaceContent(this.ModFile("orca_core"),"lsu_oimm_writedata     => lsu_oimm_writedata,", new ToWrite("lsu_oimm_writedata     => isax_lsu_oimm_writedata,",false,true,""));
			String declare = "signal isax_spawn_lsu_oimm_address : std_logic_vector(31 downto 0);\n"
					+ "signal isax_lsu_oimm_address : std_logic_vector(31 downto 0);\n"
					+ "signal isax_lsu_oimm_byteenable : std_logic_vector(3 downto 0);\n"
					+ "signal isax_lsu_oimm_requestvalid : std_logic;\n"
					+ "signal isax_spawn_read_no_write : std_logic;\n"
					+ "signal isax_lsu_oimm_readnotwrite : std_logic;\n"
					+ "signal isax_spawn_lsu_oimm_writedata : std_logic_vector(31 downto 0);\n"
					+ "signal isax_lsu_oimm_writedata  : std_logic_vector(31 downto 0);\n"
					+ "signal isax_spawnRdReq : std_logic;\n"
					+ "signal isax_RdStarted : std_logic;";
			toFile.UpdateContent(this.ModFile("orca_core"),Parse.declare, new ToWrite( declare,false, true, ""));
			String checkOngoingTransf = "";
			if(this.op_stage_instr.containsKey(BNode.RdMem_spawn)) {
				checkOngoingTransf = "process(clk) begin \n"
		    		+ tab.repeat(1)+"if rising_edge(clk) then \n"
		    		+ tab.repeat(2)+"if(lsu_oimm_requestvalid = '1') then\n"
		    		+ tab.repeat(3)+"isax_RdStarted <= lsu_oimm_readnotwrite;\n"
		    		+ tab.repeat(2)+"elsif(lsu_oimm_readdatavalid = '1') then\n"
		    		+ tab.repeat(3)+"isax_RdStarted <='0';\n"
		    		+ tab.repeat(2)+"end if; \n"
		    		+ tab.repeat(2)+"if reset = '1' then \n"
		    		+ tab.repeat(3)+"isax_RdStarted <= '0'; \n"
		    		+ tab.repeat(2)+"end if; \n"
		    		+ tab.repeat(1)+"end if;\n"
		    		+ "end process;\n";
				checkOngoingTransf += "isax_spawnRdReq <= "+ language.allISAXNameText(" or ", BNode.Mem_spawn_valid+"_", "_"+spawnStage+"_reg", this.op_stage_instr.get(BNode.RdMem_spawn).get(spawnStage))+";\n";
			} else checkOngoingTransf = "isax_spawnRdReq <= 0;\n"
		    		+"isax_RdStarted <= 0;\n";
			String setSignals = "lsu_oimm_address      <= isax_spawn_lsu_oimm_address when ("+BNode.ISAX_fire2_mem_reg+" = '1')  else isax_lsu_oimm_address;\n"
					+ "lsu_oimm_byteenable    <= \"1111\" when ("+BNode.ISAX_fire2_mem_reg+" = '1') else isax_lsu_oimm_byteenable;\n"
					+ "lsu_oimm_requestvalid  <= (not lsu_oimm_waitrequest and not isax_RdStarted) when ("+BNode.ISAX_fire2_mem_reg+" = '1') else isax_lsu_oimm_requestvalid; -- todo possible comb path in slave between valid and wait\n"
					+ "lsu_oimm_readnotwrite  <= isax_spawn_read_no_write when ("+BNode.ISAX_fire2_mem_reg+" = '1') else isax_lsu_oimm_readnotwrite;\n"
					+ "lsu_oimm_writedata     <= isax_spawn_lsu_oimm_writedata when ("+BNode.ISAX_fire2_mem_reg+" = '1') else isax_lsu_oimm_writedata;\n";
			toFile.UpdateContent(this.ModFile("orca_core"),Parse.behav, new ToWrite( setSignals+checkOngoingTransf,false, true, ""));
			if(this.op_stage_instr.containsKey(BNode.WrMem_spawn) && this.op_stage_instr.containsKey(BNode.RdMem_spawn)) {
				toFile.UpdateContent(this.ModFile("orca_core"),Parse.behav, new ToWrite( language.CreateSpawnLogicMem(this.op_stage_instr.get(BNode.RdMem_spawn).get(spawnStage),this.op_stage_instr.get(BNode.WrMem_spawn).get(spawnStage),spawnStage,"isax_spawn_lsu_oimm_writedata","isax_spawn_read_no_write","isax_spawn_lsu_oimm_address"),false, true, ""));
				toFile.UpdateContent(this.ModFile("orca_core"),Parse.behav, new ToWrite( language.CreateSpawnCommitMem(this.op_stage_instr.get(BNode.RdMem_spawn).get(spawnStage),this.op_stage_instr.get(BNode.WrMem_spawn).get(spawnStage),spawnStage,"("+BNode.ISAX_fire2_mem_reg+" = '1') and (lsu_oimm_readdatavalid = '1')","(lsu_oimm_waitrequest = '0')"),false, true, ""));
			} else if(this.op_stage_instr.containsKey(BNode.WrMem_spawn)) {
				toFile.UpdateContent(this.ModFile("orca_core"),Parse.behav, new ToWrite( language.CreateSpawnLogicMem(new HashSet<String>(),this.op_stage_instr.get(BNode.WrMem_spawn).get(spawnStage),spawnStage,"isax_spawn_lsu_oimm_writedata","isax_spawn_read_no_write","isax_spawn_lsu_oimm_address"),false, true, ""));
				toFile.UpdateContent(this.ModFile("orca_core"),Parse.behav, new ToWrite( language.CreateSpawnCommitMem(new HashSet<String>(),this.op_stage_instr.get(BNode.WrMem_spawn).get(spawnStage),spawnStage,"("+BNode.ISAX_fire2_mem_reg+" = '1') and (lsu_oimm_readdatavalid = '1')","(lsu_oimm_waitrequest = '0')"),false, true, ""));
			} else {
				toFile.UpdateContent(this.ModFile("orca_core"),Parse.behav, new ToWrite( language.CreateSpawnLogicMem(this.op_stage_instr.get(BNode.RdMem_spawn).get(spawnStage),new HashSet<String>(),spawnStage,"isax_spawn_lsu_oimm_writedata","isax_spawn_read_no_write","isax_spawn_lsu_oimm_address"),false, true, ""));
				toFile.UpdateContent(this.ModFile("orca_core"),Parse.behav, new ToWrite( language.CreateSpawnCommitMem(this.op_stage_instr.get(BNode.RdMem_spawn).get(spawnStage),new HashSet<String>(),spawnStage,"("+BNode.ISAX_fire2_mem_reg+" = '1') and (lsu_oimm_readdatavalid = '1')","(lsu_oimm_waitrequest = '0')"),false, true, ""));
			}	
		}
	}

	private void IntegrateISAX_WrPC() {
		String assign_lineToBeInserted = "";
		String decl_lineToBeInserted = "";
		String sub_top_file = this.ModFile("orca_core");
		HashMap<Integer,String> array_PC_clause = new HashMap<Integer,String> ();
		int max_stage = this.orca_core.maxStage;
		int last_stage_isax_flush_added = 0;
		for(int stage=0; stage<=max_stage; stage++) {
			if(op_stage_instr.containsKey(BNode.WrPC) && op_stage_instr.get(BNode.WrPC).containsKey(stage)) {
				// Declare RdIValid , assign and generate clauses for instructions that must update PC (if an instruction for updating PC is valid)
				decl_lineToBeInserted +=  language.CreateDeclSig(BNode.WrPC_valid, stage,"" );
				if(stage<max_stage)
					array_PC_clause.put(stage, language.CreateValidEncoding(op_stage_instr.get(BNode.WrPC).get(stage), ISAXes, this.NodeAssign(BNode.RdInstr, stage), BNode.WrPC));		
				else {
					String PC_clause = "";
					for(String instr_name : op_stage_instr.get(BNode.WrPC).get(stage)) {
						SCAIEVInstr instr = ISAXes.get(instr_name);
						 String rdIValid = language.CreateNodeName(BNode.RdIValid, stage, instr_name);
						 if(!(instr.HasNode(BNode.RdIValid) && instr.GetNode(BNode.RdIValid).GetStartCycle()== (stage))) {						 
								 if(!(ISAXes.get(instr_name).HasNode(BNode.WrRD)))
									 {						
										 rdIValid = language.CreateLocalNodeName(BNode.RdIValid, stage-1, instr_name);
										 decl_lineToBeInserted += language.CreateDeclSig(BNode.RdIValid, stage-1, instr_name);
										  String instr_field_name  = this.NodeAssign(BNode.RdInstr, stage-1); // TODO this works only considering that in stage 3 there is an instr sig in the same file. Would not work if assig Instr signal would be in another subfile for exp
										 assign_lineToBeInserted += language.CreateText1or0(rdIValid, language.CreateAllEncoding(new HashSet<String>() {{add(instr_name);}}, ISAXes, instr_field_name));
										 toFile.UpdateContent(this.ModFile(NodeAssignM(BNode.RdIValid, stage)),Parse.behav,	  new ToWrite(language.CreateTextRegReset(language.CreateLocalNodeName(BNode.RdIValid, stage, instr_name),rdIValid),false,true,""));
											
									 }
								 decl_lineToBeInserted += language.CreateDeclSig(BNode.RdIValid, stage, instr_name);								
								 rdIValid = language.CreateLocalNodeName(BNode.RdIValid, stage, instr_name);
						 }
						 if(instr.GetNode(BNode.WrPC).GetValidInterf()) {
							 if (PC_clause!="") {
								 PC_clause += " or ";
							 }
							 PC_clause += "("+rdIValid+"='1' and "+language.CreateNodeName(BNode.WrPC_valid, stage,"")+" = '1')";
						 } else {
							 if (PC_clause!="") {
								 PC_clause += " or ";
							 }
							 PC_clause += "("+rdIValid+"='1'";
						 }							 					 
					}
					array_PC_clause.put(stage, PC_clause);		
				}
				last_stage_isax_flush_added = stage;
			}	
		}
		if(!array_PC_clause.isEmpty() || op_stage_instr.containsKey(BNode.WrPC_spawn)) {
			decl_lineToBeInserted += "signal ISAX_to_pc_correction_data_s : unsigned(REGISTER_SIZE-1 downto 0);\n";
			decl_lineToBeInserted += "signal ISAX_to_pc_correction_valid_s : std_logic;\n";
			
			String PC_text = ""; 
			String PC_clause = ""; 
			for(int stage =orca_core.GetNodes().get(BNode.WrPC).GetLatest()+1; stage >= 0; stage--) {
				
				if(array_PC_clause.containsKey(stage) || (stage == (orca_core.GetNodes().get(BNode.WrPC).GetLatest()+1))) {
					if(op_stage_instr.get(BNode.WrPC).containsKey(max_stage+1) && stage==0) {
						PC_text += "if("+language.CreateNodeName(BNode.WrPC_spawn_valid, max_stage+1,"")+" = '1') then\n"+tab.repeat(2)+"ISAX_to_pc_correction_data_s <= "+language.CreateNodeName(BNode.WrPC_spawn, max_stage+1,"")+";\n";
						PC_clause += "( "+language.CreateNodeName(BNode.WrPC_spawn_valid, max_stage+1,"")+" = '1')";
					}
					if(array_PC_clause.containsKey(stage)) {
						if(!PC_text.equals(""))
							PC_text += "elsif ";
						else 
							PC_text += "if ";
						PC_text +="("+array_PC_clause.get(stage)+") then\n"+tab.repeat(2)+"ISAX_to_pc_correction_data_s <= WrPC_"+stage+"_i;\n";  
						if(PC_clause!="")
							PC_clause += " or ";
						PC_clause += "( "+ array_PC_clause.get(stage)+")";
					}
					if((stage == orca_core.GetNodes().get(BNode.WrPC).GetLatest())) 
						PC_text += "else\n"+tab.repeat(2)+"ISAX_to_pc_correction_data_s <= to_pc_correction_data;\n";
				}			
			}
			PC_text +=tab+"end if;\n";
			assign_lineToBeInserted += language.CreateInProc(false, PC_text);
			assign_lineToBeInserted +=  language.CreateTextISAXorOrig(PC_clause, "ISAX_to_pc_correction_valid_s","'1'","to_pc_correction_valid");
			toFile.ReplaceContent(sub_top_file, "to_pc_correction_data        => to_pc_correction_data,", new ToWrite("to_pc_correction_data        => ISAX_to_pc_correction_data_s,",true, false, "I : instruction_fetch"));
			toFile.ReplaceContent(sub_top_file, "to_pc_correction_valid       =>", new ToWrite("to_pc_correction_valid        => ISAX_to_pc_correction_valid_s,",true, false, "I : instruction_fetch"));
			if(this.ContainsOpInStage(BNode.WrFlush, 2))
				toFile.ReplaceContent(sub_top_file, "quash_decode =>", new ToWrite("quash_decode        => ISAX_to_pc_correction_valid_s or "+language.CreateNodeName(BNode.WrFlush, 2,"")+",",true, false, "D : decode"));
			else 
				toFile.ReplaceContent(sub_top_file, "quash_decode =>", new ToWrite("quash_decode        => ISAX_to_pc_correction_valid_s,",true, false, "D : decode"));
			
			
			for(int stage = 0; stage<this.orca_core.maxStage;stage++ ) {
				decl_lineToBeInserted += "signal ISAX_to_pc_correction_valid_"+stage+"_s : std_logic;\n";
				assign_lineToBeInserted += "ISAX_to_pc_correction_valid_"+stage+"_s <= ";
			    
				if(stage <= last_stage_isax_flush_added) {
					assign_lineToBeInserted += "'1' when ";
					if(stage==3)
						toFile.ReplaceContent(sub_top_file, "to_execute_valid <= from_decode_valid and (not to_pc_correction_valid);",new ToWrite("to_execute_valid <= from_decode_valid and (not to_pc_correction_valid) and (not ISAX_to_pc_correction_valid_"+3+"_s);",false, true, "") );
					else if(stage==1)
						toFile.ReplaceContent(sub_top_file, "to_decode_valid <= from_ifetch_valid and (not to_pc_correction_valid);",new ToWrite("to_decode_valid <= from_ifetch_valid and (not to_pc_correction_valid) and (not ISAX_to_pc_correction_valid_"+1+"_s);",false, true, "") );
					// TODO stage 2
					 String or = "";		    
					for(int array_PC_i=stage;array_PC_i<=max_stage;array_PC_i++)
						if(array_PC_clause.containsKey(array_PC_i)) {
							assign_lineToBeInserted += or + " ("+array_PC_clause.get(array_PC_i)+") ";
							or = "or";
						}
					assign_lineToBeInserted +="else '0';\n";
				} 
					else assign_lineToBeInserted += " '0';\n";			
			}
				
		}
		//insert.put("port (", new ToWrite(interf_lineToBeInserted,false,true,""));
		toFile.UpdateContent(sub_top_file,"architecture rtl of orca_core is", new ToWrite(decl_lineToBeInserted,false,true,""));
		toFile.UpdateContent(sub_top_file,"begin", new ToWrite(assign_lineToBeInserted,false,true,""));
		
		
	}
	private boolean ContainsOpInStage(String operation, int stage) {
		return op_stage_instr.containsKey(operation) && op_stage_instr.get(operation).containsKey(stage);
	}
	private void ConfigOrca() {
	 	this.PopulateNodesMap(this.orca_core.maxStage);
	 	
		PutModule(pathORCA+"\\components.vhd","instruction_fetch"	, pathORCA+"\\instruction_fetch.vhd", "orca_core","instruction_fetch");
		PutModule(pathORCA+"\\components.vhd","decode"				, pathORCA+"\\decode.vhd",			   "orca_core","decode");
		PutModule(pathORCA+"\\components.vhd","execute"				, pathORCA+"\\execute.vhd", 		   "orca_core","execute");
		PutModule(pathORCA+"\\components.vhd","arithmetic_unit"		, pathORCA+"\\alu.vhd",			   "execute",  "arithmetic_unit ");
		PutModule(pathORCA+"\\components.vhd","branch_unit"			, pathORCA+"\\branch_unit.vhd",	   "execute",  "branch_unit");
		PutModule(pathORCA+"\\components.vhd","load_store_unit"		, pathORCA+"\\load_store_unit.vhd",   "execute",  "load_store_unit");
		PutModule(pathORCA+"\\components.vhd","orca_core"			, pathORCA+"\\orca_core.vhd",		   "orca",     "orca_core");
		PutModule(pathORCA+"\\components.vhd","orca"				, pathORCA+"\\orca.vhd", 			   "",		   "orca");
	 	Module newModule = new Module();
	 	newModule.name = "extension_name";

	 	
	 	int spawnStage = this.orca_core.maxStage+1;
	 	for(int i =0; i<spawnStage;i++) {
	 		this.PutNode(32, true, "unsigned", "", "orca_core", BNode.WrPC,i); // was to_pc_correction_data
	 		this.PutNode(1, true, "std_logic", "", "orca_core", BNode.WrPC_valid,i); // was to_pc_correction_valid
	 	}
	 	this.PutNode(32, true, "unsigned", "", "orca_core", BNode.WrPC_spawn,5);
 		this.PutNode(1, true, "std_logic", "", "orca_core", BNode.WrPC_spawn_valid,5);
	 	this.PutNode(32, false, "unsigned", "program_counter", "orca_core", BNode.RdPC,0);
	 	this.PutNode(32, false, "unsigned", "ifetch_to_decode_program_counter", "orca_core", BNode.RdPC,1);
	 	this.PutNode(32, false, "unsigned", "from_stage1_program_counter", "decode", BNode.RdPC,2);
	 	this.PutNode(32, false, "unsigned", "decode_to_execute_program_counter", "orca_core", BNode.RdPC,3);
 		
	 	this.PutNode(32, false, "std_logic_vector", "ifetch_to_decode_instruction", "orca_core", BNode.RdInstr,1);
	 	this.PutNode(32, false, "std_logic_vector", "from_stage1_instruction", "decode", BNode.RdInstr,2);
	 	this.PutNode(32, false, "std_logic_vector", "decode_to_execute_instruction", "orca_core", BNode.RdInstr,3);
	 	this.PutNode(32, false, "std_logic_vector", "", "orca_core", BNode.RdInstr,4);
 		
	 	this.PutNode(32, false, "std_logic_vector", "rs1_data", "decode", BNode.RdRS1,2);
	 	this.PutNode(32, false, "std_logic_vector", "rs1_data", "execute", BNode.RdRS1,3); 		 		

	 	this.PutNode(32, false, "std_logic_vector", "rs2_data", "decode", BNode.RdRS2,2);
	 	this.PutNode(32, false, "std_logic_vector", "rs2_data", "execute", BNode.RdRS2,3);	
	 	
	 	this.PutNode(32, true, "std_logic_vector", "", "execute", BNode.WrRD,4); 
	 	this.PutNode(32, true, "std_logic_vector", "", "orca_core", BNode.WrRD_addr,4);
	 	this.PutNode(32, true, "std_logic_vector", "", "orca_core", BNode.WrRD_valid,4);
	 	
	 	
	 	this.PutNode(1,  false, "std_logic", "", "orca_core", BNode.RdIValid,1);	 		
	 	this.PutNode(1,  false, "std_logic", "", "decode", BNode.RdIValid,2);	 		
	 	this.PutNode(1,  false, "std_logic", "", "orca_core", BNode.RdIValid,3);	 		
	 	this.PutNode(1,  false, "std_logic", "", "orca_core", BNode.RdIValid,4);
	 	
		int stageMem = this.orca_core.GetNodes().get(BNode.RdMem).GetLatest();
	 	this.PutNode(32, false, "std_logic_vector", "from_lsu_data", "load_store_unit", BNode.RdMem,stageMem);
	 	this.PutNode(1,  false, "std_logic", "from_lsu_valid", "load_store_unit", BNode.RdMem_valid,stageMem);
	 	this.PutNode(32, true,  "std_logic_vector", "", "load_store_unit", BNode.WrMem,stageMem);
	 	this.PutNode(1,  true,  "std_logic", "", "load_store_unit", BNode.Mem_valid,stageMem);
	 	this.PutNode(32, true,  "std_logic_vector", "", "load_store_unit", BNode.Mem_addr,stageMem);
	 
	 	this.PutNode(1,  false, "std_logic", "not (pc_fifo_write)", "instruction_fetch", BNode.RdStall,0);
	 	this.PutNode(1,  false, "std_logic", "not (decode_to_ifetch_ready)", "orca_core", BNode.RdStall,1);
	 	this.PutNode(1,  false, "std_logic", "not (to_stage1_ready)", "decode", BNode.RdStall,2);
	 	this.PutNode(1,  false, "std_logic", "not (execute_to_decode_ready)", "orca_core", BNode.RdStall,3);	 	
	 	this.PutNode(1,  false, "std_logic", "not (from_writeback_ready)", "execute", BNode.RdStall,4);
	 	
	 	this.PutNode(1, true, "std_logic", "", "instruction_fetch", BNode.WrStall,0);
	 	this.PutNode(1, true, "std_logic", "", "orca_core", BNode.WrStall,1);
	 	this.PutNode(1, true, "std_logic", "", "orca_core", BNode.WrStall,2);
	 	this.PutNode(1, true, "std_logic", "", "execute", BNode.WrStall,3);
	 	this.PutNode(1, true, "std_logic", "", "orca_core", BNode.WrStall,4);
	 	
	 	this.PutNode(1, false, "std_logic", "to_pc_correction_valid or ISAX_to_pc_correction_valid_0_s or " +language.CreateLocalNodeName(BNode.WrFlush, 0, ""), "orca_core", BNode.RdFlush,0);
	 	this.PutNode(1, false, "std_logic", "not (to_decode_valid) or ISAX_to_pc_correction_valid_1_s or (not to_decode_valid) or " +language.CreateLocalNodeName(BNode.WrFlush, 1, ""), "orca_core", BNode.RdFlush,1);
	 	this.PutNode(1, false, "std_logic", "not (from_stage1_valid) or ISAX_to_pc_correction_valid_2_s or "+language.CreateLocalNodeName(BNode.WrFlush, 2, ""), "orca_core", BNode.RdFlush,2);// TODO to_decode_valid
	 	this.PutNode(1, false, "std_logic", "to_pc_correction_valid or ISAX_to_pc_correction_valid_3_s or (not to_execute_valid) or "+language.CreateLocalNodeName(BNode.WrFlush,3, ""), "orca_core", BNode.RdFlush,3);
	 	this.PutNode(1, false, "std_logic", "to_pc_correction_valid or ISAX_to_pc_correction_valid_4_s or "+language.CreateLocalNodeName(BNode.WrFlush, 4, ""), "orca_core", BNode.RdFlush,4);// TODO 
	 	
	 	this.PutNode(1, true, "std_logic", language.CreateLocalNodeName(BNode.WrFlush, 0, ""), "orca_core", BNode.WrFlush,0);
	 	this.PutNode(1, true, "std_logic", language.CreateLocalNodeName(BNode.WrFlush, 1, ""), "orca_core", BNode.WrFlush,1);
	 	this.PutNode(1, true, "std_logic", language.CreateLocalNodeName(BNode.WrFlush, 2, ""), "orca_core", BNode.WrFlush,2);
	 	this.PutNode(1, true, "std_logic", language.CreateLocalNodeName(BNode.WrFlush, 3, ""), "orca_core", BNode.WrFlush,3);
	 	this.PutNode(1, true, "std_logic", language.CreateLocalNodeName(BNode.WrFlush, 4, ""), "orca_core", BNode.WrFlush,4);
	 	

	 	this.PutNode(32, true, "std_logic_vector", "", "orca_core", BNode.WrRD_spawn,spawnStage);
	 	this.PutNode(1,  true, "std_logic", "", "orca_core", BNode.WrRD_spawn_valid,spawnStage);
	 	this.PutNode(5,  true, "std_logic_vector", "", "orca_core", BNode.WrRD_spawn_addr,spawnStage);

	 	this.PutNode(32, false, "std_logic_vector", "lsu_oimm_readdata", "orca_core", BNode.RdMem_spawn,spawnStage);
	 	this.PutNode(1, false, "std_logic","","orca_core", BNode.RdMem_spawn_valid,spawnStage);
	 	this.PutNode(32, true,  "std_logic_vector", "", "orca_core", BNode.WrMem_spawn,spawnStage);
	 	this.PutNode(1, false, "std_logic","","orca_core",BNode.WrMem_spawn_valid,spawnStage);
	 	this.PutNode(1,  true, "std_logic", "", "orca_core", BNode.Mem_spawn_valid,spawnStage);
	 	this.PutNode(32, true,  "std_logic_vector", "","orca_core", BNode.Mem_spawn_addr,spawnStage);
	 	
	 	this.PutNode(1, false,  "std_logic",BNode.ISAX_fire2_regF_reg,"orca_core", BNode.commited_rd_spawn_valid,spawnStage);
	 	this.PutNode(5, false,  "std_logic_vector","ISAX_execute_to_rf_select_s", "orca_core",BNode.commited_rd_spawn,spawnStage);
	 	
	 	this.PutNode(1, true,  "std_logic","","execute","ISAX_FWD_ALU",4);
	 	this.PutNode(1, true,  "std_logic","","execute","ISAX_REG",4);
     }

	
}

