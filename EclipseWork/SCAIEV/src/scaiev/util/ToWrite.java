package scaiev.util;


public class ToWrite
{
	public String text = ""; 
	public boolean prereq = false; 
	public boolean prereq_val = true;  // if prereq with previous condition, the start value should be false. If prereq with no previous condition = run once, start value should be true. If no prereq, set to whatever
	public String prereq_text = ""; 
	public boolean before; 
	public boolean replace;
	public String in_module;
	boolean found_module = true;
	
	public ToWrite(String text, boolean before, String in_module) {
		this.text = text;	
		this.before = before;
		this.replace = false;
		this.in_module = in_module;
		if(!in_module.contentEquals(""))
			this.found_module = false;	
		
	}
	
	public ToWrite(String text, boolean prereq,boolean prereq_val,String prereq_text, boolean before, String in_module) {
		this.text = text;
		this.prereq = prereq;
		this.prereq_val = prereq_val;
		this.prereq_text = prereq_text; 	
		this.before = before;
		this.replace = false;
		this.in_module = in_module;
		if(!in_module.contentEquals(""))
			this.found_module = false;	
		
	}
	
	public ToWrite(String text, boolean prereq,boolean prereq_val,String prereq_text, String in_module) {
		this.text = text;
		this.prereq = prereq;
		this.prereq_val = prereq_val;
		this.prereq_text = prereq_text; 	
		this.before = false;
		this.replace = false;
		this.in_module = in_module;
		if(!in_module.contentEquals(""))
			this.found_module = false;	
		
	}
	
	public ToWrite(String text, boolean prereq,boolean prereq_val,String prereq_text, boolean before) {
		this.text = text;
		this.prereq = prereq;
		this.prereq_val = prereq_val;
		this.prereq_text = prereq_text; 	
		this.before = before;
		this.replace = false;
		this.in_module = "";
		this.found_module = true;
		
	}
	
	public ToWrite(String text, boolean prereq,boolean prereq_val,String prereq_text) {
		this.text = text;
		this.prereq = prereq;
		this.prereq_val = prereq_val;
		this.prereq_text = prereq_text; 	
		this.before = false;
		this.in_module = "";
		this.found_module = true;
		
	}
	
	public void AllignText(String allignment) {
		String newText = allignment + this.text;
		newText = newText.replaceAll("(\\r|\\n)","\n"+allignment);
		this.text = newText;
		
	}
	@Override
	public String toString() {
		return "Text to be added: "+text+". Prereq requested: "+prereq+". Text to be added before greped line: "+before+". In module "+in_module;
	}
}