package scaiev.backend;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;


class NodeProperties {
	public int size; 
	public boolean input; 
	public String data_type;
	public String assignSignal; 
	public String assignModule; 
	public String name;	
	public int stage; // RS has different assign signals in different stages
}

class Module {
	public String name; 
	public String parentName;
	public String file; 
	public String interfaceName; 
	public String interfaceFile;	
}


public class CoreBackend {
	public HashMap <String,HashMap<Integer, NodeProperties>> nodes = new HashMap <String, HashMap<Integer, NodeProperties>>(); // <NodeName,Module>
	public HashMap <String, Module> fileHierarchy = new HashMap <String, Module>(); // <moduleName,Module>
	public HashMap <Integer, String> stages = new HashMap <Integer, String>(); // <stageNr,name>, useful for Vex
	public String topModule;
	
	public void PopulateNodesMap(int maxStage) {
		Set<String> backendNodes = BNode.GetAllBackNodes();
		for(String node:  backendNodes ) {
			HashMap<Integer, NodeProperties> newStageNode = new HashMap<Integer, NodeProperties>();
			for(int i=0;i<=maxStage;i++) {
				NodeProperties prop = new NodeProperties();
				newStageNode.put(i, prop);
			}
			nodes.put(node, newStageNode);
		}
		
		// Default nodes for spawn
		this.PutNode(1, true,  "","","",BNode.ISAX_fire2_regF_reg,maxStage+1);
		this.PutNode(1, true,  "","","",BNode.ISAX_fire_regF_reg,maxStage+1);
		this.PutNode(1, true,  "","","",BNode.ISAX_fire_regF_s,maxStage+1);
		
		this.PutNode(1, true,  "","","",BNode.ISAX_fire2_mem_reg,maxStage+1);
		this.PutNode(1, true,  "","","",BNode.ISAX_fire_mem_reg,maxStage+1);
		this.PutNode(1, true,  "","","",BNode.ISAX_fire_mem_s,maxStage+1);
		
		this.PutNode(1, true,  "","","",BNode.ISAX_sum_spawn_regF_s,maxStage+1);
		this.PutNode(1, true,  "","","",BNode.ISAX_sum_spawn_mem_s,maxStage+1);
		
		this.PutNode(1, true,  "","","",BNode.ISAX_spawnStall_regF_s,maxStage+1);
		this.PutNode(1, true,  "","","",BNode.ISAX_spawnStall_mem_s,maxStage+1);
	}
	
	public boolean IsNodeInStage(String node, int stage) {
		if(nodes.containsKey(node) && nodes.get(node).containsKey(stage))
			return true; 
		else 
			return false; 
	}
	public int NodeSize(String node, int stage) {
		return nodes.get(node).get(stage).size;
	}	
	public boolean NodeIn(String node,int stage) {
		return nodes.get(node).get(stage).input;
	}	
	public String NodeDataT(String node,int stage) {
		return nodes.get(node).get(stage).data_type;
	}
	public String NodeAssign(String node,int stage) {
		return nodes.get(node).get(stage).assignSignal;
	}	
	/**
	 * 
	 * @param node
	 * @param stage
	 * @return
	 */
	public String NodeAssignM(String node,int stage) {
		return nodes.get(node).get(stage).assignModule;
	}
	public String NodeName(String node,int stage) {
		return nodes.get(node).get(stage).name;
	}
	public int NodeStage(String node,int stage) {
		return nodes.get(node).get(stage).stage;
	}
	public String ModName(String module) {
		return fileHierarchy.get(module).name;
	}
	public String ModParentName(String module) {
		return fileHierarchy.get(module).parentName;
	}
	public String ModFile(String module) {
		return fileHierarchy.get(module).file;
	}
	public String ModInterfName(String module) {
		return fileHierarchy.get(module).interfaceName;
	}
	public String ModInterfFile(String module) {
		return fileHierarchy.get(module).interfaceFile;
	}
	
	
	public void PutNode(int size,  boolean input, String data_type, String assignSignal, String assignModule, String name, int stage) {
		NodeProperties node = new NodeProperties();
		node.size = size;
		node.input = input;
		node.data_type = data_type;
		node.assignSignal = assignSignal;
		node.assignModule = assignModule;
		node.name = name;
		node.stage = stage;
		if(nodes.containsKey(name))
			nodes.get(name).put(stage, node);	
		else {
			nodes.put(name, new HashMap<Integer, NodeProperties>());
			nodes.get(name).put(stage, node);	
		}
	}
	
	public void PutModule(String interfaceFile,  String interfaceName, String file, String parentName, String name) {
		Module module = new Module();
		module.interfaceFile = interfaceFile;
		module.interfaceName = interfaceName;
		module.file = file;
		module.parentName = parentName;
		module.name = name;
		fileHierarchy.put(name,module);	
		if(parentName.equals(""))
			topModule = name;
	}
	


}
