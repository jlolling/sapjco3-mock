package com.sap.conn.jco;

public class JCoRepository {
	
	public JCoFunctionTemplate getFunctionTemplate(String name) {
		return new JCoFunctionTemplate(name);
	}

}
