package scaiev.frontend;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import scaiev.backend.BNode;
import scaiev.drc.DRC;
import scaiev.util.Lang;

public class SCAIEVInstr {
	private String encodingOp;
	private String encodingF3;
	private String encodingF7;
	private String instr_name;
	private String instr_type;
	private HashMap<String, Scheduled> sched_nodes = new HashMap<String, Scheduled>();

	
	// Constructors
	public SCAIEVInstr(String instr_name, String encodingF7, String encodingF3,String encodingOp, String instrType) {
		this.instr_name = instr_name;
		this.encodingOp = encodingOp;
		this.encodingF3 = encodingF3;
		this.encodingF7 = encodingF7;
		this.instr_type  = instrType;
		DRC.CheckEncoding(this);
	}	
	public SCAIEVInstr(String instr_name) {
		this.instr_name = instr_name;
		this.encodingOp = "";
		this.encodingF3 = "";
		this.encodingF7 = "";
		this.instr_type  = "R";
	}
	
	public void ConvertToBackend(int spawnStage) {
		if(sched_nodes.containsKey(FNode.WrRD) && sched_nodes.get(FNode.WrRD).GetStartCycle()==spawnStage)  { 
			Scheduled oldSched = sched_nodes.get(FNode.WrRD);
			sched_nodes.remove(FNode.WrRD);
			sched_nodes.put(BNode.WrRD_spawn,oldSched);
		}
		/*
		if(sched_nodes.containsKey(FNode.WrRD) && sched_nodes.get(FNode.WrRD).GetAddrInterf())
			sched_nodes.put(BNode.WrRD_addr,sched_nodes.get(FNode.WrRD));
		if(sched_nodes.containsKey(FNode.WrRD) && sched_nodes.get(FNode.WrRD).GetValidInterf())
			sched_nodes.put(BNode.WrRD_valid,sched_nodes.get(FNode.WrRD));
		*/
		if(sched_nodes.containsKey(FNode.WrMem) && sched_nodes.get(FNode.WrMem).GetStartCycle()==spawnStage)  { 
			Scheduled oldSched = sched_nodes.get(FNode.WrMem);
			sched_nodes.remove(FNode.WrMem);
			sched_nodes.put(BNode.WrMem_spawn,oldSched);
		}
		if(sched_nodes.containsKey(FNode.RdMem) && sched_nodes.get(FNode.RdMem).GetStartCycle()==spawnStage)  { 
			Scheduled oldSched = sched_nodes.get(FNode.RdMem);
			sched_nodes.remove(FNode.RdMem);
			sched_nodes.put(BNode.RdMem_spawn,oldSched);
		}
		/*
		if(sched_nodes.containsKey(FNode.RdMem) && sched_nodes.get(FNode.RdMem).GetAddrInterf())
			sched_nodes.put(BNode.Mem_addr,sched_nodes.get(FNode.RdMem));
		if(sched_nodes.containsKey(FNode.WrMem) && sched_nodes.get(FNode.WrMem).GetAddrInterf())
			sched_nodes.put(BNode.Mem_addr,sched_nodes.get(FNode.WrMem));
		if(sched_nodes.containsKey(FNode.RdMem) && sched_nodes.get(FNode.RdMem).GetValidInterf())
			sched_nodes.put(BNode.Mem_valid,sched_nodes.get(FNode.RdMem));
		if(sched_nodes.containsKey(FNode.WrMem) && sched_nodes.get(FNode.WrMem).GetValidInterf())
			sched_nodes.put(BNode.Mem_valid,sched_nodes.get(FNode.WrMem));
		if(sched_nodes.containsKey(FNode.WrMem) && sched_nodes.get(FNode.WrMem).GetAddrInterf())
			sched_nodes.put(BNode.Mem_addr,sched_nodes.get(FNode.WrMem));
		*/
		if(sched_nodes.containsKey(FNode.WrPC) && sched_nodes.get(FNode.WrPC).GetStartCycle()==spawnStage)  { 
			Scheduled oldSched = sched_nodes.get(FNode.WrPC);
			sched_nodes.remove(FNode.WrPC);
			sched_nodes.put(BNode.WrPC_spawn,oldSched);
		}
	}
	public void PutSchedNode( String node_name, Scheduled new_sched_node) {
		sched_nodes.put(node_name, new_sched_node);
	}
	
	public void PutSchedNode(String node_name, int start_cycle, Boolean address_interf,Boolean enable_interf,int delay) {
		Scheduled new_scheduled = new Scheduled(start_cycle,address_interf,enable_interf,delay);
		sched_nodes.put(node_name, new_scheduled);
	}
	
	public void PutSchedNode(String node_name, int start_cycle) {
		Scheduled new_scheduled = new Scheduled(start_cycle);
		sched_nodes.put(node_name, new_scheduled);
	}
	
	
	public void SetEncoding(String encodingF7, String encodingF3,String encodingOp, String instrType) {		 
		 this.encodingOp = encodingOp;
		 this.encodingF3 = encodingF3;
		 this.encodingF7 = encodingF7;
		 this.instr_type  = instrType;
		 DRC.CheckEncoding(this);
		 System.out.println("INTEGRATE. Encoding updated. Op Codes F7|F3|Op: " +this.encodingF7+"|"+this.encodingF3+"|"+this.encodingOp+" and instruction type now is: "+this.instr_type);
	 }
	 
	/*
	 public void SetBypass(boolean bypass) {		 
		 this.bypass = bypass;
		 System.out.println("INFO: Bypass updated. Now bypass is: " +this.bypass);
	 }
	 */
	// Function to check if an instruction has scheduled a certain node (for exp if it writes PC)
	public Boolean HasNode(String node_name) {
		if(sched_nodes.containsKey(node_name))
			return true;
		else
			return false;
	}
	
	public Scheduled GetNode(String node_name) {
			return sched_nodes.get(node_name);
	}
	
	public String GetInstrType() {
		return instr_type;
	}
	

	public String GetEncodingString() {
		if(encodingOp.isEmpty())
			return "";
		else 
			return "key  = M\""+encodingF7+"----------"+encodingF3+"-----"+encodingOp+"\",";	// Return encoding
	}
	
	public String GetEncodingOp(String language) {		
		if(language.contains("vhdl"))
			return "\""+encodingOp+"\"";	
		else if(language.contains(Lang.Verilog)  || language.contains(Lang.Bluespec))
			return "7'b"+encodingOp;
		else 
			return encodingOp;
	}
	
	public String GetEncodingF7(String language) {		
		if(language.contains(Lang.VHDL))
			return "\""+encodingF7+"\"";	
		else if(language.contains(Lang.Verilog)  || language.contains(Lang.Bluespec))
			return "7'b"+encodingF7;
		else 
			return encodingF7;
	}
	
	public String GetEncodingF3(String language) {		
		if(language.contains(Lang.VHDL))
			return "\""+encodingF3+"\"";	
		else if(language.contains(Lang.Verilog)  || language.contains(Lang.Bluespec))
			return "3'b"+encodingF3;
		else 
			return encodingF3;
	}
	
	public HashMap<String, Scheduled> GetSchedNodes(){
		return  sched_nodes;		
	}
	
	public String GetName() {
		return instr_name;
	}
	
	
	@Override
    public String toString() { 
        return String.format("ISAX named:" +this.instr_name+ " with nodes: " + this.sched_nodes); 
    } 
	
}
