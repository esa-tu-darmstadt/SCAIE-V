package scaiev.util;

import java.util.HashMap;
import java.util.HashSet;

import scaiev.backend.BNode;
import scaiev.backend.CoreBackend;
import scaiev.frontend.SCAIEVInstr;

public class VHDL extends GenerateText {
	FileWriter toFile = new FileWriter();
	public String tab = "    ";
	
	public VHDL(FileWriter toFile, CoreBackend core) {
		// initialize dictionary 
		dictionary.put(DictWords.module,"architecture");
		dictionary.put(DictWords.endmodule,"end architecture");
		dictionary.put(DictWords.reg,"signal");
		dictionary.put(DictWords.wire,"signal");		
		dictionary.put(DictWords.assign,"");
		dictionary.put(DictWords.assign_eq,"<=");
		dictionary.put(DictWords.logical_or,"or");
		dictionary.put(DictWords.bitwise_or,"or");
		dictionary.put(DictWords.logical_and,"and");
		dictionary.put(DictWords.bitwise_and,"and");
		dictionary.put(DictWords.bitsselectRight,")");
		dictionary.put(DictWords.bitsselectLeft,"(");
		dictionary.put(DictWords.ifeq,"=");
		dictionary.put(DictWords.bitsRange,"downto");
		dictionary.put(DictWords.in,"in");
		dictionary.put(DictWords.out,"out");
		this.toFile = toFile;
		tab = toFile.tab;
		this.coreBackend = core;
	}
	
	@Override 
	public Lang getLang () {
		return Lang.VHDL;		
	}
	public void  UpdateInterface(String top_module,String operation, String instr, int stage, boolean top_interface, boolean special_case) {
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
				System.out.println("INTEGRATE. DEBUG. Inserting in components.vhd with prereq "+ current_module);
				this.toFile.UpdateContent(coreBackend.ModInterfFile(current_module),"port (", new ToWrite(CreateTextInterface(operation,stage,instr,special_case),true,false," "+current_module+" "));
				this.toFile.UpdateContent(coreBackend.ModFile(current_module),"port (", new ToWrite(CreateTextInterface(operation,stage,instr,special_case),true,false," "+current_module+" "));		
			} else if(current_module.contentEquals(top_module)) {
				this.toFile.UpdateContent(coreBackend.ModFile(current_module),"architecture rtl",new ToWrite(CreateDeclSig(operation, stage, instr),true,false," "+current_module+" "));
			}
			
