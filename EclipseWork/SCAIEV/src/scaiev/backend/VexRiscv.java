package scaiev.backend;

import java.util.HashMap;
import java.util.HashSet;

import scaiev.coreconstr.Core;
import scaiev.frontend.SCAIEVInstr;
import scaiev.util.FileWriter;
import scaiev.util.Lang;
import scaiev.util.SpinalHDL;
import scaiev.util.ToWrite;

public class VexRiscv extends CoreBackend{
	public  String 			pathRISCV = "CoresSrc/VexRiscv/src/main/scala/vexriscv";
	public  String 			baseConfig = "VexRiscvAhbLite3";
	private Core 			vex_core;
	private HashMap <String,SCAIEVInstr>  ISAXes;
	private HashMap<String, HashMap<Integer,HashSet<String>>> op_stage_instr;
	private FileWriter 		toFile = new FileWriter();
	private String 			filePlugin ;
	private String 			extension_name;
	private SpinalHDL       language = new SpinalHDL(toFile,this);
	
	private int nrTabs = 0;
	public void Generate (HashMap <String,SCAIEVInstr> ISAXes, HashMap<String, HashMap<Integer,HashSet<String>>> op_stage_instr, String extension_name, Core vex_core) { // core needed for verification purposes
		// Set variables
		this.vex_core = vex_core;
		this.ISAXes = ISAXes;
		this.op_stage_instr = op_stage_instr;
		this.extension_name = extension_name;
		
		
		ConfigVex();
		this.filePlugin = fileHierarchy.get(extension_name).file;
		language.currentFile = this.filePlugin;
		
		IntegrateISAX_DefImports();
		IntegrateISAX_DefObjValidISAX();
		IntegrateISAX_OpenClass();
		IntegrateISAX_Services();
		IntegrateISAX_OpenSetupPhase();
		for(String ISAX : ISAXes.keySet()) 
			IntegrateISAX_SetupDecode(ISAXes.get(ISAX));
		IntegrateISAX_SetupServices();
		IntegrateISAX_CloseSetupPhase();
		IntegrateISAX_OpenBuild();
		for(int i = 0;i<=this.vex_core.maxStage;i++) 
			IntegrateISAX_Build(i);
		language.CloseBrackets(); // close build section
		language.CloseBrackets(); // close class
		
		
		// Configs
		IntegrateISAX_UpdateConfigFile(); // If everything went well, also update config file
		IntegrateISAX_UpdateArbitrationFile(); // in case of WrRD spawn, update arbitration
		IntegrateISAX_UpdateRegFforSpawn(); // in case of WrRD spawn, update arbitration
		IntegrateISAX_UpdateIFetchFile();
		IntegrateISAX_UpdateServiceFile();
		IntegrateISAX_UpdateMemFile();
		
		
		// Write all files 
		toFile.WriteFiles(Lang.SpinalHDL);
	}
	
	
	 // #################################### IMPORT PHASE ####################################
	 private void IntegrateISAX_DefImports() { 
		 String addText = """
		 		package vexriscv.plugin
				import vexriscv._ 
				import spinal.core._
				import spinal.lib._;
				import scala.collection.mutable.ArrayBuffer
				import scala.collection.mutable
				import scala.collection.JavaConverters._
		 		""";
		 toFile.UpdateContent(filePlugin, addText);
	 }
	  
	 // Declare ISAXes as objects IValid
	 private void IntegrateISAX_DefObjValidISAX() {
		 toFile.UpdateContent(filePlugin, "object "+extension_name+" {");
		 toFile.nrTabs++;
		 String stageables = language.allSCAIEVInstrText("\n","object IS_"," extends Stageable(Bool)",ISAXes);
		 toFile.UpdateContent(filePlugin, stageables);
		 language.CloseBrackets();
	 }
	 
	 // Define ISAX plugin class
	 private void IntegrateISAX_OpenClass() {
		 String defineClass = "class "+extension_name+"(writeRfInMemoryStage: Boolean = false)extends Plugin[VexRiscv] {"; 
		 toFile.UpdateContent(filePlugin,defineClass); 
		 toFile.nrTabs++;
		 toFile.UpdateContent(filePlugin,"import "+extension_name+"._");			
	 }
		 
	// Declare Required Services
	 private void IntegrateISAX_Services() {
		 if(op_stage_instr.containsKey(BNode.WrPC)) 
			 for(int i=1;i<this.vex_core.maxStage+2;i++)
				 if(op_stage_instr.get(BNode.WrPC).containsKey(i))
					 toFile.UpdateContent(filePlugin,"var jumpInterface_"+i+": Flow[UInt] = null");
		 if(op_stage_instr.containsKey(BNode.RdMem) || op_stage_instr.containsKey(BNode.WrMem) )
			 toFile.UpdateContent(filePlugin,"var dBusAccess : DBusAccess = null");
		 
		 if(ContainsOpInStage(BNode.WrPC,0) || op_stage_instr.containsKey(BNode.WrPC_spawn)) 
			 toFile.UpdateContent(filePlugin,"var  jumpInFetch : JumpInFetch = null");
	 }
	 
	 // ######################################################################################################
	 // #############################################   SETUP    #############################################
	 // ######################################################################################################
	 private void IntegrateISAX_OpenSetupPhase() {
		 toFile.UpdateContent(filePlugin,"\n// Setup phase.");
		 toFile.UpdateContent(filePlugin,"override def setup(pipeline: VexRiscv): Unit = {");
		 toFile.UpdateContent(filePlugin,"import pipeline._");
		 toFile.nrTabs++; // should become 2
		 toFile.UpdateContent(filePlugin,"import pipeline.config._");
		 toFile.UpdateContent(filePlugin,"val decoderService = pipeline.service(classOf[DecoderService])");
	 }
	 
