package com.sap.conn.jco;

import java.util.Properties;

import de.jlo.sapjco.mock.MockSystem;

public class JCoDestination {

	private final String name;
	private final Properties properties;

	JCoDestination(String name, Properties properties) {
		this.name = name;
		this.properties = new Properties();
		this.properties.putAll(properties);
	}

	/**
	 * Performs the logon against the mock system
	 */
	public void ping() throws JCoException {
		MockSystem.getInstance().logon(properties);
	}

	public JCoRepository getRepository() throws JCoException {
		MockSystem.getInstance().logon(properties);
		return new JCoRepository(this);
	}

	public String getDestinationName() {
		return name;
	}

	public Properties getProperties() {
		Properties copy = new Properties();
		copy.putAll(properties);
		return copy;
	}

}
