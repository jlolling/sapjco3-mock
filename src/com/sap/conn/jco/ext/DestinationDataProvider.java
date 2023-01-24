package com.sap.conn.jco.ext;

import java.util.Properties;

public interface DestinationDataProvider {
	
	public Properties getDestinationProperties(String destinationName) throws DataProviderException;
	
	public void setDestinationDataEventListener(DestinationDataEventListener l);
	
	public boolean supportsEvents();

}
