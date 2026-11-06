package com.sap.conn.jco;

import de.jlo.sapjco.mock.MockSystem;

public class JCoRepository {

	private final JCoDestination destination;

	JCoRepository(JCoDestination destination) {
		this.destination = destination;
	}

	/**
	 * Like the real JCo it returns null if the function module does not exist
	 */
	public JCoFunctionTemplate getFunctionTemplate(String name) throws JCoException {
		MockSystem.getInstance().logon(destination.getProperties());
		if (MockSystem.isFunctionAvailable(name)) {
			return new JCoFunctionTemplate(name);
		} else {
			return null;
		}
	}

	public JCoFunction getFunction(String name) throws JCoException {
		JCoFunctionTemplate t = getFunctionTemplate(name);
		return t != null ? t.getFunction() : null;
	}

}
