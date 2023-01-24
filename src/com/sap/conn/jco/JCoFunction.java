package com.sap.conn.jco;

public class JCoFunction {
	
	private String name = null;
	
	public JCoFunction(String name) {
		this.name = name;
	}

	public JCoParameterList getImportParameterList() {
		return new JCoParameterList();
	}

	public JCoParameterList getTableParameterList() {
		return new JCoParameterList();
	}
	
	public void execute(JCoDestination dest) throws Exception {
		
	}
	
	public String getName() {
		return name;
	}

}