	 private void IntegrateISAX_SetupDecode(SCAIEVInstr isax) {
		String setupText  = "" ; 
		int tabs = toFile.nrTabs;
		String tab = toFile.tab;
		boolean defaultMemAddr = (isax.HasNode(BNode.RdMem) && !isax.GetNode(BNode.RdMem).GetAddrInterf() ) || (isax.HasNode(BNode.WrMem) && !isax.GetNode(BNode.WrMem).GetAddrInterf());
		setupText += tab.repeat(tabs)+"decoderService.addDefault(IS_"+isax.GetName()+", False)\n";
		setupText += tab.repeat(tabs)+"decoderService.add(\n";
		
		tabs++; 
		setupText += tab.repeat(tabs)+isax.GetEncodingString()+"\n";	// Set encoding			
		setupText += tab.repeat(tabs)+"List(\n";
		
		// Signal this ISAX
		tabs++; 
		setupText += tab.repeat(tabs)+"IS_"+isax.GetName()+"                  -> True,\n";
		
		// 	? PC INCREMENT DESIRED?
		// OP1
		if(isax.HasNode(BNode.RdImm) && isax.GetInstrType().equals("U"))
			setupText += tab.repeat(tabs)+"SRC1_CTRL                -> Src1CtrlEnum.IMU,\n";
		else if(isax.HasNode(BNode.RdRS1) || defaultMemAddr )
			setupText += tab.repeat(tabs)+"SRC1_CTRL                -> Src1CtrlEnum.RS,\n";
		
		// OP2
		if(isax.HasNode(BNode.RdImm) || defaultMemAddr) {
			if(isax.GetInstrType().equals("I") || defaultMemAddr) {
				setupText += tab.repeat(tabs)+"SRC2_CTRL                -> Src2CtrlEnum.IMI,\n";
				setupText += tab.repeat(tabs)+"SRC_USE_SUB_LESS  	     -> False,\n"; //  EC
			}
			else if(isax.GetInstrType().equals("S"))
				setupText += tab.repeat(tabs)+"SRC2_CTRL                -> Src2CtrlEnum.IMS,\n";
		}
		else 
			setupText += tab.repeat(tabs)+"SRC2_CTRL                -> Src2CtrlEnum.RS,\n";
		
		// WRITE REGFILE
		if(isax.HasNode(BNode.WrRD) && isax.GetNode(BNode.WrRD).GetStartCycle()<=this.vex_core.maxStage)
			setupText += tab.repeat(tabs)+"REGFILE_WRITE_VALID      -> True,\n";
		else 
			setupText += tab.repeat(tabs)+"REGFILE_WRITE_VALID      -> False,\n";
						
		// Read Reg file?
		String add_comma = "";
		if(isax.HasNode(BNode.WrRD))
			add_comma = tab.repeat(tabs)+",";
		if(isax.HasNode(BNode.RdRS1) || defaultMemAddr)
			setupText += tab.repeat(tabs)+"RS1_USE                  -> True,\n";
		else 
			setupText += tab.repeat(tabs)+"RS1_USE                  -> False,\n";
		if(isax.HasNode(BNode.RdRS2))
			setupText += tab.repeat(tabs)+"RS2_USE                  -> True"+add_comma+"\n";
		else 
			setupText += tab.repeat(tabs)+"RS2_USE                  -> False"+add_comma+"\n";	
		
		if(isax.HasNode(BNode.WrRD)) {
			setupText += tab.repeat(tabs)+"BYPASSABLE_EXECUTE_STAGE -> False,\n";
			if(isax.GetNode(BNode.WrRD).GetStartCycle()<=this.vex_core.maxStage) // spawn is not bypassable
				setupText += tab.repeat(tabs)+"BYPASSABLE_MEMORY_STAGE  -> True\n";
			else 
				setupText += tab.repeat(tabs)+"BYPASSABLE_MEMORY_STAGE  -> False\n";
		}
		tabs--; // should become 3
		setupText += tab.repeat(tabs)+")\n";
		tabs--; //should become 2
		setupText += tab.repeat(tabs)+")\n";
		toFile.UpdateContent(filePlugin,setupText);
	 }
	 
	 
	 private void IntegrateISAX_SetupServices() {
		 // toFile.nrTabs should be 2
		 String setupServices = "";
		 if(op_stage_instr.containsKey(BNode.WrPC)) {
			 boolean warning_spawn = false;
			 for(int i=0;i<this.vex_core.maxStage+2;i++) { 
				 if(op_stage_instr.get(BNode.WrPC).containsKey(i)) {
					if((this.vex_core.maxStage+1)==i) { 
						setupServices += "jumpInFetch = pipeline.service(classOf[JumpInFetchService]).createJumpInFetchInterface();\n";
						System.out.println("INTEGRATE. WARNING Requesting PC update in start cycle "+i+" but the core has max start cycle = "+this.vex_core.GetNodes().get("RdPC").GetLatest()+". Inserting spawn. PC Update in decode");
						if(warning_spawn)
							System.out.println("INTEGRATE. WARNING Another ISAX is updating PC . Spawn PC present => Compiler should solve conflicts. Shim layer will give priority to SPAWN if concurrent PC update");
							
					} else {
						if(i==1)
							warning_spawn = true;
						setupServices += "val flushStage_"+i+" = if (memory != null) memory else execute\n";
						setupServices += "val pcManagerService_"+i+" = pipeline.service(classOf[JumpService])\n";
						setupServices += "jumpInterface_"+i+" = pcManagerService_"+i+".createJumpInterface(flushStage_"+i+")\n"; 				
					}
				}
			 }			
		 }
		 if(op_stage_instr.containsKey(BNode.WrPC_spawn))  {
			 setupServices += "jumpInFetch = pipeline.service(classOf[JumpInFetchService]).createJumpInFetchInterface();\n";
		 }
		 if(op_stage_instr.containsKey(BNode.RdMem) || op_stage_instr.containsKey(BNode.RdMem_spawn) ||  op_stage_instr.containsKey(BNode.WrMem) || op_stage_instr.containsKey(BNode.WrMem_spawn) ) {
			 setupServices += "// Get service for memory transfers\n";
			 setupServices += "dBusAccess = pipeline.service(classOf[DBusAccessService]).newDBusAccess();\n";
		}
		 toFile.UpdateContent(filePlugin,setupServices);
	 }
	 
	 
	 private void IntegrateISAX_CloseSetupPhase() {
		 language.CloseBrackets();
	 }
	 
	 // #####################################################################################
	 // #################################### BUILD PHASE ####################################
	 // #####################################################################################
	 /***************************************
	  * Open build section 
	  **************************************/
	 private void IntegrateISAX_OpenBuild() {
		 toFile.UpdateContent(filePlugin,"\n// Build phase.");
		 toFile.UpdateContent(filePlugin,"override def build(pipeline: VexRiscv): Unit = {");
		 toFile.nrTabs++;
		 toFile.UpdateContent(filePlugin,"import pipeline._");
		 toFile.UpdateContent(filePlugin,"import pipeline.config._");

		 if(op_stage_instr.containsKey(BNode.WrRD)) // EC
			 toFile.UpdateContent(filePlugin,"val writeStage = if (writeRfInMemoryStage) pipeline.memory else pipeline.stages.last ");
	 }
	 
	 /***************************************
	  * Open build section for ONE plugin/stage
	  ***************************************/
	 private void IntegrateISAX_Build(int stage) {
		 for(String operation : op_stage_instr.keySet()) {
			 boolean spawnReq = ( (operation.contains(BNode.WrMem_spawn) || operation.contains(BNode.RdMem_spawn) || operation.contains(BNode.WrRD_spawn))  && stage==this.vex_core.maxStage) || (operation.contains(BNode.WrPC_spawn) && stage==this.vex_core.GetNodes().get(BNode.WrPC).GetEarliest()) ;
			 if(op_stage_instr.get(operation).containsKey(stage) || spawnReq)
			 {
				 if(op_stage_instr.containsKey(BNode.WrPC_spawn) && stage==0) {
					 IntegrateISAX_BuildDecoupledPC();
				 	break;
			     } else {
					 toFile.UpdateContent(filePlugin," ");
					 toFile.UpdateContent(filePlugin,stages.get(stage)+" plug new Area {");
					 toFile.UpdateContent(filePlugin,"import "+stages.get(stage)+"._");
					 toFile.nrTabs++;
					 IntegrateISAX_BuildIOs(stage);
					 IntegrateISAX_BuildBody(stage);
					 language.CloseBrackets(); // close build stage
					 
					 break;
				 }
			 }
		 }
		 
	 }
	 
