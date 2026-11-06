package com.sap.conn.jco.ext;

public class Environment {

	private static DestinationDataProvider provider = null;

	public static synchronized void registerDestinationDataProvider(DestinationDataProvider p) {
		if (p == null) {
			throw new IllegalArgumentException("DestinationDataProvider cannot be null");
		}
		// the real JCo allows only one registered provider
		if (provider != null) {
			throw new IllegalStateException("DestinationDataProvider already registered [" + provider.getClass().getName() + "]");
		}
		provider = p;
	}

	public static synchronized void unregisterDestinationDataProvider(DestinationDataProvider p) {
		if (provider == p) {
			provider = null;
		}
	}

	public static synchronized boolean isDestinationDataProviderRegistered() {
		return provider != null;
	}

	public static synchronized DestinationDataProvider getDestinationDataProvider() {
		return provider;
	}

}
