package com.sap.conn.jco;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JCoTable {
	
	private int currentIndex = -1;
	private List<Map<String, String>> resultset = new ArrayList<>();
	private String name = null;
	
	public JCoTable(String name) {
		this.name = name;
	}
	
	public void appendRows(int count) {
		for (int i = 0; i < count; i++) {
			Map<String, String> entry = new HashMap<>();
			resultset.add(entry);
		}
	}
	
	public void firstRow() {
		currentIndex = 0;
	}
	
	public void nextRow() {
		currentIndex++;
	}
	
	public void setValue(String name, String value) {
		resultset.get(currentIndex).put(name, value);
	}
	
	public int getNumRows() {
		return resultset.size();
	}
	
	public String getString(String name) {
		return resultset.get(currentIndex).get(name);
	}
	
	@Override
	public int hashCode() {
		return name.hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof JCoTable) {
			return ((JCoTable) o).name.equals(name);
		} else {
			return false;
		}
	}

}
