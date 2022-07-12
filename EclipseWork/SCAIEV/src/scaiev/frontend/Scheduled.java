package scaiev.frontend;



public class Scheduled {
	private int     start_cycle; 
	private Boolean address_interf;
	private Boolean valid_interf;
	
	public Scheduled (int start_cycle) {
		this.start_cycle = start_cycle;
		this.address_interf = false;
		this.valid_interf = false;
	}
	
	public Scheduled (int start_cycle,Boolean address_interf,Boolean valid_interf,int delay) {
		this.start_cycle = start_cycle;
		this.address_interf = address_interf;
		this.valid_interf = valid_interf;
	}
	

	
	public int GetStartCycle() {
		return start_cycle;
	}
	
	public void UpdateStartCycle(int newStartCycle) {
		start_cycle = newStartCycle;
	}
	public Boolean GetAddrInterf() {
		return address_interf;
	}
	
	public Boolean GetValidInterf() {
		return valid_interf;
	}
	

	
	@Override
    public String toString() { 
        return String.format("Scheduled nde. Node start cycle: " +this.start_cycle+ "address_interf: " +this.address_interf+" valid_interf: "+valid_interf); 
    } 
}
