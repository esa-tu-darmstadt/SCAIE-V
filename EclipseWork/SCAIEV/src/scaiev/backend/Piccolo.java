package scaiev.backend;

import java.util.HashMap;
import java.util.HashSet;

import scaiev.coreconstr.Core;
import scaiev.frontend.FNode;
import scaiev.frontend.SCAIEVInstr;
import scaiev.util.Bluespec;
import scaiev.util.FileWriter;
import scaiev.util.Lang;
import scaiev.util.ToWrite;

public class Piccolo extends CoreBackend{


	HashSet<String> AddedIValid = new HashSet<String>();
	
	public  String 			pathPiccolo = "CoresSrc\\Piccolo\\src_Core\\";
	private Core 			piccolo_core;
	private HashMap <String,SCAIEVInstr>  ISAXes;
	private HashMap<String, HashMap<Integer,HashSet<String>>> op_stage_instr;
	private FileWriter 		toFile = new FileWriter();
	public  String          tab = toFile.tab;
	private String 			extension_name;
	private Bluespec        language = new Bluespec(toFile,this);
	private String          topModule = "mkCore";
	private String 			grepDeclareBehav = "// INTERFACE";
	
	private int nrTabs = 0;
	public void Generate (HashMap <String,SCAIEVInstr> ISAXes, HashMap<String, HashMap<Integer,HashSet<String>>> op_stage_instr, String extension_name, Core core) { // core needed for verification purposes
		// Set variables
		this.piccolo_core = core;
		this.ISAXes = ISAXes;
		this.op_stage_instr = op_stage_instr;
		this.extension_name = extension_name;
		ConfigPiccolo();
		IntegrateISAX_IOs();
		IntegrateISAX_NoIllegalInstr();
		IntegrateISAX_RdRS();
		IntegrateISAX_RdIvalid();
		IntegrateISAX_RdStall();
		IntegrateISAX_WrStall();
		IntegrateISAX_SpawnRD();
		IntegrateISAX_FlushStages();
		IntegrateISAX_WrRD();
		IntegrateISAX_WrPC();
		IntegrateISAX_Mem();
		IntegrateISAX_SpawnMem();
		
		toFile.WriteFiles(Lang.Bluespec);
	}
	
	private void IntegrateISAX_IOs() {

		 language.GenerateAllInterfaces(topModule,op_stage_instr,ISAXes,  piccolo_core ,BNode.RdStall);
		 for(int stage = 0;stage<=this.piccolo_core.maxStage;stage++) {
			 // Typical for Piccolo: if useer wants flush in last stage, we need to generate RdInstr and Flush signals from pre-last stage . These will be used by datahaz mechanism in case of spawn 
			 if((stage > (this.piccolo_core.GetNodes().get(BNode.RdRS1).GetEarliest()+1)) && op_stage_instr.containsKey(BNode.WrPC) && op_stage_instr.get(BNode.WrPC).containsKey(stage)) {
				 if(!(op_stage_instr.containsKey(BNode.RdInstr) && op_stage_instr.get(BNode.RdInstr).containsKey(stage-1)))
					 language.UpdateInterface(topModule,BNode.RdInstr, "",stage-1,true,false);
				 if(!(op_stage_instr.containsKey(BNode.RdFlush) && op_stage_instr.get(BNode.RdFlush).containsKey(stage-1)))
					 language.UpdateInterface(topModule,BNode.RdFlush, "",stage-1,true,false);
				 
			 }
		 }	
	}
	
