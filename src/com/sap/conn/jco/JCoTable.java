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

	public String getName() {
		return name;
	}

	public void appendRows(int count) {
		for (int i = 0; i < count; i++) {
			Map<String, String> entry = new HashMap<>();
			resultset.add(entry);
		}
		// the pointer is set to the last appended row
		currentIndex = resultset.size() - 1;
	}

	public void appendRow() {
		appendRows(1);
	}

	public void firstRow() {
		currentIndex = 0;
	}

	/**
	 * Move to the next row
	 * @return true if the pointer is still within the table
	 */
	public boolean nextRow() {
		currentIndex++;
		return currentIndex < resultset.size();
	}

	public void setRow(int index) {
		currentIndex = index;
	}

	public int getRow() {
		return currentIndex;
	}

	public void deleteAllRows() {
		resultset.clear();
		currentIndex = -1;
	}

	public boolean isEmpty() {
		return resultset.isEmpty();
	}

	private Map<String, String> currentRow() {
		if (currentIndex < 0 || currentIndex >= resultset.size()) {
			throw new IndexOutOfBoundsException("Table " + name + ": row index " + currentIndex + " out of range (rows: " + resultset.size() + ")");
		}
		return resultset.get(currentIndex);
	}

	public void setValue(String name, String value) {
		currentRow().put(name, value);
	}

	public void setValue(String name, int value) {
		setValue(name, String.valueOf(value));
	}

	public int getNumRows() {
		return resultset.size();
	}

	public String getString(String name) {
		return currentRow().get(name);
	}

	public int getInt(String name) {
		String v = getString(name);
		return v == null || v.trim().isEmpty() ? 0 : Integer.parseInt(v.trim());
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < resultset.size(); i++) {
			sb.append(i);
			sb.append(": ");
			sb.append(resultset.get(i));
			sb.append("\n");
		}
		return sb.toString();
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
