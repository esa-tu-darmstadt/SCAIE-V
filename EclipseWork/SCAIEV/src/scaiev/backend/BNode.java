package scaiev.backend;

import java.util.HashSet;

import scaiev.frontend.FNode;

/***********************
 * 
 * Backend supported nodes
 *
 */
public class BNode extends FNode{

	public static String WrRD_valid = "wrRD_valid"; 
	public static String WrRD_addr = "wrRD_addr";
	public static String Mem_valid = "mem_valid"; 
	public static String Mem_addr = "mem_addr";
	public static String RdMem_valid = "rdMem_valid"; // valdir ead data
	public static String WrPC_valid = "wrPC_valid"; 
	public static String WrPC_spawn_valid = "wrPC_spawn_valid";
	public static String WrPC_spawn = "wrPC_spawn";
	public static String RdImm ="rdImm";  // TODO not yet supported  by frontend
	public static String WrRD_spawn_valid = "wrRD_spawn_valid"; 
	public static String WrRD_spawn = "wrRD_spawn";
	public static String WrRD_spawn_addr = "wrRD_spawn_addr";
	public static String Mem_spawn_valid = "mem_spawn_valid"; 
	public static String Mem_spawn_addr = "mem_spawn_addr";
	public static String RdMem_spawn = "rdMem_spawn";
	public static String RdMem_spawn_valid = "rdMem_spawn_valid";
	public static String WrMem_spawn = "wrMem_spawn";
	public static String WrMem_spawn_valid = "wrMem_spawn_valid";
	public static String commited_rd_spawn_valid = "commited_rd_spawn_valid";
	public static String commited_rd_spawn = "commited_rd_spawn";
	
	// Local Signal Names used in all cores for logic  of spawn
	public static String ISAX_fire2_regF_reg = "isax_fire2_regF_reg";
	public static String ISAX_fire_regF_reg = "isax_fire_regF_reg";	
	public static String ISAX_fire_regF_s = "isax_fire_regF_s";	
	
	public static String ISAX_fire2_mem_reg = "isax_fire2_mem_reg";
	public static String ISAX_fire_mem_reg = "isax_fire_mem_reg";
	public static String ISAX_fire_mem_s = "isax_fire_mem_s";
	public static String ISAX_sum_spawn_regF_s = "isax_sum_spawn_regF_s";
	public static String ISAX_sum_spawn_mem_s = "isax_sum_spawn_mem_s";
	public static String ISAX_spawnStall_regF_s = "isax_spawnStall_regF_s";
	public static String ISAX_spawnStall_mem_s = "isax_spawnStall_mem_s";
	
	public static HashSet<String>  GetAllBackNodes(){
		HashSet<String> bnodes = FNode.GetAllFrontendNodes();
		bnodes.add(WrRD_valid);
		bnodes.add(WrRD_addr);
		bnodes.add(Mem_valid);
		bnodes.add(Mem_addr);
		bnodes.add(RdMem_valid);
		bnodes.add(WrPC_valid);
		bnodes.add(WrPC_spawn_valid);
		bnodes.add(WrPC_spawn);
		bnodes.add(WrRD_spawn_valid);
		bnodes.add(WrRD_spawn_addr);
		bnodes.add(WrRD_spawn);
		bnodes.add(Mem_spawn_valid);
		bnodes.add(Mem_spawn_addr);
		bnodes.add(RdMem_spawn);
		bnodes.add(RdMem_spawn_valid);
		bnodes.add(WrMem_spawn);
		bnodes.add(WrMem_spawn_valid);
		bnodes.add(commited_rd_spawn_valid);
		bnodes.add(ISAX_fire2_regF_reg);
		bnodes.add(ISAX_fire_regF_reg);
		bnodes.add(ISAX_fire2_mem_reg);
		bnodes.add(ISAX_fire_mem_reg);
		bnodes.add(ISAX_sum_spawn_regF_s);
		bnodes.add(ISAX_sum_spawn_mem_s);
		bnodes.add(ISAX_spawnStall_regF_s);
		bnodes.add(ISAX_spawnStall_mem_s);
		return bnodes;
	}
	
}