	private void IntegrateISAX_NoIllegalInstr() {
		HashSet<String> allISAX = new HashSet<String>();
		for(String ISAX : ISAXes.keySet())
				allISAX.add(ISAX);
		String rdInstr  = "isax_instr";
		this.toFile.UpdateContent(this.ModFile("fv_ALU"), "function ALU_Outputs fv_ALU (ALU_Inputs inputs);",new ToWrite("let isax_instr = {inputs.decoded_instr.funct3,5'd0,inputs.decoded_instr.opcode};\n",false,true,""));
		if(this.op_stage_instr.containsKey(BNode.WrMem)) {
			this.toFile.UpdateContent(this.ModFile("fv_ALU"), "function ALU_Outputs fv_STORE (ALU_Inputs inputs);", new ToWrite("let isax_instr = {inputs.decoded_instr.funct3,5'd0,inputs.decoded_instr.opcode};\n",false,true,""));
			this.toFile.UpdateContent(this.ModFile("fv_ALU"), "function ALU_Outputs fv_STORE (ALU_Inputs inputs);",new ToWrite("let is_isax =  "+language.CreateAllEncoding(op_stage_instr.get(BNode.WrMem).get(this.piccolo_core.GetNodes().get(BNode.WrMem).GetLatest()),ISAXes,rdInstr)+";\n",false,true,""));	
			toFile.ReplaceContent(this.ModFile("fv_ALU"), "Bool legal_STORE = ",new ToWrite("Bool legal_STORE = (   ((opcode == op_STORE) || is_isax)",false,true,""));
			this.toFile.UpdateContent(this.ModFile("fv_ALU"), "else begin",new ToWrite("else if( "+language.CreateAllEncoding(op_stage_instr.get(BNode.WrMem).get(this.piccolo_core.GetNodes().get(BNode.WrMem).GetLatest()),ISAXes,rdInstr)+") begin\n"+tab+"alu_outputs = fv_STORE (inputs);\nend",true,false,"inputs.decoded_instr.opcode == op_STORE_FP)",true));	
		}
		if(this.op_stage_instr.containsKey(BNode.RdMem)) { 
			this.toFile.UpdateContent(this.ModFile("fv_ALU"), "function ALU_Outputs fv_LOAD (ALU_Inputs inputs);", new ToWrite("let isax_instr = {inputs.decoded_instr.funct3,5'd0,inputs.decoded_instr.opcode};\n",false,true,""));
			this.toFile.UpdateContent(this.ModFile("fv_ALU"), "function ALU_Outputs fv_LOAD (ALU_Inputs inputs);",new ToWrite("let is_isax =  "+language.CreateAllEncoding(op_stage_instr.get(BNode.RdMem).get(this.piccolo_core.GetNodes().get(BNode.RdMem).GetLatest()),ISAXes,rdInstr)+";\n",false,true,""));	
			toFile.ReplaceContent(this.ModFile("fv_ALU"), "Bool legal_LOAD = ",new ToWrite("Bool legal_LOAD = (   ((opcode == op_LOAD) || is_isax)",false,true,""));
			this.toFile.UpdateContent(this.ModFile("fv_ALU"), "else begin",new ToWrite("else if( "+language.CreateAllEncoding(op_stage_instr.get(BNode.RdMem).get(this.piccolo_core.GetNodes().get(BNode.RdMem).GetLatest()),ISAXes,rdInstr)+") begin\n"+tab+"alu_outputs = fv_LOAD (inputs);\nend",true,false,"inputs.decoded_instr.opcode == op_LOAD_FP)",true));	
		}
		if(this.op_stage_instr.containsKey(BNode.RdRS1) || this.op_stage_instr.containsKey(BNode.RdInstr) || this.op_stage_instr.containsKey(BNode.WrPC) || this.op_stage_instr.containsKey(BNode.RdPC) || this.op_stage_instr.containsKey(BNode.RdIValid)) {
			this.toFile.UpdateContent(this.ModFile("fv_ALU"), "else begin",new ToWrite("else if( "+language.CreateAllNoMemEncoding(allISAX,ISAXes,rdInstr)+") begin\n"+tab+"alu_outputs = alu_outputs_base;\nalu_outputs.op_stage2 = OP_Stage2_ISAX;\nend",true,false,"else if (   (inputs.decoded_instr.opcode == op_STORE_FP))",true));	
			this.toFile.UpdateContent(this.ModFile("CPU_Globals"),"typedef enum {  OP_Stage2_ALU",new ToWrite(", OP_Stage2_ISAX",false,true,""));
		}
		
	}
	private void IntegrateISAX_RdRS() {
		for(int i=1;i<3;i++) {
			String nodeName = BNode.RdRS1;
			if(i==2)
				nodeName = BNode.RdRS2;
			if(op_stage_instr.containsKey(nodeName)) {
				if(op_stage_instr.get(nodeName).containsKey(1) || op_stage_instr.get(nodeName).containsKey(2)) {
					this.toFile.UpdateContent(this.ModFile("mkCPU"), "module mkCPU",new ToWrite(language.CreateDeclReg(nodeName,1,""),false,true,""));	
					this.toFile.UpdateContent(this.ModFile("mkCPU"), "begin",new ToWrite(language.CreateLocalNodeName(nodeName,1,"").replace("_s","_reg") + " <= stage1.met_"+language.CreateNodeName(nodeName, 0, "")+";",true,false,"// Move instruction from Stage1 to Stage2"));
					if(!op_stage_instr.get(nodeName).containsKey(0))
						language.UpdateInterface(topModule,nodeName, "",0,false,true);
					if(op_stage_instr.get(nodeName).containsKey(2)) {
						this.toFile.UpdateContent(this.ModFile("mkCPU"), "module mkCPU",new ToWrite(language.CreateDeclReg(nodeName,2,""),false,true,""));	
						this.toFile.UpdateContent(this.ModFile("mkCPU"), "begin",new ToWrite(language.CreateLocalNodeName(nodeName,2,"").replace("_s","_reg") + " <= "+language.CreateLocalNodeName(nodeName,1,"").replace("_s","_reg")+";",true,false,"// Move instruction from Stage1 to Stage2"));
					}	
				}
			}
		}
	}
	private void IntegrateISAX_RdIvalid() {
		if(op_stage_instr.containsKey(BNode.RdIValid)) {
			for(int stage : op_stage_instr.get(BNode.RdIValid).keySet())
				for(String ISAX :op_stage_instr.get(BNode.RdIValid).get(stage)) {
					String checkEmpty = "(stage"+(stage+1)+".out.ostatus != OSTATUS_EMPTY)";
					if(stage==2)
						checkEmpty = "rg_full";
					toFile.UpdateContent(this.ModFile(this.NodeAssignM(BNode.RdInstr, stage)), grepDeclareBehav, new ToWrite(language.CreatePutInRule(language.CreateLocalNodeName(BNode.RdIValid, stage,ISAX) + " <= "+language.CreateAllEncoding(new HashSet<String>() {{add(ISAX);}}, ISAXes, this.NodeAssign(BNode.RdInstr, stage))+" && "+checkEmpty+";",BNode.RdIValid, stage,ISAX), false, true, "", true));
				}		
		}
		
	}
	
