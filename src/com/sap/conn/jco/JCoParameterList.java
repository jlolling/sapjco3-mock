package com.sap.conn.jco;

import java.util.ArrayList;
import java.util.List;

public class JCoParameterList {
	
	private List<JCoField> map = new ArrayList<>();
	
	public void setValue(String name, Object value) {
		JCoField f = new JCoField(name);
		int index = map.indexOf(f);
		if (index == -1) {
			map.add(f);
		}
		f.setValue(value);
	}
	
	public JCoTable getTable(String name) {
		JCoField f = new JCoField(name);
		int index = map.indexOf(f);
		if (index == -1) {
			map.add(f);
			index = map.indexOf(f);
		}
		f = map.get(index);
		JCoTable t = (JCoTable) f.getValue();
		if (t == null) {
			t = new JCoTable(f.getName());
		}
		return t;
	}
	
	public JCoParameterFieldIterator getParameterFieldIterator() {
		return new JCoParameterFieldIterator(map);
	}
	
}
