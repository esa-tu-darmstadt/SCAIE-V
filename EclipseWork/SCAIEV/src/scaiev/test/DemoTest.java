package scaiev.test;

import scaiev.SCAIEV;
import scaiev.frontend.SCAIEVInstr;
import scaiev.frontend.FNode;

public class DemoTest {
	public static void main(String[] args) {
		
		
		SCAIEV shim = new SCAIEV();
		// Dummy instruction reading 2 operands just as an example of how to use the tool
		SCAIEVInstr setaddr  = shim.addInstr("SETADDRGEN","-------", "000", "0001011", "I");
		setaddr.PutSchedNode(FNode.RdRS1, 0);  
		setaddr.PutSchedNode(FNode.RdRS2, 0);  
		// ... other instructions
		shim.Generate("Piccolo");
	
	}
}