	private void IntegrateISAX_WrRD() {
		String wrRD = "// ISAX WrRD Logic //\n";
		int stage = this.piccolo_core.GetNodes().get(BNode.WrRD).GetLatest();
		if(op_stage_instr.containsKey(BNode.WrRD)) {
			wrRD += language.CreatePutInRule("stage3.met_"+language.CreateNodeName("isax_"+BNode.WrRD_valid, stage,"")+"("+language.CreateNodeName("isax_"+BNode.WrRD_valid, stage,"").replace("_i","_reg")+");","isax_"+BNode.WrRD_valid, stage,"")
				 + "rule rule2_"+language.CreateNodeName("isax_"+BNode.WrRD_valid, stage,"")+";\n"
				 + tab + language.CreateNodeName("isax_"+BNode.WrRD_valid, stage,"").replace("_i","_reg") + " <= pack("+language.CreateValidEncoding(op_stage_instr.get(BNode.WrRD).get(stage), ISAXes,"stage2.out.data_to_stage3.instr",BNode.WrRD )+");\n"
				 + "endrule\n";
			language.UpdateInterface("mkCPU","isax_"+BNode.WrRD_valid, "",stage,false,true);
			toFile.UpdateContent(this.ModFile("mkCPU"), grepDeclareBehav, new ToWrite(wrRD+"\n",false,true,"", true));
			String address = " rg_stage3.rd";
			String addrEnc = language.CreateAddrEncoding(op_stage_instr.get(BNode.WrRD).get(stage), ISAXes, "rg_stage3.instr", BNode.WrRD);
			if(!addrEnc.isEmpty())
				address = "("+addrEnc+") ? "+language.CreateLocalNodeName(BNode.WrRD_addr, stage,"")+" : "+address;
			toFile.UpdateContent(this.ModFile("mkCPU_Stage3"), "// BEHAVIOR", new ToWrite("let isax_rg_stage3_rd_val = ((unpack("+language.CreateLocalNodeName("isax_"+BNode.WrRD_valid, stage,"")+")) ? "+language.CreateLocalNodeName(BNode.WrRD,stage,"")+" : rg_stage3.rd_val);\nlet isax_rg_stage3_rd ="+address+";",false,true,""));
			toFile.ReplaceContent(this.ModFile("mkCPU_Stage3"), "rd:", new ToWrite("rd: isax_rg_stage3_rd,\n",true,false,"let bypass_base = Bypass"));
			toFile.ReplaceContent(this.ModFile("mkCPU_Stage3"), "rd_val:", new ToWrite("rd_val:       isax_rg_stage3_rd_val\n",true,false,"let bypass_base = Bypass"));
			toFile.ReplaceContent(this.ModFile("mkCPU_Stage3"), "gpr_regfile.write_rd", new ToWrite("gpr_regfile.write_rd (isax_rg_stage3_rd, isax_rg_stage3_rd_val);\n",true,false,"// Write to GPR"));
			toFile.ReplaceContent(this.ModFile("mkCPU_Stage3"), "rg_stage3.rd, rg_stage3.rd_val", new ToWrite("isax_rg_stage3_rd, isax_rg_stage3_rd_val);\n",true,false,"write GRd 0x"));
		}		
	}
	
	private void IntegrateISAX_RdStall() {
		if(op_stage_instr.containsKey(BNode.RdStall)) {
			for(int stage = 0; stage <2; stage++) {
				if(op_stage_instr.get(BNode.RdStall).containsKey(stage)) {
					toFile.UpdateContent(this.ModFile("mkCPU"), "begin", new ToWrite(language.CreateLocalNodeName(BNode.RdStall, stage,"")+" <= False;",true,false,"// Move instruction from Stage"+(stage+1)+" to Stage"+(stage+2)));					
					toFile.UpdateContent(this.ModFile("mkCPU"), "end", new ToWrite("else\n"+tab+language.CreateLocalNodeName(BNode.RdStall, stage,"")+" <= True;",true,false,"// Move instruction from Stage"+(stage+1)+" to Stage"+(stage+2)));					
					String declare = "Wire #( Bool ) "+language.CreateLocalNodeName(BNode.RdStall,stage,"")+" <- mkDWire( True );\n";
					this.toFile.UpdateContent(this.ModFile("mkCPU"), ");",new ToWrite(declare, true,false,"module mkCPU"));
					this.toFile.UpdateContent(this.ModInterfFile("mkCPU"), "endinterface",new ToWrite(language.CreateMethodName(BNode.RdStall,stage,"",false)+";\n",true,false,"interface",true));
					this.toFile.UpdateContent(this.ModInterfFile("mkCore"), "endinterface",new ToWrite("(*always_enabled *)"+language.CreateMethodName(BNode.RdStall,stage,"",false)+";\n",true,false,"interface",true));
					String assignText = language.CreateMethodName(BNode.RdStall,stage,"",false)+";\n"+tab+"return "+language.CreateLocalNodeName(BNode.RdStall,stage, "")+";\nendmethod";
					this.toFile.UpdateContent(this.ModFile("mkCPU"), "endmodule",new ToWrite(assignText,true,false,"module mkCPU",true));
					this.toFile.UpdateContent(this.ModFile("mkCore"), "endmodule",new ToWrite(language.CreateMethodName(BNode.RdStall,stage,"",true)+" = cpu.met_"+language.CreateNodeName(BNode.RdStall, stage,"")+";\n",true,false,"module mkCore",true));
				}
			}
		}		
	}
	
