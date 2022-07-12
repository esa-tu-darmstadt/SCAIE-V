package scaiev.frontend;

import java.util.HashSet;

/***********************
 * 
 * Frontend supported nodes
 *
 */
public class FNode{

	public static String WrRD = "wrRD";
	public static String WrMem = "wrMem"; // input data to core
	public static String RdMem = "rdMem"; // output data from core
	public static String WrPC = "wrPC";	
	public static String RdPC = "rdPC";	
	public static String RdRS1 = "rdRS1";
	public static String RdRS2 ="rdRS2";
	public static String RdInstr = "rdInstr" ;
	public static String RdIValid = "rdIValid"; 
	public static String RdStall = "rdStall";
	public static String WrStall = "wrStall";
	public static String RdFlush = "rdFlush";
	public static String WrFlush = "wrFlush";
	
	

	public static HashSet<String>  GetAllFrontendNodes(){
	 HashSet<String> fnodes = new HashSet<String>();
		fnodes.add(WrRD);
		fnodes.add(WrMem);
		fnodes.add(RdMem);
		fnodes.add(WrPC);
		fnodes.add(RdPC);
		fnodes.add(RdRS1);
		fnodes.add(RdRS2);
		fnodes.add(RdInstr);
		fnodes.add(RdIValid);
		fnodes.add(RdStall);
		fnodes.add(WrStall);
		fnodes.add(RdFlush);
		fnodes.add(WrFlush);
		return fnodes;
	}
}
