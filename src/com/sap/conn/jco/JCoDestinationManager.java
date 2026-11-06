package com.sap.conn.jco;

import java.util.Properties;

import com.sap.conn.jco.ext.DestinationDataProvider;
import com.sap.conn.jco.ext.Environment;

public class JCoDestinationManager {

	/**
	 * Returns the destination with the properties of the registered DestinationDataProvider.
	 * Like the real JCo it does not connect, the logon happens with the first call (ping, getRepository, execute).
	 */
	public static JCoDestination getDestination(String name) throws JCoException {
		if (name == null || name.trim().isEmpty()) {
			throw new JCoException(JCoException.JCO_ERROR_CONFIGURATION, "JCO_ERROR_CONFIGURATION", "Destination name cannot be null or empty");
		}
		DestinationDataProvider provider = Environment.getDestinationDataProvider();
		if (provider == null) {
			throw new JCoException(JCoException.JCO_ERROR_RESOURCE, "JCO_ERROR_RESOURCE", "Destination " + name + " does not exist (no DestinationDataProvider registered)");
		}
		Properties props;
		try {
			props = provider.getDestinationProperties(name);
		} catch (Exception e) {
			throw new JCoException(JCoException.JCO_ERROR_RESOURCE, "JCO_ERROR_RESOURCE", "Destination " + name + " could not be provided: " + e.getMessage());
		}
		if (props == null) {
			throw new JCoException(JCoException.JCO_ERROR_RESOURCE, "JCO_ERROR_RESOURCE", "Destination " + name + " does not exist");
		}
		return new JCoDestination(name, props);
	}

}
