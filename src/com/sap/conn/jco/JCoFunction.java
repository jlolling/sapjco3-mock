package com.sap.conn.jco;

import de.jlo.sapjco.mock.MockSystem;

public class JCoFunction {

	private final String name;
	private final JCoParameterList importParameterList = new JCoParameterList();
	private final JCoParameterList exportParameterList = new JCoParameterList();
	private final JCoParameterList tableParameterList = new JCoParameterList();

	public JCoFunction(String name) {
		this.name = name;
	}

	public JCoParameterList getImportParameterList() {
		return importParameterList;
	}

	public JCoParameterList getExportParameterList() {
		return exportParameterList;
	}

	public JCoParameterList getTableParameterList() {
		return tableParameterList;
	}

	/**
	 * Logon and execute the function within the mock system
	 */
	public void execute(JCoDestination dest) throws JCoException {
		if (dest == null) {
			throw new IllegalArgumentException("Destination cannot be null");
		}
		MockSystem system = MockSystem.getInstance();
		system.logon(dest.getProperties());
		system.execute(this);
	}

	public String getName() {
		return name;
	}

}
