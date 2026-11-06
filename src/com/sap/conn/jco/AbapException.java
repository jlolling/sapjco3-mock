package com.sap.conn.jco;

/**
 * Mock of the exception for classic ABAP exceptions raised by a function module
 * (e.g. TABLE_NOT_AVAILABLE in RFC_READ_TABLE)
 */
public class AbapException extends JCoException {

	private static final long serialVersionUID = 1L;

	public AbapException(String key, String message) {
		super(JCO_ERROR_ABAP_EXCEPTION, key, message);
	}

}
