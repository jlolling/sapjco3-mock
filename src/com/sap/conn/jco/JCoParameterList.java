package com.sap.conn.jco;

import java.util.ArrayList;
import java.util.List;

public class JCoParameterList {

	private List<JCoField> map = new ArrayList<>();

	private JCoField getOrCreateField(String name) {
		for (JCoField f : map) {
			if (f.getName().equals(name)) {
				return f;
			}
		}
		JCoField f = new JCoField(name);
		map.add(f);
		return f;
	}

	private JCoField getField(String name) {
		for (JCoField f : map) {
			if (f.getName().equals(name)) {
				return f;
			}
		}
		return null;
	}

	public void setValue(String name, Object value) {
		getOrCreateField(name).setValue(value);
	}

	public Object getValue(String name) {
		JCoField f = getField(name);
		return f != null ? f.getValue() : null;
	}

	public String getString(String name) {
		Object v = getValue(name);
		return v != null ? String.valueOf(v) : null;
	}

	public JCoTable getTable(String name) {
		JCoField f = getOrCreateField(name);
		JCoTable t = (JCoTable) f.getValue();
		if (t == null) {
			t = new com.sap.conn.jco.rt.DefaultTable(name);
			f.setValue(t);
		}
		return t;
	}

	public JCoParameterFieldIterator getParameterFieldIterator() {
		return new JCoParameterFieldIterator(map);
	}

}