	private void IntegrateISAX_WrStall() {
		if(ContainsOpInStage(BNode.WrStall,1))
			toFile.ReplaceContent(this.ModFile("mkCPU"), "if ((! stage3_full)", new ToWrite("if ((! stage3_full) && (stage2.out.ostatus == OSTATUS_PIPE) && (!"+language.CreateLocalNodeName(BNode.WrStall, 1,"")+")) begin\n",true,false,"// Move instruction from Stage2 to Stage3"));					
		if(ContainsOpInStage(BNode.WrStall,0))
			toFile.ReplaceContent(this.ModFile("mkCPU"), "if (   (! halting)", new ToWrite("if (   (! halting) && (!"+language.CreateLocalNodeName(BNode.WrStall, 0,"")+")",true,false,"// Move instruction from Stage1 to Stage2"));									
	
	}
	private void IntegrateISAX_WrPC() {
		int nrJumps = 0;
		String updatePC = "";
		String [] instrName = {"stage1.out.data_to_stage2.instr","stage2.out.data_to_stage3.instr","stage2.out.data_to_stage3.instr"};
		for(int stage = this.piccolo_core.maxStage; stage>=0 ; stage--) {
			if(stage==0)
				if(op_stage_instr.containsKey(BNode.WrPC_spawn)) {
					updatePC += "("+language.CreateLocalNodeName(BNode.WrPC_spawn_valid, this.piccolo_core.maxStage+1, "")+") ? ("+language.CreateLocalNodeName(BNode.WrPC_spawn, this.piccolo_core.maxStage+1, "")+") : (";
					nrJumps++;
				}
			
			if(ContainsOpInStage(BNode.WrPC,stage)) {
				updatePC += "("+language.CreateLocalNodeName("isax_"+BNode.WrPC_valid, stage, "")+") ? ("+language.CreateLocalNodeName(BNode.WrPC, stage, "")+") : (";
				nrJumps++;
				
				// Compute update valid bit in CPU 
				String nodeName = language.CreateLocalNodeName( "isax_"+BNode.WrPC_valid,stage, "");
				String assig ="let "+nodeName+" = ";
				if(stage==this.piccolo_core.maxStage)  {
					nodeName = language.CreateLocalNodeName( "isax_"+BNode.WrPC_valid,stage, "").replace("_s", "_reg");
					language.UpdateInterface("mkCPU","isax_"+BNode.WrPC_valid, "",stage,false, nodeName.contains("_reg"));	
					assig = nodeName+" <= ";
				} else 
					language.UpdateInterface("mkCPU_Stage1","isax_"+BNode.WrPC_valid, "",stage,true, false);	
							
				String updatePCValid =  language.CreatePutInRule(assig +language.CreateValidEncoding(op_stage_instr.get(BNode.WrPC).get(stage), ISAXes, instrName[stage], BNode.WrPC)+" && (stage"+(stage+1)+".out.ostatus == OSTATUS_PIPE);\nstage1.met_"+language.CreateNodeName( "isax_"+BNode.WrPC_valid,stage, "")+"("+nodeName+");", "isax_"+BNode.WrPC_valid,stage, "");
				toFile.UpdateContent(this.ModFile("mkCPU"),grepDeclareBehav,new ToWrite(updatePCValid,false,true,""));
				
			}	
		}
		
		
		
		// Change next_pc with ISAX one
		if(nrJumps>0) {
			updatePC += "next_pc"+ ")".repeat(nrJumps)+";";
			toFile.ReplaceContent(this.ModFile("mkCPU_Stage1"), "output_stage1.next_pc", new ToWrite("output_stage1.next_pc = isax_next_pc;",true,false,"let next_pc"));									
			toFile.UpdateContent(this.ModFile("mkCPU_Stage1"), ": fall_through_pc);", new ToWrite("let isax_next_pc ="+updatePC,true,false,"let next_pc"));											
		}
	}
	
	private void IntegrateISAX_FlushStages () {
		// flush stages in case of ISAX jumps or WrFlush
		if(op_stage_instr.containsKey(BNode.WrPC) || op_stage_instr.containsKey(BNode.WrFlush) ) {
			String valid_PC_flush = "";
			if(ContainsOpInStage(BNode.WrPC,2))
				valid_PC_flush += language.CreateLocalNodeName( "isax_"+BNode.WrPC_valid,2, "");
			if(ContainsOpInStage(BNode.WrFlush,2))
				valid_PC_flush += language.OpIfNEmpty(valid_PC_flush,"||") + language.CreateLocalNodeName(BNode.WrFlush,2, "");
			if(!valid_PC_flush.isEmpty())
				toFile.UpdateContent(this.ModFile("mkCPU_Stage2"), "// This stage is just relaying ALU",new ToWrite("if("+valid_PC_flush+") begin "+ language.CreateMemStageFrwrd(false,true),false,true,""));
			ContainsOpInStage(BNode.WrPC,1); {
				valid_PC_flush += language.OpIfNEmpty(valid_PC_flush,"||") + language.CreateLocalNodeName( "isax_"+BNode.WrPC_valid,1, "");
			}
			if(ContainsOpInStage(BNode.WrFlush,1))
				valid_PC_flush += language.OpIfNEmpty(valid_PC_flush,"||") + language.CreateLocalNodeName(BNode.WrFlush,1, "");
			if(!valid_PC_flush.isEmpty())
				toFile.ReplaceContent(this.ModFile("mkCPU_Stage1"),"let ostatus =",new ToWrite("let ostatus = ("+valid_PC_flush+") ? OSTATUS_EMPTY : (  (   (alu_outputs.control == CONTROL_STRAIGHT)",false,true,""));		
		}
	}
	
