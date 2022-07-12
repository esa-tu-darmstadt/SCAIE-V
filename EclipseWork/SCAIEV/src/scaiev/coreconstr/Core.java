package scaiev.coreconstr;


import java.util.HashMap;

public class Core {
	private Boolean debug = true;
	private HashMap<String,Node> nodes = new HashMap<String,Node>();
	private Boolean flush;
	private Boolean datahaz;
	private int[] stall;
	private String name; 
	public int maxStage;
	
	public Core (HashMap<String,Node> nodes,Boolean flush, Boolean datahaz, int[] stall, String name) {
		this.datahaz  = datahaz;
		this.flush = flush;
		this.nodes = nodes;
		this.stall = stall;
		this.name = name;
	}
	
	public Core () {}
	
	@Override
    public String toString() { 
        return String.format("INFO. Core. Core named:" +name+ " with nodes = "+nodes.toString()); 
    } 
	 
	public void PutName(String name) {
		this.name = name;		
	}
	
	public void PutDatahaz(Boolean datahaz) {
		this.datahaz = datahaz;		
	}
	
	public void PutFlush(Boolean flush) {
		this.flush = flush;		
	}
	
	public void PutNodes(HashMap<String,Node> nodes) {
		this.nodes = nodes;		
	}
	
	public void PutStall(int[] stall) {
		this.stall = stall;		
	}
	
	public String  GetName() {
		return name;	
	}
	
	public HashMap<String,Node>  GetNodes() {
		return nodes;	
	}
	
	public Boolean  GetFlush() {
		return flush;	
	}
	
	public Boolean  GetDatahaz() {
		return datahaz;	
	}
	
	public int []  GetStall() {
		return stall;	
	}
	
	
}
