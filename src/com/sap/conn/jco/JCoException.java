package com.sap.conn.jco;

/**
 * Mock of the JCo exception. The message format follows the real JCo: (group) GROUP_NAME: message
 */
public class JCoException extends Exception {

	private static final long serialVersionUID = 1L;
	public static final int JCO_ERROR_COMMUNICATION = 102;
	public static final int JCO_ERROR_LOGON_FAILURE = 103;
	public static final int JCO_ERROR_SYSTEM_FAILURE = 104;
	public static final int JCO_ERROR_RESOURCE = 106;
	public static final int JCO_ERROR_CONFIGURATION = 121;
	public static final int JCO_ERROR_FUNCTION_NOT_FOUND = 122;
	public static final int JCO_ERROR_ABAP_EXCEPTION = 123;

	private final int group;
	private final String key;

	public JCoException(int group, String key, String message) {
		super("(" + group + ") " + key + ": " + message);
		this.group = group;
		this.key = key;
	}

	public int getGroup() {
		return group;
	}

	public String getKey() {
		return key;
	}

}