	private void IntegrateISAX_SpawnRD() {
		int spawnStage = this.piccolo_core.maxStage+1;
		String commit_stage = "stage3";
		if(op_stage_instr.containsKey(BNode.WrRD_spawn)) {		
			// Declarations
			this.toFile.UpdateContent(this.ModFile("mkCPU"), "module mkCPU",new ToWrite(language.CreateSpawnDeclareSigs(op_stage_instr.get(BNode.WrRD_spawn).get(spawnStage),spawnStage, BNode.WrRD_spawn, true),false,true,""));
			
			// Fire and Commit Logic
			// Fire logic
			String stall3 = "";
			if (ContainsOpInStage(BNode.WrStall,0))
				stall3 = language.CreateLocalNodeName(BNode.WrStall, 0, "");
			String fire_logic = language.CommitSpawnFire(BNode.WrRD_spawn,stall3,"stage3.out.ostatus == OSTATUS_PIPE",spawnStage,op_stage_instr.get(BNode.WrRD_spawn).get(spawnStage),new  HashSet<String> (), "");
			// Commit logic
			String priority   = "";
			String elseWord   = "";
			String spawnLogic = "";
			String spawnCommit = "";
			for(String ISAX : op_stage_instr.get(BNode.WrRD_spawn).get(spawnStage)) {
				spawnCommit += elseWord + language.CreateSpawnCommitWrRD(ISAX, spawnStage, priority);
				spawnLogic  +=  language.CreateSpawnLogic(ISAX, spawnStage,commit_stage, priority, BNode.WrRD_spawn);

				if(op_stage_instr.get(BNode.WrRD_spawn).get(spawnStage).size()==1) {
					spawnCommit = language.CreateLocalNodeName(BNode.commited_rd_spawn, spawnStage,"")+" <= "+language.CreateNodeName(BNode.WrRD_spawn_addr, spawnStage,ISAX).replace("_i", "_reg")+";\n";				
					spawnLogic = "stage3.met_"+language.CreateNodeName(BNode.WrRD_spawn_valid, spawnStage,"")+"("+language.CreateNodeName(BNode.WrRD_spawn_addr, spawnStage,ISAX).replace("_i", "_reg")+","+language.CreateNodeName(BNode.WrRD_spawn, spawnStage,ISAX).replace("_i", "_reg")+","+BNode.ISAX_fire2_regF_reg+");\n";
				}
				if(!priority.isEmpty())
					priority += " || ";
				priority   += language.CreateNodeName(BNode.WrRD_spawn_valid, spawnStage,ISAX).replace("_i", "_reg");
			}
			
			String spawnCommitandLogic = "rule rule_spawnCommitandLogic;\n"+spawnCommit+"\nendrule\n";
			this.toFile.UpdateContent(this.ModFile("mkCPU"), grepDeclareBehav, new ToWrite(fire_logic+"\n"+spawnCommitandLogic,false,true,"", true));
			String writeRes = "rule rule_send_to_stage3_spawn( "+BNode.ISAX_fire2_regF_reg+" );\n"+language.AllignText(tab,spawnLogic)+"\nendrule\n";
			this.toFile.UpdateContent(this.ModFile("mkCPU"), grepDeclareBehav, new ToWrite(writeRes+"\n",false,true,"", true));
			
			// Update mkCPU for stall signal 
			if(this.ContainsOpInStage(BNode.WrMem_spawn, spawnStage) || this.ContainsOpInStage(BNode.RdMem_spawn, spawnStage))
				toFile.ReplaceContent(this.ModFile("mkCPU"), "rule rl_pipe",new ToWrite("rule rl_pipe (   (rg_state == CPU_RUNNING) && (!("+BNode.ISAX_spawnStall_regF_s+")) && (!("+BNode.ISAX_spawnStall_mem_s+"))",false,true,""));
			else 
				toFile.ReplaceContent(this.ModFile("mkCPU"), "rule rl_pipe",new ToWrite("rule rl_pipe (   (rg_state == CPU_RUNNING) && (!("+BNode.ISAX_spawnStall_regF_s+"))",false,true,""));
			
			// Update stage 3 
			String stage3 ="method Action met_"+language.CreateNodeName(BNode.WrRD_spawn_valid, spawnStage,"")+" (RegName addr, Bit#(32) data, Bool valid);\n"
					+ tab + "if(valid)\n"
					+ tab + "gpr_regfile.write_rd (addr, data);\n"
					+ "endmethod\n";
			this.toFile.UpdateContent(this.ModFile("mkCPU_Stage3"), "endmodule", new ToWrite(stage3+"\n",false,true,"", true));
			this.toFile.UpdateContent(this.ModFile("mkCPU_Stage3"), "endinterface", new ToWrite("(*always_enabled*)method Action met_"+language.CreateNodeName(BNode.WrRD_spawn_valid, spawnStage,"")+" (RegName x,Bit#(32) y,Bool z);\n",false,true,"", true));
			
		} 
	}
	
