package com.sap.conn.jco;

public class JCoFunctionTemplate {
	
	private String name;
	
	public JCoFunctionTemplate(String name) {
		this.name = name;
	}
	
	public JCoFunction getFunction() {
		return new JCoFunction(name);
	}

}