			if(prev_module.contentEquals("")) {
				// Connect signals to top interface
				if(!coreBackend.NodeAssign(operation, stage).contentEquals("")) {
					assign_lineToBeInserted += sig_name+" <= "+coreBackend.NodeAssign(operation, stage)+" ;\n";
					this.toFile.UpdateContent(coreBackend.ModFile(current_module),"begin", new ToWrite(assign_lineToBeInserted,false,true,""));	
				}
				/*
				else {
					assign_lineToBeInserted += this.CreateDeclSig(operation, stage, instr)+" \n";
					this.toFile.UpdateContent(coreBackend.ModFile(current_module),"architecture rtl", new ToWrite(assign_lineToBeInserted,false,true,"",false));	
				}*/
			} else {
				String instance_sig = sig_name+" => "+sig_name+",\n";
				if(current_module.contentEquals(top_module) && !top_interface)
					instance_sig = sig_name+" => "+this.CreateLocalNodeName(operation, stage, instr)+",\n";
				this.toFile.UpdateContent(coreBackend.ModFile(current_module),"port map (", new ToWrite(instance_sig,true,false,": "+prev_module));
			}
			prev_module = current_module;
			if(!current_module.contentEquals(top_module) && !coreBackend.ModParentName(current_module).equals(""))
				current_module = coreBackend.ModParentName(current_module);	
			else 
				break;				
		}
		
	}

	
	/**
	 * Generates text like : signal signalName_s  :  std_logic_vector(1 downto 0);
	 * signalName created from <operation,  stage,  instr>
	 */
	public String CreateDeclSig(String operation, int stage, String instr) {
		String decl = "";
		String size = "";
		if(coreBackend.NodeSize(operation,stage) != 1 ) 
			size += "("+coreBackend.NodeSize(operation, stage)+" -1 downto 0)";
		
		decl = "signal "+CreateLocalNodeName(operation,stage,instr)+"  :  " + coreBackend.NodeDataT(operation, stage) +size+";\n";
		return decl;	
	}
	
	/**
	 * Generates text like : signal signalName_reg  :  std_logic_vector(1 downto 0);
	 * signalName created from <operation,  stage,  instr>
	 */
	public String CreateDeclReg(String operation, int stage, String instr) {
		String decl = "";
		String size = "";
		if(coreBackend.NodeSize(operation,stage) > 1  ) 
			size += "("+coreBackend.NodeSize(operation, stage)+" -1 downto 0)";
		String regName = CreateNodeName(operation,stage,instr);
		if(coreBackend.NodeIn(operation, stage))
			regName = regName.replace("_i", "_reg");
		else 
			regName = regName.replace("_o", "_reg");
		decl = "signal "+regName+"  :  " + coreBackend.NodeDataT(operation, stage) +size+";\n";
		return decl;	
	}
	
	/**
	 * Declares all Regs required for spawn. Returns string with text of declarations. Not for wrpc spawn, as wrpc spawn is allowed to have only 1 interf
	 * @param ISAXes
	 * @param stage
	 * @return
	 * 
	 */

	public String CreateSpawnDeclareSigs(HashSet<String> ISAXes, int stage, String node, boolean withFire, int sizeOtherNode) {
		String declare = "-- ISAX : Declarations of Signals required for spawn logic for "+node+"\n";
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
			declare += "signal "+ISAX_fire_r+" : std_logic;\n";
			declare += "signal "+ISAX_fire_s+" : std_logic;\n";
			declare += "signal "+ISAX_fire2_r+" : std_logic;\n";
			declare += "signal "+ISAX_spawnStall_s+" : std_logic;\n";
			
			String declareSumSpawn = "signal "+ISAX_sum_spawn_s+": ";
			if((ISAXes.size()+sizeOtherNode)==1)
				declareSumSpawn += "std_logic;";
			else 
				declareSumSpawn += "unsigned("+((int)  (Math.ceil( Math.log(ISAXes.size()+sizeOtherNode+1) / Math.log(2) ) ) ) +"-1 downto 0);\n";
			declare += declareSumSpawn;
		}
		return declare;		
	}
	
	public String CreateSpawnRegsLogic(String instr, int stage, String priority, String node, String exprSpawnReady) {
		String body = "";	
		String ISAX_fire2_r = BNode.ISAX_fire2_regF_reg;
		String valid_node = node+"_valid";
		String addr_node = node+"_addr";
		if(node.contains(BNode.RdMem_spawn.split("d")[1])) {
			ISAX_fire2_r = BNode.ISAX_fire2_mem_reg;
			valid_node = BNode.Mem_spawn_valid ;
			addr_node = BNode.Mem_spawn_addr;
		}
		if(!exprSpawnReady.isEmpty())
			exprSpawnReady = " and ("+exprSpawnReady+")";
		if(!priority.isEmpty())
			priority = " "+this.dictionary.get(DictWords.logical_and)+" !("+priority+")"; 
		body += "if("+CreateNodeName(valid_node, stage,instr)+" = '1') then \n"
				+ tab+CreateNodeName(valid_node, stage,instr).replace("_i", "_reg")+" <= '1'; \n";
		if(!node.contentEquals(BNode.RdMem_spawn))
				body +=  tab+CreateNodeName(node, stage,instr).replace("_i", "_reg")+" <= "+CreateNodeName(node, stage,instr)+"; \n";
		body +=  tab+CreateNodeName(addr_node, stage,instr).replace("_i", "_reg")+" <= "+CreateNodeName(addr_node, stage,instr)+"; \n"
				+ "elsif(("+ISAX_fire2_r+" = '1') "+priority+" "+exprSpawnReady+") then\n"
				+ tab+CreateNodeName(valid_node, stage,instr).replace("_i", "_reg")+" <= '0'; \n"
				+ "end if;\n"
				+ "if "+reset+" = '1' then\n"
		        + tab +CreateNodeName(valid_node, stage,instr).replace("_i", "_reg") + "<= '0'; \n"
		        +"end if;\n";
				
		return body;		
	}
	
	public String CommitSpawnFire (String node, String stallStage, String stageReady, int spawnStage, boolean moreThanOne, String exprSpawnReady) { // stageReady =  execute_to_decode_ready for ORCA
		String ISAX_fire_r = BNode.ISAX_fire_regF_reg;
		String ISAX_fire_s = BNode.ISAX_fire_regF_s; 
		String ISAX_fire2_r = BNode.ISAX_fire2_regF_reg; 
		String ISAX_sum_spawn_s = BNode.ISAX_sum_spawn_regF_s; 
		String ISAX_spawnStall_s = BNode.ISAX_spawnStall_regF_s;
		if(node.contains(BNode.RdMem_spawn.split("d")[1])) {
			ISAX_fire_r = BNode.ISAX_fire_mem_reg; 
			ISAX_fire_s = BNode.ISAX_fire_mem_s;
			ISAX_fire2_r = BNode.ISAX_fire2_mem_reg;
			ISAX_sum_spawn_s = BNode.ISAX_sum_spawn_mem_s;
			ISAX_spawnStall_s = BNode.ISAX_spawnStall_mem_s;
		}
		
		// Create stall logic 
		String stall3Text = "";
		String stallFullLogic = "";
		String stageReadyText = "";
		if (!stallStage.isEmpty() && !stageReady.isEmpty())
			stall3Text += " or ";
		if (!stallStage.isEmpty())
			stall3Text += stallStage+" = '1'";
		if (!stageReady.isEmpty())
			stageReadyText = stageReady+" = '1'";
		if(!stallStage.isEmpty() || !stageReady.isEmpty())
			stallFullLogic = "and ("+stageReadyText+" "+stall3Text+")";
		if(!exprSpawnReady.isEmpty())
			exprSpawnReady = " and ("+exprSpawnReady+")";
		String sumRes = "'1'";
		if(moreThanOne)
			sumRes = "D\"1\"";
		String default_logic = " -- ISAX : Spawn fire logic\n"
				+ "process ("+clk+")  \n"
				+ "  begin \n"
				+ "    if rising_edge("+clk+") then \n"
				+ "        if(("+ISAX_fire_r+"  = '1') "+stallFullLogic+")  then \n"
				+ "          "+ISAX_fire_r+" <=  '0'; \n"
				+ "        elsif("+ISAX_fire_s+"  = '1' ) then \n"
				+ "          "+ISAX_fire_r+" <=  '1'; \n"
				+ "        end if; \n"
				+ "        if "+reset+" = '1' then \n"
				+ "            "+ISAX_fire_r+" <= '0'; \n"
				+ "        end if; \n"
				+ "     end if; \n"
				+ "  end process; \n"
				+ "   \n"
				+ "  process ("+clk+")  \n"
				+ "  begin \n"
				+ "    if rising_edge("+clk+") then \n"
				+ "        if(("+ISAX_fire_r+"  = '1' ) "+stallFullLogic+")  then  \n"
				+ "          "+ISAX_fire2_r+" <=  '1'; \n"
				+ "        elsif("+ISAX_sum_spawn_s+" = "+sumRes+") "+exprSpawnReady+"then   \n"
				+ "          "+ISAX_fire2_r+" <=  '0'; \n"
				+ "        end if; \n"
				+ "        if "+reset+" = '1' then \n"
				+ "          "+ISAX_fire2_r+" <= '0'; \n"
				+ "        end if; \n"
				+ "    end if; \n"
				+ "  end process; \n"
				+ "\n "
			
				+ ISAX_spawnStall_s+ " <= '1' when ("+ISAX_fire_s+" = '1' or "+ISAX_fire_r+" = '1') "+stallFullLogic+" else '0';\n"
				+ "  ";
		
				
		return default_logic;
	}
	
	public String CreateSpawnCommitWrRD(String instr, int stage, String priority) {
		String body = "";
		/*
		if(!priority.isEmpty())
			priority = " and ! ("+priority+")"; 
		body += "if(("+CreateNodeName(BNode.WrRD_spawn_valid, stage,instr).replace("_i", "_reg")+")"+priority+")\n"
				+ CreateLocalNodeName(BNode.commited_rd_spawn, stage,"")+" <= "+CreateNodeName(BNode.WrRD_spawn_addr, stage,instr).replace("_i", "_reg")+";\n";
				*/
		return body;		
	}
	
	public String CreateSpawnCommitMem(HashSet<String> RdISAXes,HashSet<String> WrISAXes, int stage, String readyRd, String readyWr ) {
		String body = "";
		String valid_node = BNode.Mem_spawn_valid;
		String ISAX_fire2_r = BNode.ISAX_fire2_mem_reg; 
		String priority_ISAXes = "";
		if(!WrISAXes.isEmpty())
			for (String instr : WrISAXes) {
				String priority = "";
				if(!priority_ISAXes.isEmpty())
					priority = " and not("+priority_ISAXes+")"; 
				if(!readyWr.isEmpty())
					readyWr = " and ("+readyWr+")"; 
				body += "process(all) begin \n";
				body += CreateNodeName(BNode.WrMem_spawn_valid, stage,instr)+" <= '0';\n";
				body +=   tab.repeat(1)+"if(("+this.CreateNodeName(valid_node, stage, instr).replace("_i", "_reg")+"  = '1' ) "+priority+" "+readyWr+"   ) then \n";   //(lsu_oimm_waitrequest = '0')
				body +=  tab.repeat(2)+CreateNodeName(BNode.WrMem_spawn_valid, stage,instr)+" <= '1';\n";
				body += tab.repeat(1)+"end if; \n"
						+ "end process; ";
				if(!priority_ISAXes.isEmpty())
					priority_ISAXes += " or ";
				priority_ISAXes   += "("+CreateNodeName(BNode.Mem_spawn_valid, stage,instr).replace("_i", "_reg")+ " = '1')";
			}
		if(!RdISAXes.isEmpty())
			for (String instr : RdISAXes) {
				String priority = "";
				if(!priority_ISAXes.isEmpty())
					priority = " and not("+priority_ISAXes+")"; 
				if(!readyRd.isEmpty())
					readyRd = " and ("+readyRd+")"; 
				body += "process(all) begin \n";
				body += CreateNodeName(BNode.RdMem_spawn_valid, stage,instr)+" <= '0';\n";
				body +=   tab.repeat(1)+"if(("+this.CreateNodeName(valid_node, stage, instr).replace("_i", "_reg")+"  = '1' )  "+priority+" "+readyRd+"   ) then \n";   //(lsu_oimm_waitrequest = '0')
				body +=  tab.repeat(2)+CreateNodeName(BNode.RdMem_spawn_valid, stage,instr)+" <= '1';\n";
				body += tab.repeat(1)+"end if; \n"
						+ "end process; ";
				if(!priority_ISAXes.isEmpty())
					priority_ISAXes += " or ";
				priority_ISAXes   += "("+CreateNodeName(BNode.Mem_spawn_valid, stage,instr).replace("_i", "_reg")+ " = '1')";
			}

		return body;
		
	}
	
	
	public String CreateSpawnLogicWrRD(HashSet<String> ISAXes, int stage, String ISAX_execute_to_rf_data_s) {
		String body = "";
		String condition = "if";
		for(String ISAX : ISAXes) {
			body += condition + "("+BNode.ISAX_fire2_regF_reg+" = '1' and "+CreateNodeName(BNode.WrRD_spawn_valid, stage,ISAX).replace("_i", "_reg")+" = '1' )then\n"+tab+ISAX_execute_to_rf_data_s+" <= "+CreateNodeName(BNode.WrRD_spawn, stage,ISAX).replace("_i", "_reg")+";\n";
			condition = "elsif";
		}
		return body;		
	}
	

	
	public String CreateSpawnLogicMem(HashSet<String> RdISAXes,HashSet<String> WrISAXes, int stage,  String wdata, String selectRead, String addr) { //priority = first write then read
		String body = "process(all) begin \n"
				+ tab+wdata+" <= X\"00000000\";\n"
				+ tab+selectRead+" <= '0';\n"
		        + tab+addr+" <= X\"00000000\";\n";
		String priority_ISAXes = "";
		if(!WrISAXes.isEmpty())
			for (String instr : WrISAXes) {
				String priority = "";
				if(!priority_ISAXes.isEmpty())
					priority = " and not("+priority_ISAXes+")"; 
			
				body	+= tab.repeat(1)+"if(  ("+CreateNodeName(BNode.Mem_spawn_valid, stage,instr).replace("_i", "_reg")+"  = '1' ) "+priority+" ) then\n"
					+ tab.repeat(2)+selectRead+" <= '0';\n"
					+ tab.repeat(2)+wdata+" <= "+CreateNodeName(BNode.WrMem_spawn, stage,instr).replace("_i", "_reg")+";\n"
					+ tab.repeat(2)+addr+" <= "+CreateNodeName(BNode.Mem_spawn_addr, stage,instr).replace("_i", "_reg")+"; \n"
					+ tab.repeat(1)+" end if; \n";
				if(!priority_ISAXes.isEmpty())
					priority_ISAXes += " or ";
				priority_ISAXes   += "("+ CreateNodeName(BNode.Mem_spawn_valid, stage,instr).replace("_i", "_reg")+ " = '1')";
			}
		if(!RdISAXes.isEmpty())
			for (String instr : RdISAXes) {
				String priority = "";
				if(!priority_ISAXes.isEmpty())
					priority = " and not("+priority_ISAXes+")"; 
			
				body	+= tab.repeat(1)+"if(  ("+CreateNodeName(BNode.Mem_spawn_valid, stage,instr).replace("_i", "_reg")+"  = '1' ) "+priority+" ) then\n"
					+ tab.repeat(2)+addr+" <= "+CreateNodeName(BNode.Mem_spawn_addr, stage,instr).replace("_i", "_reg")+"; \n"
					+ tab.repeat(2)+selectRead+" <= '1';\n"
					+ tab.repeat(1)+" end if; \n";
				if(!priority_ISAXes.isEmpty())
					priority_ISAXes += " or ";
				priority_ISAXes   += "("+CreateNodeName(BNode.Mem_spawn_valid, stage,instr).replace("_i", "_reg")+ " = '1')";
			}
		
		body += "end  process;\n";
		return body;		
	}
	
	public String CreateSpawnLogicWrRDAddr(HashSet<String> ISAXes, int stage, String ISAX_execute_to_rf_select_s) {
		String body = "";
		String condition = "if";
		for(String ISAX : ISAXes) {
			body += condition + "("+BNode.ISAX_fire2_regF_reg+" = '1' and "+CreateNodeName(BNode.WrRD_spawn_valid, stage,ISAX).replace("_i", "_reg")+" = '1' )then\n"+tab+ISAX_execute_to_rf_select_s+" <= "+CreateNodeName(BNode.WrRD_spawn_addr, stage,ISAX).replace("_i", "_reg")+";\n";
			condition = "elsif";
		}
		return body;		
	}
	
	public String CreateTextInterface(String operation, int stage, String instr, boolean special_case) {
		String interf_lineToBeInserted = "";
		String sig_name = this.CreateNodeName(operation, stage, instr);
		String sig_in = this.dictionary.get(DictWords.out);
		String sig_type = coreBackend.NodeDataT(operation, stage);   
		if(coreBackend.NodeIn(operation, stage))
			sig_in = this.dictionary.get(DictWords.in);
		String size = "";
		if(coreBackend.NodeSize(operation, stage)>1 ) 
			size += "("+coreBackend.NodeSize(operation, stage)+" -1 downto 0)";
		// Add top interface	
		interf_lineToBeInserted = sig_name+" : "+sig_in+" "+sig_type+" "+size+";-- ISAX\n";
		if(special_case)
			interf_lineToBeInserted = sig_name+" : "+"buffer"+" "+size+";-- ISAX\n";
		return interf_lineToBeInserted;
	}
	
	
	
	
	public String CreateTextISAXorOrig(String if_clause, String new_signal, String ISAX_signal, String orig_signal) {
		String text ="";
		text += "process(all) begin \n"
				+ tab+"if( "+if_clause+" ) then\n"
				+ tab.repeat(2)+new_signal+" <= "+ISAX_signal+"; \n"
				+ tab+"else \n"
				+ tab.repeat(2)+new_signal+" <= "+orig_signal+";\n"
				+ tab+"end if;\n"
				+"end process;\n\n";
		return text;
	}
	public String CreateTextRegReset(String signal_name, String signal_assign) {
		String text ="";
		text += "process (clk, reset) \n"
				+ "begin\n"
				+ tab+"if reset = '1' then\n"
				+ tab.repeat(2)+signal_name+" <= '0';\n"
				+ tab+"elsif clk'EVENT and clk = '1' then\n"
				+ tab.repeat(2)+signal_name+" <= "+signal_assign+";\n"
				+ tab+"end if;\n"
				+ "end process;\n\n";
		return text;
	}
	
	public String CreateText1or0(String new_signal, String condition) {
		String text =  new_signal + " <= '1' when ("+condition+") else '0';\n";
		return text;
	}
	
	public String CreateInProc(boolean clk, String text) {
		int i = 1;
		String sensitivity = "all";
		String clockEdge = "";
		String endclockEdge = "";
		if(clk) {
			sensitivity = "clk";
			clockEdge = tab+"if rising_edge(clk) then\n";
			i++;
			endclockEdge = tab+"end if;\n";
		}
		String body ="process("+sensitivity+") begin -- ISAX Logic\n "+clockEdge+AllignText(tab.repeat(i),text)+"\n"+endclockEdge+"end process;\n" ;
		return body;		
	}
	
	

	

	
}