	private void IntegrateISAX_Mem() {
		int stage = this.piccolo_core.GetNodes().get(FNode.RdMem).GetLatest();
		String both = "";
		
		if(op_stage_instr.containsKey(BNode.RdMem) || op_stage_instr.containsKey(BNode.WrMem)) {
			if(op_stage_instr.containsKey(BNode.RdMem)) 
				this.toFile.UpdateContent(this.ModFile("mkCPU"), grepDeclareBehav,new ToWrite(language.CreatePutInRule(language.CreateLocalNodeName(BNode.RdMem_valid, stage, "")+" <= (" + language.CreateAllEncoding(op_stage_instr.get(BNode.RdMem).get(stage), ISAXes, "stage2.out.data_to_stage3.instr")+"&& (stage2.out.ostatus == OSTATUS_PIPE));",BNode.RdMem_valid,stage,""),false,true,"", true));
			if(op_stage_instr.containsKey(BNode.WrMem)) {
				if(op_stage_instr.containsKey(BNode.RdMem) )
					both = " || ";
				this.toFile.UpdateContent(this.ModFile("mkCPU_Stage2"), "dcache.req", new ToWrite("let iasx_wdata_from_gpr = ("+language.CreateAllEncoding(op_stage_instr.get(BNode.WrMem).get(stage), ISAXes, "x.instr")+") ? zeroExtend ("+language.CreateLocalNodeName(BNode.WrMem, stage, "")+") : wdata_from_gpr;",false,true,"",true));
				this.toFile.ReplaceContent(this.ModFile("mkCPU_Stage2"), "wdata_from_gpr,", new ToWrite("iasx_wdata_from_gpr,",true,false,"dcache.req"));
			}
			String customAddr = language.CreateAddrEncoding(op_stage_instr.get(BNode.WrMem).get(stage), ISAXes, "x.instr",BNode.WrMem)+ both +language.CreateAddrEncoding(op_stage_instr.get(BNode.RdMem).get(stage), ISAXes, "x.instr",BNode.RdMem);
			if(!customAddr.isEmpty()) {
				this.toFile.UpdateContent(this.ModFile("mkCPU_Stage2"), "dcache.req", new ToWrite("let isax_x_addr = ("+customAddr+") ? "+language.CreateLocalNodeName(BNode.Mem_addr, stage, "")+" : x.addr;",false,true,"",true));
				this.toFile.ReplaceContent(this.ModFile("mkCPU_Stage2"), "x.addr,", new ToWrite("isax_x_addr,",true,false,"dcache.req"));
			}
				
		}
		if(this.op_stage_instr.containsKey(BNode.WrRD)) 
			this.toFile.UpdateContent(this.ModFile("mkCPU_Stage2"), "// This stage is just relaying ALU", new ToWrite("else if ("+language.CreateAllNoMemEncoding(op_stage_instr.get(BNode.WrRD).get(this.piccolo_core.GetNodes().get(BNode.WrRD).GetLatest()),ISAXes,"rg_stage2.instr")+") begin\n"+language.CreateMemStageFrwrd(true,false),false,true,"", true));
		
		HashSet<String> noWrRDLOAD = new HashSet<String>();
		boolean noWrRD = false;
		String or = "";
		for(String ISAX : ISAXes.keySet()) {
			if(!ISAXes.get(ISAX).GetSchedNodes().containsKey(BNode.WrRD) && ISAXes.get(ISAX).GetSchedNodes().containsKey(BNode.RdMem)) {
				noWrRDLOAD.add(ISAX);
				or = " || ";
			}
			if(!ISAXes.get(ISAX).GetSchedNodes().containsKey(BNode.WrRD))
				noWrRD = true;
		}
		if (noWrRD)
			this.toFile.UpdateContent(this.ModFile("mkCPU_Stage2"), "// This stage is just relaying ALU", new ToWrite("else if(rg_stage2.op_stage2 ==  OP_Stage2_ISAX "+or+language.CreateAllEncoding(noWrRDLOAD,ISAXes,"rg_stage2.instr" )+") begin\n"+language.CreateMemStageFrwrd(false,false),false,true,"", true));
		
	}
	
	
	private void IntegrateISAX_SpawnMem() {
		int spawnStage = this.piccolo_core.maxStage +1;
		String commit_stage = "stage2";
		String node = BNode.RdMem_spawn.split("d")[1]; 
		
		if(op_stage_instr.containsKey(BNode.RdMem_spawn) || op_stage_instr.containsKey(BNode.WrMem_spawn)) {
			// Declarations
			this.toFile.UpdateContent(this.ModFile("mkCPU"), "module mkCPU",new ToWrite(language.CreateSpawnDeclareSigs(op_stage_instr.get(BNode.RdMem_spawn).get(spawnStage),spawnStage, BNode.RdMem_spawn, true),false,true,""));
			this.toFile.UpdateContent(this.ModFile("mkCPU"), "module mkCPU",new ToWrite(language.CreateSpawnDeclareSigs(op_stage_instr.get(BNode.WrMem_spawn).get(spawnStage),spawnStage, BNode.WrMem_spawn, false),false,true,""));
			
			// Fire and Commit Logic
			// Fire logic
			String stall0 = "";
			if (ContainsOpInStage(BNode.WrStall,0))
				stall0 = language.CreateLocalNodeName(BNode.WrStall, 0, "");
			String fire_logic = language.CommitSpawnFire(node,stall0,"stage3.out.ostatus == OSTATUS_PIPE",spawnStage,op_stage_instr.get(BNode.WrMem_spawn).get(spawnStage),op_stage_instr.get(BNode.RdMem_spawn).get(spawnStage), "near_mem.dmem.valid && isax_memONgoing_wire");
			// Commit logic
			String priority   = "";
			String spawnLogic = "";
			String spawnCommit = "";
			String[] nodes = {BNode.WrMem_spawn,BNode.RdMem_spawn };
			int i= 0 ;
			for(i=0;i<2;i++) {
				if( op_stage_instr.containsKey(nodes[i])) {
					for(String ISAX : op_stage_instr.get(nodes[i]).get(spawnStage)) {
						spawnCommit += language.CreateSpawnCommitMem(ISAX, spawnStage, priority,nodes[i],"near_mem.dmem.valid && isax_memONgoing_wire","truncate( near_mem.dmem.word64)");
						spawnLogic  += language.CreateSpawnLogic(ISAX, spawnStage,commit_stage, priority, nodes[i]);
	
						// TODO
						if(op_stage_instr.get(nodes[i]).get(spawnStage).size()==1) {				
						}
						if(!priority.isEmpty())
							priority += " || ";
						priority   += language.CreateNodeName(BNode.Mem_spawn_valid, spawnStage,ISAX).replace("_i", "_reg");
					}
				}
			}	
			 spawnCommit = "rule rule_spawn"+node+"CommitandLogic;\n"+spawnCommit+"\nendrule\n";
			this.toFile.UpdateContent(this.ModFile("mkCPU"), grepDeclareBehav, new ToWrite(fire_logic+"\n"+spawnCommit,false,true,"", true));
			String writeRes = "rule rule_send_to_stage2_spawn( "+BNode.ISAX_fire2_mem_reg+" &&  !isax_memONgoing);\n"+language.AllignText(tab,spawnLogic)+"\n if("+priority+") isax_memONgoing <= True; \nendrule\n";
			this.toFile.UpdateContent(this.ModFile("mkCPU"), grepDeclareBehav, new ToWrite(writeRes+"\n",false,true,"", true));
			
			// Update mkCPU for stall signal 
			if(!this.ContainsOpInStage(BNode.WrRD_spawn, spawnStage)) // else, text updated in wrrd_spawn function
				toFile.ReplaceContent(this.ModFile("mkCPU"), "rule rl_pipe",new ToWrite("rule rl_pipe (   (rg_state == CPU_RUNNING) && (!("+BNode.ISAX_spawnStall_mem_s+"))",false,true,""));
			
			String spawnStall = language.CreatePutInRule(BNode.ISAX_spawnStall_mem_s +" <= "+ BNode.ISAX_fire2_mem_reg, BNode.ISAX_spawnStall_mem_s, spawnStage,""); // TODO
			// Update stage 3 
			String stage2 = "method Action met_"+language.CreateNodeName(BNode.Mem_spawn_valid, spawnStage,"")+" (Bit#(32) addr, Bit#(32) data, Bool valid, Bool read); \n"
					+ "   CacheOp cache_op = ?;\n"
					+ "	    if      (read)  cache_op = CACHE_LD;\n"
					+ "	    else  cache_op = CACHE_ST;\n "
					+ "   if(valid) \n"
					+ "        dcache.req (cache_op, \n"
					+ "		        instr_funct3 ({17'd0,3'b010,12'd0}), \n"
					+ "				addr, \n"
					+ "				{32'd0,data}, \n"
					+ "				3, \n"
					+ "				0, \n"
					+ "				(csr_regfile.read_mstatus)[19], \n"
					+ "				csr_regfile.read_satp); \n"
					+ "endmethod\n";

			this.toFile.UpdateContent(this.ModFile("mkCPU_Stage2"), "endmodule", new ToWrite(stage2+"\n",false,true,"", true));
			this.toFile.UpdateContent(this.ModFile("mkCPU_Stage2"), "endinterface", new ToWrite("method Action met_"+language.CreateNodeName(BNode.Mem_spawn_valid, spawnStage,"")+" (Bit#(32) x,Bit#(32) y,Bool z, Bool read);\n",false,true,"", true));
			
			
			String checkSpawnDone = "rule rule_isaxwire;\n"
					+ "isax_memONgoing_wire <= isax_memONgoing;\n"
					+ "endrule\n"
					+ "\n"
					+ "rule rule_resetMemOngoing (isax_memONgoing) ; \n"
					+ "if(near_mem.dmem.valid)\n"
					+ "isax_memONgoing <= False;\n"
					+ "endrule\n"
					+ "\n";
			this.toFile.UpdateContent(this.ModFile("mkCPU"), "module mkCPU",new ToWrite("Wire #(Bool) isax_memONgoing_wire <- mkWire();\nReg #(Bool) isax_memONgoing <- mkReg(False);\n",false,true,""));
			
			this.toFile.UpdateContent(this.ModFile("mkCPU"), grepDeclareBehav, new ToWrite(checkSpawnDone,false,true,"", true));
			
		}
	}
	
