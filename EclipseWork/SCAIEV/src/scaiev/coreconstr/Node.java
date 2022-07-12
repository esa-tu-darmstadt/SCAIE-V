package scaiev.coreconstr;

public class Node {
	int earliest_time;
	int latency;
	int latest_time;
	int expensive_time; //rd - timeslot starting with which it gets expensive // wr - timeslot untill which it was expensive 
    String name; 
    
	public Node(int earliest_time, int latency, int latest_time, int expensive_time, String name) {
		this.earliest_time = earliest_time;
		this.latency = latency;
		this.latest_time = latest_time;
		this.expensive_time = expensive_time;	
		this.name = name;
	}
	
	// Function for writing data to the constraints file in the format required. 
	@Override
    public String toString() { 
		String to_print;
		to_print = earliest_time + " "+latency+" "+latest_time+" "+expensive_time;
		return to_print;
	}
	
	public int GetLatest() {
		return this.latest_time;
	}
	
	public int GetEarliest() {
		return this.earliest_time;
	}
	
	public int GetLatency() {
		return this.latency;
	}
	
	public String GetName() {
		return this.name;
	}
}
