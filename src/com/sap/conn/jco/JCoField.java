package com.sap.conn.jco;

public class JCoField {
	
	private String name;
	private Object value;
	
	public JCoField(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name;
	}
	
	public Object getValue() {
		return value;
	}
	
	public void setValue(Object value) {
		this.value = value;
	}
	
	@Override
	public int hashCode() {
		return name.hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof JCoField) {
			return ((JCoField) o).name.equals(name);
		} else {
			return false;
		}
	}

}