	private boolean ContainsOpInStage(String operation, int stage) {
		return op_stage_instr.containsKey(operation) && op_stage_instr.get(operation).containsKey(stage);
	}
	
	private void ConfigPiccolo() {
	 	this.PopulateNodesMap(this.piccolo_core.maxStage);
	 	
	 	PutModule(this.pathPiccolo+"Core\\Core_IFC.bsv",  "Core_IFC", this.pathPiccolo+"Core\\Core.bsv", "", "mkCore");
	 	PutModule(this.pathPiccolo+"CPU\\CPU_IFC.bsv",  "CPU_IFC", this.pathPiccolo+"CPU\\CPU.bsv", "mkCore", "mkCPU");
	 	PutModule(this.pathPiccolo+"CPU\\CPU_Stage1.bsv",  "CPU_Stage1_IFC", this.pathPiccolo+"CPU\\CPU_Stage1.bsv", "mkCPU", "mkCPU_Stage1");
	 	PutModule(this.pathPiccolo+"CPU\\CPU_Stage2.bsv",  "CPU_Stage2_IFC", this.pathPiccolo+"CPU\\CPU_Stage2.bsv", "mkCPU", "mkCPU_Stage2");
	 	PutModule(this.pathPiccolo+"CPU\\CPU_Stage3.bsv",  "CPU_Stage3_IFC", this.pathPiccolo+"CPU\\CPU_Stage3.bsv", "mkCPU", "mkCPU_Stage3");
	 	PutModule("",  "", this.pathPiccolo+"CPU\\EX_ALU_functions.bsv", "", "fv_ALU");
	 	PutModule("",  "", this.pathPiccolo+"CPU\\CPU_Globals.bsv", "", "CPU_Globals");	
	 	
	 	int spawnStage = this.piccolo_core.maxStage+1;
	 	
	 	this.PutNode(32, true, "Bit", "", "mkCPU_Stage1", BNode.WrPC,0);
 		this.PutNode(1, true, "Bool", "",  "mkCPU", BNode.WrPC_valid,0);
 		this.PutNode(32, true, "Bit", "",  "mkCPU_Stage1", BNode.WrPC,1);
 		this.PutNode(1, true, "Bool", "",  "mkCPU", BNode.WrPC_valid,1);
 		this.PutNode(32, true, "Bit", "",  "mkCPU_Stage1", BNode.WrPC,2);
 		this.PutNode(1, true, "Bool", "",  "mkCPU", BNode.WrPC_valid,2);
 		this.PutNode(32, true, "Bit", "", "mkCPU_Stage1", BNode.WrPC_spawn,3);
 		this.PutNode(1, true, "Bool", "",  "mkCPU_Stage1", BNode.WrPC_spawn_valid,3);
 	
	 	this.PutNode(32, false, "Bit", "pc", "mkCPU_Stage1", BNode.RdPC,0);
	 	this.PutNode(32, false, "Bit", "rg_stage2.pc", "mkCPU_Stage2", BNode.RdPC,1);
	 	this.PutNode(32, false, "Bit", "rg_stage3.pc", "mkCPU_Stage3", BNode.RdPC,2);
	

	 	this.PutNode(32, false, "Bit", "stage1.out.data_to_stage2.instr", "mkCPU", BNode.RdInstr,0);
	 	this.PutNode(32, false, "Bit", "stage2.out.data_to_stage3.instr", "mkCPU", BNode.RdInstr,1);
	 	this.PutNode(32, false, "Bit", "rg_stage3.instr", "mkCPU_Stage3", BNode.RdInstr,2);
 		
	 	this.PutNode(32, false, "Bit", "rs1_val_bypassed", "mkCPU_Stage1", BNode.RdRS1,0);
	 	this.PutNode(32, false, "Bit", language.CreateNodeName(BNode.RdRS1, 1, "",false).replace("_o", "_reg"), "mkCPU", BNode.RdRS1,1); 		
	 	this.PutNode(32, false, "Bit", language.CreateNodeName(BNode.RdRS1, 2, "",false).replace("_o", "_reg"), "mkCPU", BNode.RdRS1,2); 		

	 	this.PutNode(32, false, "Bit", "rs2_val_bypassed", "mkCPU_Stage1", BNode.RdRS2,0);
	 	this.PutNode(32, false, "Bit", language.CreateNodeName(BNode.RdRS2, 1, "",false).replace("_o", "_reg"), "mkCPU", BNode.RdRS2,1);
	 	this.PutNode(32, false, "Bit", language.CreateNodeName(BNode.RdRS2, 2, "",false).replace("_o", "_reg"), "mkCPU", BNode.RdRS2,2);
	 	
	 	this.PutNode(32, true, "Bit", "", "mkCPU_Stage3", BNode.WrRD,2);
	 	this.PutNode(32, true, "Bit", "", "mkCPU", BNode.WrRD_valid,2);
	 	this.PutNode(5, true, "Bit", "", "mkCPU_Stage3", BNode.WrRD_addr,2);
	 	
	 	
	 	this.PutNode(1,  false, "Bool", "", "mkCPU", BNode.RdIValid,0);	 		
	 	this.PutNode(1,  false, "Bool", "", "mkCPU", BNode.RdIValid,1);	 		
	 	this.PutNode(1,  false, "Bool", "", "mkCPU_Stage3", BNode.RdIValid,2);	 		
	 	
		int stageMem = this.piccolo_core.GetNodes().get(BNode.RdMem).GetLatest();
	 	this.PutNode(32, false, "Bit", "stage2.out.data_to_stage3.rd_val", "mkCPU", BNode.RdMem,stageMem);
	 	this.PutNode(1,  false, "Bool", "", "mkCPU", BNode.RdMem_valid,stageMem);
	 	this.PutNode(32, true,  "Bit", "", "mkCPU_Stage2", BNode.WrMem,stageMem);
	 	this.PutNode(1,  true,  "Bool",  "","mkCPU", BNode.Mem_valid,stageMem);
	 	this.PutNode(32, true,  "Bit", "", "mkCPU_Stage2",BNode.Mem_addr,stageMem);
	 
	 	this.PutNode(1,  false, "Bool", "", "mkCPU", BNode.RdStall,0);
	 	this.PutNode(1,  false, "Bool", "", "mkCPU", BNode.RdStall,1);
	 	this.PutNode(1,  false, "Bool", "", "mkCPU", BNode.RdStall,2);
	 	
	 	this.PutNode(1, true, "Bool", "", "mkCPU", BNode.WrStall,0);
	 	this.PutNode(1, true, "Bool", "", "mkCPU", BNode.WrStall,1);
	 	this.PutNode(1, true, "Bool", "", "mkCPU", BNode.WrStall,2);
	 	
	 	this.PutNode(1, false, "Bool", "stage1.out.ostatus == OSTATUS_EMPTY", "mkCPU", BNode.RdFlush,0);
	 	this.PutNode(1, false, "Bool", "stage2.out.ostatus == OSTATUS_EMPTY", "mkCPU", BNode.RdFlush,1);
	 	this.PutNode(1, false, "Bool", "stage3.out.ostatus == OSTATUS_EMPTY", "mkCPU", BNode.RdFlush,2);
	 	
	 	
	 	this.PutNode(32, true, "Bit", "", "mkCPU", BNode.WrRD_spawn,spawnStage);
	 	this.PutNode(1,  true, "Bool", "", "mkCPU", BNode.WrRD_spawn_valid,spawnStage);
	 	this.PutNode(5,  true, "Bit", "", "mkCPU", BNode.WrRD_spawn_addr,spawnStage);

	 	this.PutNode(32, false, "Bit", "", "mkCPU", BNode.RdMem_spawn,spawnStage);
	 	this.PutNode(1, false, "Bool","","mkCPU", BNode.RdMem_spawn_valid,spawnStage);
	 	this.PutNode(32, true,  "Bit", "", "mkCPU", BNode.WrMem_spawn,spawnStage);
	 	this.PutNode(1, false, "Bool","","mkCPU",BNode.WrMem_spawn_valid,spawnStage);
	 	this.PutNode(1,  true, "Bool", "", "mkCPU", BNode.Mem_spawn_valid,spawnStage);
	 	this.PutNode(32, true,  "Bit", "","mkCPU", BNode.Mem_spawn_addr,spawnStage);
	 	
	 	this.PutNode(1, false,  "Bool",BNode.ISAX_fire2_regF_reg,"mkCPU", BNode.commited_rd_spawn_valid,spawnStage);
	 	this.PutNode(5, false,  "Bit","", "mkCPU",BNode.commited_rd_spawn,spawnStage);
	 	
	 	
	 	
	 	// internal signals, additional nodes to generate automatically interface 
	 	this.PutNode(1, true, "Bit", "", "mkCPU_Stage3", "isax_"+BNode.WrRD_valid,2);
	 	this.PutNode(1, true, "Bool", "", "mkCPU_Stage1", "isax_"+BNode.WrPC_valid,0);
	 	this.PutNode(1, true, "Bool", "", "mkCPU_Stage1", "isax_"+BNode.WrPC_valid,1);
	 	this.PutNode(1, true, "Bool", "", "mkCPU_Stage1", "isax_"+BNode.WrPC_valid,2);
	}
	
}
