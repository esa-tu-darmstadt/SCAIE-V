package scaiev;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import scaiev.backend.BNode;
import scaiev.backend.Orca;
import scaiev.backend.Piccolo;
import scaiev.backend.PicoRV32;
import scaiev.backend.VexRiscv;
import scaiev.coreconstr.Core;
import scaiev.coreconstr.CoreDatab;
import scaiev.drc.DRC;
import scaiev.frontend.FNode;
import scaiev.frontend.SCAIEVInstr;
import scaiev.frontend.Scheduled;


public class SCAIEV {	

	private CoreDatab coreDatab;   									// database with supported cores 
	private HashMap <String,SCAIEVInstr>  instrSet = new HashMap <String,SCAIEVInstr> ();	// database of requested Instructions
	private HashMap<String, HashMap<Integer,HashSet<String>>> op_stage_instr = new HashMap<String, HashMap<Integer,HashSet<String>>>();
    private String extensionName = "DEMO";

	
	
	public SCAIEV() {
		// Add currently supported nodes 
		Set<String> supported_nodes = FNode.GetAllFrontendNodes();
		

		System.out.println("SHIM. Instantiated shim layer. Supported nodes are: "+supported_nodes.toString());
		this.coreDatab = new CoreDatab(supported_nodes);
		coreDatab.ReadAvailCores("./Cores");
	}
	
	public void PrintCoreNames() {		
		System.out.println(coreDatab.GetCoreNames());
	}

	public void PrintCoreInfo(String coreName) {
		System.out.println(coreDatab.GetCore(coreName));
	}
	public void SetErrLevel (boolean errLevelHigh) {
		DRC.SetErrLevel(errLevelHigh);
	}
	public SCAIEVInstr addInstr(String name, String encodingF7, String encodingF3,String encodingOp, String instrType) {
		SCAIEVInstr newISAX = new SCAIEVInstr(name,encodingF7, encodingF3,encodingOp, instrType);
		instrSet.put(name,newISAX);
		return newISAX;		
	}
	public SCAIEVInstr addInstr(String name) {
		SCAIEVInstr newISAX = new SCAIEVInstr(name);
		instrSet.put(name,newISAX);
		return newISAX;		
	}
	
	public boolean Generate(String coreName) {
		boolean success = true; 
		// Select Core
		Core core = coreDatab.GetCore(coreName);
		
		
		// Create HashMap with <operations, <stages,instructions>>. Print it
		CreateOpStageInstr(core.maxStage+1,core.GetNodes().get(BNode.RdRS1).GetEarliest());		
		OpStageInstrToString(); 
		
		// Check errors
		DRC.CheckSchedErr(core,op_stage_instr);
		DRC.CheckEncPresent(instrSet);
		
		
		// Generate Interface
		if(coreName.contains("VexRiscv")) {
			VexRiscv coreInstance = new VexRiscv();	
			coreInstance.Generate(instrSet, op_stage_instr, this.extensionName, core);
		}
		
		if(coreName.contains("Piccolo")) {
			Piccolo coreInstance = new Piccolo();	
			coreInstance.Generate(instrSet, op_stage_instr, this.extensionName, core);
		}
		
		if(coreName.contains("ORCA")) {
			Orca coreInstance = new Orca();	
			coreInstance.Generate(instrSet, op_stage_instr, this.extensionName, core);
		}
		
		
		if(coreName.contains("PicoRV32")) {
			PicoRV32 coreInstance = new PicoRV32();	
			coreInstance.Generate(instrSet, op_stage_instr, this.extensionName, core);
		}
		return success;
	}
	
	private void CreateOpStageInstr(int spawnStage, int rdrsStage) {
		for(String instructionName : instrSet.keySet()) {
			SCAIEVInstr instruction = instrSet.get(instructionName);
			instruction.ConvertToBackend(spawnStage);
			HashMap<String, Scheduled> schedNodes = instruction.GetSchedNodes();
			for(String operation : schedNodes.keySet()) {
				int stage = schedNodes.get(operation).GetStartCycle();
				String putOperation = operation;
				if(!op_stage_instr.containsKey(putOperation)) 
					op_stage_instr.put(putOperation, new HashMap<Integer,HashSet<String>>()); 
				if(!op_stage_instr.get(putOperation).containsKey(stage))
					op_stage_instr.get(putOperation).put(stage, new HashSet<String>()); 
				op_stage_instr.get(putOperation).get(stage).add(instructionName);
			}
		}
		
		// TODO Improve this fast solution (adding an instruction which reads rs)
		if(op_stage_instr.containsKey(BNode.WrRD_spawn)) {
			op_stage_instr.get(BNode.RdRS1).get(rdrsStage).add("disaxkill");
			op_stage_instr.get(BNode.RdRS1).get(rdrsStage).add("disaxfence");
			SCAIEVInstr kill = addInstr("disaxkill","-------", "110", "0001011", "S");
			SCAIEVInstr fence = addInstr("disaxfence","-------", "111", "0001011", "S");
			kill.PutSchedNode(FNode.RdRS1,rdrsStage);  
			fence.PutSchedNode(FNode.RdRS1, rdrsStage);  
			instrSet.put("disaxkill", kill);
			instrSet.put("disaxfence", fence);
		}
			
	}
	
	private void OpStageInstrToString() {
		for(String operation : op_stage_instr.keySet())
			for(Integer stage : op_stage_instr.get(operation).keySet())
				System.out.println("INFO. SCAIEV. Operation = "+ operation+ " in stage = "+stage+ " for instruction/s: "+op_stage_instr.get(operation).get(stage).toString());
		
	}
	
	public void SetExtensionName(String name) {
		this.extensionName = name;
	}
}