	 /**************************************
	  * Build body, Inputs/Outputs.
	  **************************************/
	 private void IntegrateISAX_BuildIOs(int stage) {
		 toFile.UpdateContent(filePlugin,"val io = new Bundle {");
		 toFile.nrTabs++;
		  
		 // Work in progress
		 // TODO 
		 /*
		 if(op_stage_instr.get("RdImm").containsKey(stage)) {
			 HashSet<String> seen = new HashSet<String>();
			 for(ISAX instruction : rdImm.get(stage)) {
				 if(!seen.contains(instruction.GetInstrType())) {
					 seen.add(instruction.GetInstrType());
					 toFile.UpdateContent(filePlugin,"val RdImm_"+instruction.GetInstrType()+"_"+stage+"_o = out Bits(32 bits)");
				 }
			 }		
		 }*/
		 String interfaces = "";
		 boolean mem_addr = false;
		 boolean reg_addr = false;
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
					 if(!data) {
						 interfaces += language.CreateInterface(operation,stage,instructionName);	
						 data = true;
					 }
					 if(ISAXes.get(instruction).GetNode(operation).GetValidInterf() && !operation.contains("spawn") && !valid ) {
						 interfaces += language.CreateInterface(operation+"_valid",stage,instructionName);
						 valid = true;
					 }
					 if(ISAXes.get(instruction).GetNode(operation).GetAddrInterf() && !operation.contains("spawn")) {
						 if(operation.contains("Mem") && !mem_addr) {
							 interfaces += language.CreateInterface(BNode.Mem_addr,stage,instructionName);
							 mem_addr = true;
						 } else if(reg_addr) {
							 interfaces += language.CreateInterface(operation+"_addr",stage,instructionName);
							 reg_addr = true;
						 }
					 }
					 
				 }
			 }
		 // Memory data read valid 
		 if(op_stage_instr.containsKey(BNode.RdMem) && op_stage_instr.get(BNode.RdMem).containsKey(stage))
			 interfaces += language.CreateInterface(BNode.RdMem_valid,stage,"");
		 // Spawn interfaces
		 int spawnStage = this.vex_core.maxStage+1;
		 if(stage == this.vex_core.GetNodes().get(BNode.RdMem).GetLatest() && op_stage_instr.containsKey(BNode.RdMem_spawn)) {
			 for(String instruction : op_stage_instr.get(BNode.RdMem_spawn).get(this.vex_core.maxStage+1)) {
				 interfaces += language.CreateInterface(BNode.RdMem_spawn,spawnStage,instruction);	
				 interfaces += language.CreateInterface(BNode.RdMem_spawn_valid,spawnStage,instruction);	
				 interfaces += language.CreateInterface(BNode.Mem_spawn_valid,spawnStage,instruction);
				 interfaces += language.CreateInterface(BNode.Mem_spawn_addr,spawnStage,instruction);
			 }
		 }
		 if(stage == this.vex_core.GetNodes().get(BNode.WrMem).GetLatest() && op_stage_instr.containsKey(BNode.WrMem_spawn)) {
			 for(String instruction : op_stage_instr.get(BNode.WrMem_spawn).get(this.vex_core.maxStage+1)) {
				 interfaces += language.CreateInterface(BNode.WrMem_spawn,spawnStage,instruction);
				 interfaces += language.CreateInterface(BNode.WrMem_spawn_valid,spawnStage,instruction);
				 interfaces += language.CreateInterface(BNode.Mem_spawn_valid,spawnStage,instruction);
				 interfaces += language.CreateInterface(BNode.Mem_spawn_addr,spawnStage,instruction);
			 }
		 }
		 
		 if(stage == this.vex_core.GetNodes().get(BNode.WrRD).GetLatest() && (op_stage_instr.containsKey(BNode.WrRD_spawn))) {
			 for(String instruction : op_stage_instr.get(BNode.WrRD_spawn).get(this.vex_core.maxStage+1)) {
				 interfaces += language.CreateInterface(BNode.WrRD_spawn,spawnStage,instruction);	
				 interfaces += language.CreateInterface(BNode.WrRD_spawn_valid,spawnStage,instruction);
				 interfaces += language.CreateInterface(BNode.WrRD_spawn_addr,spawnStage,instruction);
				 
			 }
			 // for spawn datahaz mechanism
			 interfaces += "val commited_rd_spawn_valid_4_o = out Bool()\n"
					+"val commited_rd_spawn_4_o = out Bits(5 bits)\n"
			 		+"val "+language.CreateNodeName(BNode.WrStall, 2, "")+" = in Bool()";
			 if(!this.op_stage_instr.containsKey(BNode.WrStall) || !(this.op_stage_instr.containsKey(BNode.WrStall) && this.op_stage_instr.get(BNode.WrStall).containsKey(2) )) interfaces += "val wrStall_2_i = in Bool()";
		 }
		 
		 toFile.UpdateContent(filePlugin,interfaces);
		 language.CloseBrackets();
	 }
	 
	 public void IntegrateISAX_BuildDecoupledPC() {
		 int spawnStage = this.vex_core.maxStage+1;
		 String text = "pipeline plug new Area {\n"
			        +toFile.tab.repeat(1)+"import pipeline._\n"
		            +toFile.tab.repeat(1)+"val io = new Bundle {\n"
		            +toFile.tab.repeat(2)+"val "+language.CreateNodeName(BNode.WrPC_spawn, spawnStage, "")+"= in UInt(32 bits)\n"
		            +toFile.tab.repeat(2)+"val "+language.CreateNodeName(BNode.WrPC_spawn_valid, spawnStage, "")+"= in Bool()\n"
		            +toFile.tab.repeat(1)+"}\n"
			 		+toFile.tab.repeat(1)+"jumpInFetch.target_PC := io."+language.CreateNodeName(BNode.WrPC_spawn, spawnStage, "")+";\n"
			 		+toFile.tab.repeat(1)+"jumpInFetch.update_PC := io."+language.CreateNodeName(BNode.WrPC_spawn_valid, spawnStage, "")+";\n"
			 		+toFile.tab.repeat(1)+"decode.arbitration.flushNext setWhen jumpInFetch.update_PC;\n"
		 		    +"}\n"; 
		 toFile.UpdateContent(filePlugin,text);
	 }
		 
	 
	 /*************************************
	  * Build body for ONE stage.
	  *************************************/	 
	 public void IntegrateISAX_BuildBody(int stage) {
		String thisStagebuild = "";
		String clause = "";
		
	    
		// Simple assigns
		for(String operation : op_stage_instr.keySet()) {
			if( (stage >0) && op_stage_instr.get(operation).containsKey(stage) && (!operation.contains(BNode.WrRD) && !operation.contains(BNode.WrMem) && !operation.contains(BNode.RdMem)) ) { // it cannot be spawn, as stage does not go to max_stage + 1
				if(stage>=this.vex_core.GetNodes().get(operation).GetEarliest()) {
					if(operation.contains(BNode.RdIValid))
						for(String instruction : op_stage_instr.get(operation).get(stage))
							thisStagebuild += language.CreateAssignToISAX(operation,stage,instruction,false);
					else 
						thisStagebuild += language.CreateAssignToISAX(operation,stage,"",(operation.contains(BNode.WrStall) || operation.contains(BNode.WrFlush)) );
				}
			}
		}
		
		
		// WrPC valid clause
		if(ContainsOpInStage(BNode.WrPC,stage))  {
			clause = language.CreateClauseValid(ISAXes, BNode.WrPC, stage,"");
			thisStagebuild += "jumpInterface_"+stage+".valid := arbitration.isValid && ("+clause+") && !arbitration.isStuckByOthers\n";
			thisStagebuild += "arbitration.flushNext setWhen (jumpInterface_"+stage+".valid);";
		}
		thisStagebuild += "\n\n\n";
		toFile.UpdateContent(filePlugin,thisStagebuild);
		IntegrateISAX_WrRDBuild(stage);
		if(stage == this.vex_core.GetNodes().get(BNode.RdMem).GetEarliest() && (op_stage_instr.containsKey(BNode.WrMem) || op_stage_instr.containsKey(BNode.WrMem_spawn) || op_stage_instr.containsKey(BNode.RdMem) || op_stage_instr.containsKey(BNode.RdMem_spawn)))
			 IntegrateISAX_MemBuildBody(stage);
		
		
      }
	 
	 
	// RD/WR Memory
	 private void IntegrateISAX_MemBuildBody(int stage) {
		int memStage = this.vex_core.GetNodes().get(BNode.RdMem).GetLatest();
		if(stage == memStage) {
			// Default MEM Signals
			if(op_stage_instr.containsKey(BNode.WrMem) || op_stage_instr.containsKey(BNode.WrMem_spawn) || op_stage_instr.containsKey(BNode.RdMem) || op_stage_instr.containsKey(BNode.RdMem_spawn))
			{
				String states = "IDLE, CMD";
				if(op_stage_instr.containsKey(BNode.RdMem) || op_stage_instr.containsKey(BNode.RdMem_spawn))
					states += ", RESPONSE";	
				String declareStates = "val State = new SpinalEnum{\n"
						+ toFile.tab+"val "+states+" = newElement()\n"
						+ "}\n"
						+"val state = RegInit(State.IDLE)\n"; 
				toFile.UpdateContent(filePlugin,declareStates);
				toFile.ReplaceContent(pathRISCV+"/plugin/DBusSimplePlugin.scala","when(!stages.dropWhile(_ != execute)", new ToWrite("when(stages.dropWhile(_ != execute).map(_.arbitration.isValid).orR){",false,true,""));
			}
			/////////////////  START CODE, DEFAULT VALUES ///////////
			// SPAWN SIGNALS
			int spawnStage = this.vex_core.maxStage+1;
			boolean spawnReq = op_stage_instr.containsKey(BNode.WrMem_spawn) || op_stage_instr.containsKey(BNode.RdMem_spawn);
			String declareSpawnSig  = "";
			String spawnLogic = "";
			if(spawnReq) {
				if(op_stage_instr.containsKey(BNode.WrMem_spawn))
					for(String instructionName :  op_stage_instr.get(BNode.WrMem_spawn).get(spawnStage)) {
						declareSpawnSig +=language.CreateDeclReg(BNode.WrMem_spawn, spawnStage, instructionName);
						declareSpawnSig +=language.CreateDeclReg(BNode.Mem_spawn_valid, spawnStage, instructionName);
						declareSpawnSig +=language.CreateDeclReg(BNode.Mem_spawn_addr, spawnStage, instructionName);
						spawnLogic += language.CreateSpawnTrigMem(BNode.WrMem_spawn,spawnStage,instructionName,0);
						spawnLogic += "io."+language.CreateNodeName(BNode.WrMem_spawn_valid, spawnStage, instructionName)+":= False\n";
					}
				if(op_stage_instr.containsKey(BNode.RdMem_spawn))
					for(String instructionName :  op_stage_instr.get(BNode.RdMem_spawn).get(spawnStage)) {
						declareSpawnSig +=language.CreateDeclReg(BNode.Mem_spawn_valid, spawnStage, instructionName);
						declareSpawnSig +=language.CreateDeclReg(BNode.Mem_spawn_addr, spawnStage, instructionName);
						spawnLogic += language.CreateSpawnTrigMem(BNode.RdMem_spawn,spawnStage,instructionName,0);
						spawnLogic += "io."+language.CreateNodeName(BNode.RdMem_spawn_valid, spawnStage, instructionName)+" := False\nio."+language.CreateNodeName(BNode.RdMem_spawn, spawnStage, instructionName)+" := 0\n";
					}
				toFile.UpdateContent(filePlugin,declareSpawnSig);
				spawnLogic = "val "+BNode.ISAX_fire2_mem_reg+" =   Reg( Bool) init(False)\n"
						+"val "+BNode.ISAX_fire_mem_reg+" =   Reg( Bool) init(False)\n"
						+ spawnLogic
						+"// I have valid result for mem spawn & current instr in the pipeline is firing => afterwards I can write my result\n"
						+"when("+BNode.ISAX_fire_mem_reg+" && (execute.arbitration.isFiring)) { \n"
						+toFile.tab+BNode.ISAX_fire2_mem_reg+" := True\n"
						+toFile.tab+BNode.ISAX_fire_mem_reg+" := False\n"
						+"}\n"
						+"when("+BNode.ISAX_fire2_mem_reg+") {\n"
	                  	+toFile.tab+"execute.arbitration.haltItself := True;\n"
	                  	+"}\n";

				String sum = "";
				String asUInt = "";
				
				int nrSpawn = 0;
				if(op_stage_instr.containsKey(BNode.WrMem_spawn)) {
					nrSpawn += op_stage_instr.get(BNode.WrMem_spawn).get(spawnStage).size();
					sum += language.allISAXNameText(" + ", BNode.Mem_spawn_valid+"_", "_"+spawnStage+"_reg.asUInt.resize("+((int) (Math.ceil( Math.log(nrSpawn+1) / Math.log(2) ) ) )+")",op_stage_instr.get(BNode.WrMem_spawn).get(spawnStage));
					nrSpawn += op_stage_instr.get(BNode.WrMem_spawn).get(spawnStage).size();
				}
				if(op_stage_instr.containsKey(BNode.RdMem_spawn)) {
					if(!sum.isEmpty())
						sum += "+";
					sum += language.allISAXNameText(" + ", BNode.Mem_spawn_valid+"_",  "_"+spawnStage+"_reg.asUInt.resize("+((int) (Math.ceil( Math.log(nrSpawn+1) / Math.log(2) ) ) )+")",op_stage_instr.get(BNode.RdMem_spawn).get(spawnStage));
					nrSpawn += op_stage_instr.get(BNode.RdMem_spawn).get(spawnStage).size();
				}
				spawnLogic ="val "+BNode.ISAX_sum_spawn_mem_s+" = UInt("+((int) (Math.ceil( Math.log(nrSpawn+1) / Math.log(2) ) ) ) +" bits)\n"+spawnLogic+ " "+ BNode.ISAX_sum_spawn_mem_s+" := "+sum+";\n";
				
				toFile.UpdateContent(filePlugin,spawnLogic);
				
			}
			
			
			// Default MEM Signals
			String defaultText= ""; 
			if(op_stage_instr.containsKey(BNode.RdMem)) 
				defaultText += "io."+language.CreateNodeName(BNode.RdMem_valid, stage, "")+" := False\nio."+language.CreateNodeName(BNode.RdMem, stage, "")+" := 0\n";
			
			if(!spawnReq)
				defaultText += "val "+BNode.ISAX_fire2_mem_reg+" =   Reg( Bool) init(False)\n" + defaultText ; // added here and not in spawn logic because definition is still required if no spawn, as it is used bellow
			defaultText += "// Define some default values for memory FSM\n"
					+ "dBusAccess.cmd.valid := False\n"
					+ "dBusAccess.cmd.write := False\n"
					+ "dBusAccess.cmd.size := 0 \n"
					+ "dBusAccess.cmd.address.assignDontCare() \n"
					+ "dBusAccess.cmd.data.assignDontCare() \n"
					+ "dBusAccess.cmd.writeMask.assignDontCare()\n"
					+ "\n"
					+ "val ldst_in_decode = Bool()		    \n"
					+ "when(state !==  State.IDLE) {\n"
					+ toFile.tab+"when(ldst_in_decode) {\n"
					+ toFile.tab.repeat(2)+"decode.arbitration.haltItself := True;\n"
					+ toFile.tab+"}\n"
					+ "}\n";
			String comb = "";
			if(op_stage_instr.containsKey(BNode.RdMem) && op_stage_instr.containsKey(BNode.WrMem))
				comb = " || ";
			defaultText += "ldst_in_decode := ("+language.CreateClauseValid(ISAXes, BNode.RdMem, stage, stages.get(stage-1)) +comb+ language.CreateClauseValid(ISAXes, BNode.WrMem, stage, stages.get(stage-1)) + ")"; 
			toFile.UpdateContent(filePlugin,defaultText);
			
			
			/////////////////  FSM ///////////	
			// IDLE, CMD
			String spawnTrig = "";
			if(spawnReq)
				spawnTrig = "when("+BNode.ISAX_fire2_mem_reg+" || ("+BNode.ISAX_fire_mem_reg+" && (memory.arbitration.isFiring))) {\n"  
							+toFile.tab+"state := State.CMD\n"		                
						    +"}\n";
			String fsm = "switch(state){\n"
					+toFile.tab.repeat(1) + "is(State.IDLE){\n"
					+toFile.tab.repeat(2) + "when(ldst_in_decode && decode.arbitration.isFiring) { \n" 
				    +toFile.tab.repeat(3) + "state := State.CMD\n"	                
					+toFile.tab.repeat(2) + "}\n"
					+  language.AllignText(toFile.tab.repeat(2), spawnTrig)   
					+toFile.tab.repeat(1)+"}\n"
					+toFile.tab.repeat(1)+"is(State.CMD){\n"
					+toFile.tab.repeat(2)+"when(execute.arbitration.isValid || "+BNode.ISAX_fire2_mem_reg+") { \n"
					+toFile.tab.repeat(3)+"dBusAccess.cmd.valid := True \n"
					+toFile.tab.repeat(3)+"dBusAccess.cmd.size := execute.input(INSTRUCTION)(13 downto 12).asUInt	\n";
					
			if(!op_stage_instr.containsKey(BNode.WrMem))
				fsm += toFile.tab.repeat(3)+"dBusAccess.cmd.data  := 0\ndBusAccess.cmd.write := False\n"; 
			else 
				fsm += toFile.tab.repeat(3)+"dBusAccess.cmd.data  := io.wrMem_"+stage+"_i\n"+toFile.tab.repeat(3)+"dBusAccess.cmd.write := " +language.CreateClause(ISAXes,  BNode.WrMem, stage, stages.get(stage))+"\n";
			String addrRd =  language.CreateClauseAddr(ISAXes, BNode.RdMem, stage, stages.get(stage));
			String addrWr =  language.CreateClauseAddr(ISAXes, BNode.WrMem, stage, stages.get(stage));
			if(addrRd.isEmpty() && addrWr.isEmpty())
				fsm += "dBusAccess.cmd.address := execute.input(SRC_ADD).asUInt";
			else {
				String combAddr  ="";
				if(!addrRd.isEmpty() && !addrWr.isEmpty())
					 combAddr = ") && !( ";
				 fsm +=   toFile.tab.repeat(3)+"when(!("+addrRd+combAddr+addrWr+")) {\n"
				 		+ toFile.tab.repeat(4)+"dBusAccess.cmd.address := execute.input(SRC_ADD).asUInt\n"
				 		+ toFile.tab.repeat(3)+"}.otherwise {\n"
				 		+ toFile.tab.repeat(4)+"dBusAccess.cmd.address := io."+language.CreateNodeName(BNode.Mem_addr, 2, "")+"\n"
				 		+ toFile.tab.repeat(3)+"}\n";
			}
			
			String spawnCMDRDY = ""; // Switch to Response/Idle 
			if(spawnReq) {
				String priority = ""; 
				if(op_stage_instr.containsKey(BNode.WrMem_spawn))
					for(String instruction : op_stage_instr.get(BNode.WrMem_spawn).get(spawnStage)) {
						fsm += language.CreateSpawnCMDMem(BNode.WrMem_spawn,spawnStage,instruction,3,priority);
						spawnCMDRDY += language.CreateSpawnCMDRDYMem(BNode.WrMem_spawn,spawnStage,instruction,spawnStage,priority);
						if(!priority.isEmpty())
							priority += " || ";
						priority   += language.CreateNodeName(BNode.Mem_spawn_valid, spawnStage,instruction).replace("_i", "_reg");
					}
				if(op_stage_instr.containsKey(BNode.RdMem_spawn))
					for(String instruction : op_stage_instr.get(BNode.RdMem_spawn).get(spawnStage)) {
						fsm += language.CreateSpawnCMDMem(BNode.RdMem_spawn,spawnStage,instruction,3,priority);
						spawnCMDRDY += language.CreateSpawnCMDRDYMem(BNode.RdMem_spawn,spawnStage,instruction,spawnStage,priority);
						if(!priority.isEmpty())
							priority += " || ";
						priority   += language.CreateNodeName(BNode.Mem_spawn_valid, spawnStage,instruction).replace("_i", "_reg");
					}
			}
				
			
		
			fsm += toFile.tab.repeat(3)+"when(dBusAccess.cmd.ready) {\n";
			if(op_stage_instr.containsKey(BNode.RdMem)  && op_stage_instr.containsKey(BNode.WrMem)) {
				fsm +=    toFile.tab.repeat(4)+" when( "+language.CreateClause(ISAXes,  BNode.WrMem, stage, stages.get(stage))+") {\n"
						+ toFile.tab.repeat(5)+"state := State.IDLE\n"
						+ toFile.tab.repeat(4)+"}.otherwise {\n"
						+ toFile.tab.repeat(5)+"state := State.RESPONSE\n"
						+ toFile.tab.repeat(4)+"}\n";
			 } else if(op_stage_instr.containsKey(BNode.RdMem))
				 fsm +=   toFile.tab.repeat(4)+"state := State.RESPONSE\n";
			
			 else {
				 fsm +=   toFile.tab.repeat(4)+"state := State.IDLE\n";
			 }
			fsm      +=   spawnCMDRDY
					 +    toFile.tab.repeat(3)+"}.otherwise {\n"
					 +    toFile.tab.repeat(4)+"execute.arbitration.haltItself := True\n"
					 +    toFile.tab.repeat(3)+"}\n"		
			         +    toFile.tab.repeat(2)+"}\n"
					 +    "}\n"; // from open is state
			// RESPONSE 
			String spawnRSP = "";
			String priority = "";
			if(spawnReq && (op_stage_instr.containsKey(BNode.RdMem_spawn))) {
				for(String instruction : op_stage_instr.get(BNode.RdMem_spawn).get(spawnStage)) {
					spawnRSP += language.CreateSpawnRSPRDYMem(spawnStage,instruction,2,priority);
					if(!priority.isEmpty())
						priority += " || ";
					priority   += language.CreateNodeName(BNode.Mem_spawn_valid, spawnStage,instruction).replace("_i", "_reg");
				}
			}		
			fsm +=    toFile.tab.repeat(1)+"is(State.RESPONSE){  \n"
					+ toFile.tab.repeat(2)+"when(dBusAccess.rsp.valid){ \n"
					+ toFile.tab.repeat(3)+"state := State.IDLE\n"
					+ toFile.tab.repeat(3)+"io."+language.CreateNodeName( BNode.RdMem, stage, "")+" := dBusAccess.rsp.data  \n"
					+ toFile.tab.repeat(3)+"io."+language.CreateNodeName(BNode.RdMem_valid, stage, "")+"  := True\n"
					+ language.AllignText(toFile.tab.repeat(3), spawnRSP)
					+ toFile.tab.repeat(2)+"} .otherwise {\n"
					+ toFile.tab.repeat(3)+"memory.arbitration.haltItself := True\n"
					+ toFile.tab.repeat(2)+"}\n"
					+ toFile.tab.repeat(1)+"}\n"
					+ "}";
			toFile.UpdateContent(filePlugin,fsm); 
		}
		
	 }
	 public void IntegrateISAX_WrRDBuild(int stage) {
		 // COMMON WrRD, NO SPAWN : 
		 String clause = "";
		 clause = language.CreateClauseAddr(ISAXes,BNode.WrRD,stage,"" );
		 if(!clause.isEmpty()) {
			 toFile.UpdateContent(filePlugin,"when("+clause+") {\n");
			 toFile.nrTabs++;	 
			 toFile.UpdateContent(filePlugin,stages.get(stage)+".output(INSTRUCTION) :=((31 downto 12) -> input(INSTRUCTION)(31 downto 12), (11 downto 7) -> io."+language.CreateNodeName(BNode.WrRD_addr, stage, "")+", (6 downto 0) ->input(INSTRUCTION)(6 downto 0));\n"); // EC
			 toFile.nrTabs--;
			 toFile.UpdateContent(filePlugin,"}\n");
		 }
		 clause = language.CreateClauseValid(ISAXes, BNode.WrRD, stage,"");
		 if(!clause.isEmpty()) {
			 toFile.UpdateContent(filePlugin,"when("+clause+") {\n");
			 toFile.nrTabs++;	 
			 toFile.UpdateContent(filePlugin,stages.get(stage)+".output(REGFILE_WRITE_VALID) := True;\n"); // EC
			 toFile.nrTabs--;
			 toFile.UpdateContent(filePlugin,"}\n");	
		 }
		 clause = language.CreateClause(ISAXes, BNode.WrRD, stage,"");
		 if(!clause.isEmpty()) {
			 toFile.UpdateContent(filePlugin,"when("+clause+") {\n");
			 toFile.nrTabs++;	 
			 toFile.UpdateContent(filePlugin,stages.get(stage)+".output(REGFILE_WRITE_DATA) := io."+language.CreateNodeName(BNode.WrRD, stage, "")+";\n");				 		
			 toFile.nrTabs--;
			 toFile.UpdateContent(filePlugin,"}\n");			 
		 }
		 
		 // SPAWN
		 int spawnStage = this.vex_core.maxStage+1;
		 if(op_stage_instr.containsKey(BNode.WrRD_spawn) && stage==this.vex_core.maxStage) {			 
			 String declareSpawnSig = "";
			 for(String instructionName :  op_stage_instr.get(BNode.WrRD_spawn).get(spawnStage)) {
				 declareSpawnSig +=language.CreateDeclReg(BNode.WrRD_spawn, spawnStage, instructionName);
				 declareSpawnSig +=language.CreateDeclReg(BNode.WrRD_spawn_valid, spawnStage, instructionName);
				 declareSpawnSig +=language.CreateDeclReg(BNode.WrRD_spawn_addr, spawnStage, instructionName);
			 }
			 declareSpawnSig += "val "+BNode.ISAX_fire_regF_reg+" =   Reg( Bool) init(False)\n"
			 		+ "val "+BNode.ISAX_fire2_regF_reg+" = Reg( Bool) init(False)\n"
			 		+ "val "+BNode.ISAX_fire_regF_s+" = Bool\n";
			 declareSpawnSig += "val "+BNode.ISAX_sum_spawn_regF_s+" = UInt("+((int)  (Math.ceil( Math.log(this.op_stage_instr.get(BNode.WrRD_spawn).get(spawnStage).size()+1) / Math.log(2) ) ) ) +" bits);\n";
			 String fireLogic = "";
			 fireLogic = BNode.ISAX_fire_regF_s+" := " +language.allISAXNameText("||","io." +BNode.WrRD_spawn_valid+"_","_"+spawnStage+"_i",op_stage_instr.get(BNode.WrRD_spawn).get(spawnStage)) +";\n"; //!ela
			 fireLogic += "	// I have valid result & current instr in the pipeline is firing => afterwards I can write my result\n "
			 		+ "when(("+BNode.ISAX_fire_regF_reg+" || "+BNode.ISAX_fire_regF_s+") && (writeStage.arbitration.isFiring || io."+language.CreateNodeName(BNode.WrStall,2,"")+")) { \n"
			 		+ toFile.tab + BNode.ISAX_fire2_regF_reg+" := True\n"
			 		+ toFile.tab + BNode.ISAX_fire_regF_reg+ " := False\n"
			 		+ toFile.tab + "execute.arbitration.haltByOther := True\n"
			 		+ "}";
			 String defaultValues = "// Some defaults\n"
			 		+ "io.commited_rd_spawn_valid_4_o :=  "+BNode.ISAX_fire2_regF_reg+";\n"
			 		+ "io.commited_rd_spawn_4_o :=  0; //default\n"
			 		+ BNode.ISAX_sum_spawn_regF_s+" :=";

			 defaultValues += language.allISAXNameText("  + ", BNode.WrRD_spawn_valid+"_", "_"+spawnStage+"_reg.asUInt.resize("+((int) (Math.ceil( Math.log(this.op_stage_instr.get(BNode.WrRD_spawn).get(spawnStage).size()+1) / Math.log(2) ) ) )+")",op_stage_instr.get(BNode.WrRD_spawn).get(spawnStage)) +";\n";
			 toFile.UpdateContent(filePlugin,declareSpawnSig+defaultValues+fireLogic);
			 for(String instructionName :  op_stage_instr.get(BNode.WrRD_spawn).get(spawnStage)) {					
				 toFile.UpdateContent(filePlugin,"when(io."+language.CreateNodeName(BNode.WrRD_spawn_valid, spawnStage, instructionName)+"){\n");
				 toFile.nrTabs++;	 
				 toFile.UpdateContent(filePlugin,BNode.WrRD_spawn+"_"+instructionName+"_"+spawnStage+"_reg := io."+BNode.WrRD_spawn+"_"+instructionName+"_"+spawnStage+"_i;\n");				 		
				 toFile.UpdateContent(filePlugin,BNode.WrRD_spawn_addr+"_"+instructionName+"_"+spawnStage+"_reg := io."+BNode.WrRD_spawn_addr+"_"+instructionName+"_"+spawnStage+"_i;\n");				 		
				 toFile.UpdateContent(filePlugin,BNode.WrRD_spawn_valid+"_"+instructionName+"_"+spawnStage+"_reg := io."+BNode.WrRD_spawn_valid+"_"+instructionName+"_"+spawnStage+"_i;\n");				 		
				 toFile.nrTabs--;
				 toFile.UpdateContent(filePlugin,"}\n");	
			 }
			 String spawnLogicBody = "";
			 toFile.UpdateContent(filePlugin," when("+BNode.ISAX_fire2_regF_reg+") {\n");
			 toFile.nrTabs++;
			 String priority = "";
			 for(String instructionName :  op_stage_instr.get(BNode.WrRD_spawn).get(spawnStage)) {					
				 spawnLogicBody += language.CreateSpawnLogicWrRD(instructionName, spawnStage,priority);
				 if(!priority.isEmpty())
						priority += " || ";
					priority   += language.CreateNodeName(BNode.Mem_spawn_valid, spawnStage,instructionName).replace("_i", "_reg");
			 }
			 spawnLogicBody += "when("+BNode.ISAX_sum_spawn_regF_s+"===1) {\n"
						    + toFile.tab + BNode.ISAX_fire2_regF_reg+" := False\n"
					        + "}\n"; 
			 if(op_stage_instr.get(BNode.WrRD_spawn).get(spawnStage).size()>1) 
				 spawnLogicBody += "when("+BNode.ISAX_sum_spawn_regF_s+">1) {\n"
				 		    + toFile.tab +"execute.arbitration.haltByOther := True\n"
				 		    + "}\n";
			 toFile.UpdateContent(filePlugin,spawnLogicBody);
			 language.CloseBrackets();
		 }
		 
		 
	 
	 }
	 

	 /** Function for updating configuration file and adding new instructions
	  * 
	  */
	 private void IntegrateISAX_UpdateConfigFile() {

		 String filePath = pathRISCV+"/demo"+"/"+baseConfig + ".scala";
		 System.out.println("INTEGRATE. Updating "+filePath);
		 
		 String lineToBeInserted = "new "+extension_name+"(),";
		 
		 toFile.UpdateContent(filePath,"plugins = List(", new ToWrite(lineToBeInserted,false,true,""));
		 toFile.ReplaceContent(filePath,"new MulPlugin,", new ToWrite("//new MulPlugin, // SCAIEV Paper",false,true,""));
		 toFile.ReplaceContent(filePath,"new MulPlugin,", new ToWrite("//new DivPlugin, // SCAIEV Paper",false,true,""));
		 toFile.ReplaceContent(filePath,"earlyBranch = false,",new ToWrite("earlyBranch = true, // SCAIEV Paper",false,true,""));
	 }
	 
	 private void IntegrateISAX_UpdateMemFile() {
		 String filePath = pathRISCV+"/plugin"+"/"+"DBusSimplePlugin.scala";
		 System.out.println("INTEGRATE. Updating "+filePath);
		 String lineToBeInserted = "";
		 boolean memory_required = false;
		 if(this.op_stage_instr.containsKey(BNode.WrMem) || this.op_stage_instr.containsKey(BNode.RdMem))
			 memory_required = true;
		 if (this.op_stage_instr.containsKey(BNode.WrMem_spawn) || this.op_stage_instr.containsKey(BNode.RdMem_spawn)) {
			 lineToBeInserted = "when(stages.dropWhile(_ != execute).map(_.arbitration.isValid).orR ) {";
			 toFile.ReplaceContent(filePath,"when(stages.dropWhile(_ != execute).map(_.arbitration.isValid).orR", new ToWrite(lineToBeInserted,false,true,""));
		 } else if(memory_required){
			 lineToBeInserted = "when(stages.dropWhile(_ != execute).map(_.arbitration.isValid).orR){";
			 toFile.ReplaceContent(filePath,"when(!stages.dropWhile(_ != execute).map(_.arbitration.isValid).orR", new ToWrite(lineToBeInserted,false,true,""));		 
		 }
			 
		 
	 }

	 /** Function for updating arbitration for WrRD Spawn
	  * 
	  */
	 private void IntegrateISAX_UpdateRegFforSpawn() {
		 if(this.op_stage_instr.containsKey(BNode.WrRD_spawn)) {
			 String filePath = pathRISCV+"/plugin"+"/"+ "RegFilePlugin.scala";
			 System.out.println("INTEGRATE. Updating "+filePath);
			 String lineToBeInserted = "regFileWrite.valid :=  output(REGFILE_WRITE_VALID) && arbitration.isFiring || arbitration.isRegFSpawn // Logic updated for Spawn WrRD ISAX";
		
			 toFile.ReplaceContent(filePath,"regFileWrite.valid :=", new ToWrite(lineToBeInserted,false,true,""));	
		 }
	 }
	 
	 /** Function for updating RegF for  WrRD Spawn
	  * 
	  */
	 private void IntegrateISAX_UpdateArbitrationFile() {
			 if(this.op_stage_instr.containsKey(BNode.WrRD_spawn)) {
			 String filePath =  pathRISCV+"/Stage.scala";
			 System.out.println("INTEGRATE. Updating "+filePath);
			 String lineToBeInserted = " val isRegFSpawn     = False    //Inform if an instruction using the spawn construction is ready to write its result in the RegFile\n"
									     +"val IDSpawn         = UInt(2 bits)\n"
									     +"val isMemSpawn      = True\n";
			
			 toFile.UpdateContent(filePath,"val arbitration =", new ToWrite(lineToBeInserted,false,true,""));	
		 }
	 }
	 
	 /** Function for updating IFetch 
	  * 
	  */
	 private void IntegrateISAX_UpdateIFetchFile() {
		 String tab = toFile.tab;
		 String filePath = pathRISCV+"/plugin"+"/"+ "Fetcher.scala";
		 System.out.println("INTEGRATE. Updating "+filePath);
		 String lineToBeInserted ="";
		 if(this.op_stage_instr.containsKey(BNode.WrPC_spawn)  || (this.op_stage_instr.containsKey(BNode.WrPC) && this.op_stage_instr.get(BNode.WrPC).containsKey(0)) || (this.op_stage_instr.containsKey(BNode.RdPC) && this.op_stage_instr.get(BNode.RdPC).containsKey(0))  ) {
			 lineToBeInserted = "var jumpInFetch: JumpInFetch = null\n"
					  +"override def createJumpInFetchInterface(): JumpInFetch = {\n"
					  +tab+"assert(jumpInFetch == null)\n"
					  +tab+"jumpInFetch = JumpInFetch()\n"
					  +tab+"jumpInFetch\n"
					  +"}";
			 toFile.UpdateContent(filePath,"class FetchArea", new ToWrite(lineToBeInserted,false,true,"",true));	
			 
			 lineToBeInserted =  "val predictionBuffer : Boolean = true) extends Plugin[VexRiscv] with JumpService with IBusFetcher with JumpInFetchService{ ";
			 toFile.ReplaceContent(filePath,"val predictionBuffer : Boolean = true) extends Plugin[VexRiscv] with JumpService with IBusFetcher{", new ToWrite(lineToBeInserted,false,true,""));	
		 }
		 if(this.op_stage_instr.containsKey(BNode.WrPC_spawn)  || (this.op_stage_instr.containsKey(BNode.WrPC) && this.op_stage_instr.get(BNode.WrPC).containsKey(0)) ) {
			 lineToBeInserted = "when (jumpInFetch.update_PC){\n"
					 			+tab+"correction := True\n"
					 		    +tab+"pc := jumpInFetch.target_PC\n"
					 		    +tab+"flushed := False\n"       
					 		   +"}\n";
			 toFile.UpdateContent(filePath,"when(booted && (output.ready || correction || pcRegPropagate)){", new ToWrite(lineToBeInserted,false,true,"",true));	
			 
		 }
		 
		 if( this.op_stage_instr.containsKey(BNode.RdPC) && this.op_stage_instr.get(BNode.RdPC).containsKey(0) ) {
			 lineToBeInserted = "jumpInFetch.current_pc := pc;";
			 toFile.UpdateContent(filePath,"output.payload := pc", new ToWrite(lineToBeInserted,false,true,""));
			 
		 }
	 }
	 
	 /** Function for updating Services 
	  * 
	  */
	 private void IntegrateISAX_UpdateServiceFile() {
		 String filePath =  pathRISCV+"/Services.scala";
		 String tab = toFile.tab;
		 System.out.println("INTEGRATE. Updating "+filePath);
		 String lineToBeInserted ="";
		 if(this.op_stage_instr.containsKey(BNode.WrPC_spawn)  || (this.op_stage_instr.containsKey(BNode.WrPC) && this.op_stage_instr.get(BNode.WrPC).containsKey(0))  || (this.op_stage_instr.containsKey(BNode.RdPC) && this.op_stage_instr.get(BNode.RdPC).containsKey(0))  ) {
			 lineToBeInserted ="case class JumpInFetch() extends Bundle {\n"
			  +tab+"val update_PC  = Bool\n"
			  +tab+"val target_PC =  UInt(32 bits)\n"
			  +tab+"val current_pc = UInt(32 bits)\n"
			  +"}\n"
			  +"trait JumpInFetchService {\n"
			  +tab+"def createJumpInFetchInterface() : JumpInFetch\n"
			  +"}\n";
			 toFile.UpdateContent(filePath,"trait JumpService{", new ToWrite(lineToBeInserted,false,true,"",true));
		 }

	 }
	 
	 private boolean ContainsOpInStage(String operation, int stage) {
		 return op_stage_instr.containsKey(operation) && op_stage_instr.get(operation).containsKey(stage);
	 }
	 
	 private void ConfigVex() {
	 	this.PopulateNodesMap(this.vex_core.maxStage);
	 	Module newModule = new Module();
	 	newModule.name = extension_name;
	 	newModule.file = pathRISCV + "/plugin"+extension_name+".scala";
	 	this.fileHierarchy.put(extension_name,newModule );
	 	stages.put(1, "decode");
	 	stages.put(2, "execute");
	 	stages.put(3, "memory");
	 	int spawnStage = this.vex_core.maxStage+1;
	 	this.PutNode(32, true, "UInt", "jumpInFetch.target_PC", "", BNode.WrPC,0);
	 	this.PutNode(1, true,  "Bool", "jumpInFetch.update_PC", "", BNode.WrPC_valid,0);
	 	this.PutNode(32, true, "UInt", "jumpInFetch.target_PC", "", BNode.WrPC_spawn,spawnStage);
	 	this.PutNode(1, true,  "Bool", "jumpInFetch.update_PC", "", BNode.WrPC_spawn_valid,spawnStage);
	 	for(int stage : stages.keySet()) {
	 		this.PutNode(32, false, "Bits", stages.get(stage)+".input(INSTRUCTION)", stages.get(stage), BNode.RdInstr,stage);
	 		this.PutNode(32, false, "UInt", stages.get(stage)+".input(PC)", stages.get(stage),BNode.RdPC,stage);
	 		this.PutNode(1,  false, "Bool", stages.get(stage)+".arbitration.isStuck", stages.get(stage), BNode.RdStall,stage);
	 		this.PutNode(1,  false, "Bool", stages.get(stage)+".arbitration.isFlushed", stages.get(stage), BNode.RdFlush,stage);
	 		this.PutNode(1,  true,  "Bool", stages.get(stage)+".arbitration.haltByOther", stages.get(stage), BNode.WrStall,stage);
	 		this.PutNode(1,  false, "Bool", stages.get(stage)+".input(IS_ISAX) && "+stages.get(stage)+".arbitration.isValid && !"+stages.get(stage)+".arbitration.removeIt;", stages.get(stage), BNode.RdIValid,stage);	 		
	 	}
	 	for(int stage = 1;stage<4;stage++) {
	 		this.PutNode(32, false, "Bits", stages.get(stage)+".input(RS1)", stages.get(stage), BNode.RdRS1,stage);
	 		this.PutNode(32, false, "Bits", stages.get(stage)+".input(RS2)", stages.get(stage), BNode.RdRS2,stage);
	 		this.PutNode(32, true,  "UInt", "jumpInterface_"+stage+".payload", "memory", BNode.WrPC,stage);
	 		this.PutNode(32, true,  "Bool", "", "memory", BNode.WrPC_valid,stage);
	 	}
	 
	 	int stageWrRD = this.vex_core.GetNodes().get(BNode.WrRD).GetLatest();
	 	this.PutNode(32, true, "Bits", stages.get(stageWrRD)+".output(REGFILE_WRITE_DATA)", stages.get(stageWrRD), BNode.WrRD,stageWrRD);
	 	this.PutNode(1,  true, "Bool", stages.get(stageWrRD)+".output(REGFILE_WRITE_VALID)", stages.get(stageWrRD), BNode.WrRD_valid,stageWrRD);
	 	this.PutNode(5,  true, "Bits", stages.get(stageWrRD)+".output(INSTRUCTION)", stages.get(stageWrRD), BNode.WrRD_addr,stageWrRD);
	 	
	 	this.PutNode(32, true, "Bits", stages.get(stageWrRD)+".output(REGFILE_WRITE_DATA)", stages.get(stageWrRD), BNode.WrRD_spawn,spawnStage);
	 	this.PutNode(1,  true, "Bool", stages.get(stageWrRD)+".output(REGFILE_WRITE_VALID)", stages.get(stageWrRD), BNode.WrRD_spawn_valid,spawnStage);
	 	this.PutNode(5,  true, "Bits", stages.get(stageWrRD)+".output(INSTRUCTION)", stages.get(stageWrRD), BNode.WrRD_spawn_addr,spawnStage);

	 	
	 	int stageMem = this.vex_core.GetNodes().get(BNode.RdMem).GetLatest();
	 	this.PutNode(32, false, "Bits", stages.get(stageMem)+"", stages.get(stageMem), BNode.RdMem,stageMem);
	 	this.PutNode(1,  false, "Bool", stages.get(stageMem)+"", stages.get(stageMem), BNode.RdMem_valid,stageMem);
	 	this.PutNode(32, true,  "Bits", stages.get(stageMem)+"", stages.get(stageMem), BNode.WrMem,stageMem);
	 	this.PutNode(1,  true,  "Bool", stages.get(stageMem)+"", stages.get(stageMem), BNode.Mem_valid,stageMem);
	 	this.PutNode(32, true,  "UInt", stages.get(stageMem)+"", stages.get(stageMem), BNode.Mem_addr,stageMem);
	 	
	 	this.PutNode(32, false, "Bits", stages.get(stageMem)+"", stages.get(stageMem), BNode.RdMem_spawn,spawnStage);
	 	this.PutNode(1, false, "Bool", stages.get(stageMem)+"", stages.get(stageMem), BNode.RdMem_spawn_valid,spawnStage);
	 	this.PutNode(32, true,  "Bits", stages.get(stageMem)+"", stages.get(stageMem), BNode.WrMem_spawn,spawnStage);
	 	this.PutNode(1, false, "Bool", stages.get(stageMem)+"", stages.get(stageMem),BNode.WrMem_spawn_valid,spawnStage);
	 	this.PutNode(1,  true, "Bool", stages.get(stageMem)+"", stages.get(stageMem), BNode.Mem_spawn_valid,spawnStage);
	 	this.PutNode(32, true,  "UInt", stages.get(stageMem)+"", stages.get(stageMem), BNode.Mem_spawn_addr,spawnStage);
     }

}
