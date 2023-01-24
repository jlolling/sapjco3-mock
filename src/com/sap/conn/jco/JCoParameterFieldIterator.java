package com.sap.conn.jco;

import java.util.List;

public class JCoParameterFieldIterator {
	
	private List<JCoField> parameters = null;
	private int index = 0;
	
	public JCoParameterFieldIterator(List<JCoField> list) {
		this.parameters = list;
	}
	
	public boolean hasNextField() {
		return index < parameters.size();
	}
	
	public JCoField nextField() {
		return parameters.get(index++);
	}

}
