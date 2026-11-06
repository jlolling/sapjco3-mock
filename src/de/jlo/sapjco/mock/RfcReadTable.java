package de.jlo.sapjco.mock;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.sap.conn.jco.AbapException;
import com.sap.conn.jco.JCoException;
import com.sap.conn.jco.JCoFunction;
import com.sap.conn.jco.JCoParameterList;
import com.sap.conn.jco.JCoTable;

/**
 * Simulates the function module RFC_READ_TABLE
 * <ul>
 * <li>Import: QUERY_TABLE, DELIMITER (only the first char is used, like the CHAR1 parameter), NO_DATA, ROWSKIPS, ROWCOUNT</li>
 * <li>Tables: OPTIONS (TEXT, CHAR72 - longer lines are truncated like in SAP), FIELDS (FIELDNAME, OFFSET, LENGTH, TYPE, FIELDTEXT), DATA (WA, CHAR512)</li>
 * <li>ABAP exceptions: TABLE_NOT_AVAILABLE, FIELD_NOT_VALID, OPTION_NOT_VALID, DATA_BUFFER_EXCEEDED, NOT_AUTHORIZED, TABLE_WITHOUT_DATA</li>
 * </ul>
 * Per table simulation settings in the config file:
 * <ul>
 * <li>table.&lt;NAME&gt;.delayMillis: wait before returning the result (e.g. to test keep-alive handling)</li>
 * <li>table.&lt;NAME&gt;.error: KEY:message - throws an AbapException for the keys of RFC_READ_TABLE, otherwise a SYSTEM_FAILURE (e.g. a short dump)</li>
 * </ul>
 */
public class RfcReadTable {

	public static final int MAX_LINE_LENGTH = 512;
	public static final int MAX_OPTION_LENGTH = 72;
	private static final List<String> ABAP_EXCEPTIONS = Arrays.asList("TABLE_NOT_AVAILABLE", "TABLE_WITHOUT_DATA", "OPTION_NOT_VALID", "FIELD_NOT_VALID", "NOT_AUTHORIZED", "DATA_BUFFER_EXCEEDED");

	private final MockSystem.Config config;

	RfcReadTable(MockSystem.Config config) {
		this.config = config;
	}

	public void execute(JCoFunction function) throws JCoException {
		JCoParameterList imports = function.getImportParameterList();
		JCoParameterList tables = function.getTableParameterList();
		String tableName = MockSystem.trim(imports.getString("QUERY_TABLE"));
		if (tableName == null) {
			throw new AbapException("TABLE_NOT_AVAILABLE", "Parameter QUERY_TABLE is not set");
		}
		tableName = tableName.toUpperCase();
		String delimiter = imports.getString("DELIMITER");
		delimiter = (delimiter == null || delimiter.isEmpty() || delimiter.charAt(0) == ' ') ? "" : delimiter.substring(0, 1);
		boolean noData = "X".equalsIgnoreCase(MockSystem.trim(imports.getString("NO_DATA")));
		int rowSkips = parseInt(imports.getString("ROWSKIPS"), "ROWSKIPS");
		int rowCount = parseInt(imports.getString("ROWCOUNT"), "ROWCOUNT");
		// simulation settings
		MockSystem.sleep(MockSystem.parseLong(config.raw.getProperty("table." + tableName + ".delayMillis"), 0));
		String error = MockSystem.trim(config.raw.getProperty("table." + tableName + ".error"));
		if (error != null) {
			throwConfiguredError(error);
		}
		MockTable table = loadTable(tableName);
		// fields
		JCoTable fieldsTable = tables.getTable("FIELDS");
		List<MockTable.Field> fields = new ArrayList<>();
		for (int i = 0; i < fieldsTable.getNumRows(); i++) {
			fieldsTable.setRow(i);
			String fieldName = MockSystem.trim(fieldsTable.getString("FIELDNAME"));
			if (fieldName == null) {
				continue;
			}
			MockTable.Field f = table.getField(fieldName);
			if (f == null) {
				throw new AbapException("FIELD_NOT_VALID", "Field " + fieldName.toUpperCase() + " does not exist in table " + tableName);
			}
			fields.add(f);
		}
		if (fields.isEmpty()) {
			fields.addAll(table.getFields()); // like SAP: no fields means all fields
		}
		int lineLength = 0;
		for (MockTable.Field f : fields) {
			lineLength += f.length;
		}
		lineLength += delimiter.length() * (fields.size() - 1);
		if (lineLength > MAX_LINE_LENGTH) {
			throw new AbapException("DATA_BUFFER_EXCEEDED", "The selected fields need " + lineLength + " characters per line, but DATA-WA has only " + MAX_LINE_LENGTH);
		}
		// return the field description
		fieldsTable.deleteAllRows();
		int offset = 0;
		for (MockTable.Field f : fields) {
			fieldsTable.appendRow();
			fieldsTable.setValue("FIELDNAME", f.name);
			fieldsTable.setValue("OFFSET", leftPad(String.valueOf(offset), 6));
			fieldsTable.setValue("LENGTH", leftPad(String.valueOf(f.length), 6));
			fieldsTable.setValue("TYPE", f.type);
			fieldsTable.setValue("FIELDTEXT", f.text);
			offset += f.length + delimiter.length();
		}
		if (fieldsTable.isEmpty() == false) {
			fieldsTable.firstRow();
		}
		// where condition
		WhereCondition.Node condition;
		String where = buildCondition(tables.getTable("OPTIONS"));
		try {
			condition = WhereCondition.parse(where, table);
		} catch (WhereCondition.ParseException e) {
			throw new AbapException("OPTION_NOT_VALID", "Invalid condition <" + where + ">: " + e.getMessage());
		}
		JCoTable data = tables.getTable("DATA");
		data.deleteAllRows();
		if (noData) {
			return;
		}
		int matched = 0;
		for (Map<String, String> row : table.getRows()) {
			if (condition.eval(row) == false) {
				continue;
			}
			matched++;
			if (matched <= rowSkips) {
				continue;
			}
			if (rowCount > 0 && data.getNumRows() >= rowCount) {
				break;
			}
			StringBuilder wa = new StringBuilder();
			for (int i = 0; i < fields.size(); i++) {
				if (i > 0) {
					wa.append(delimiter);
				}
				MockTable.Field f = fields.get(i);
				wa.append(rightPad(row.get(f.name), f.length));
			}
			data.appendRow();
			data.setValue("WA", wa.toString());
		}
		if (data.isEmpty() == false) {
			data.firstRow();
		}
	}

	private MockTable loadTable(String tableName) throws JCoException {
		File file = new File(config.tablesDir, tableName + ".csv");
		if (file.exists() == false) {
			// table names may contain / for namespaces, which cannot be used in file names
			file = new File(config.tablesDir, tableName.replace('/', '#') + ".csv");
		}
		if (file.exists() == false) {
			throw new AbapException("TABLE_NOT_AVAILABLE", "Table " + tableName + " is not available");
		}
		try {
			return MockTable.load(tableName, file);
		} catch (Exception e) {
			throw new JCoException(JCoException.JCO_ERROR_SYSTEM_FAILURE, "JCO_ERROR_SYSTEM_FAILURE", "Mock table file " + file.getAbsolutePath() + " is invalid: " + e.getMessage());
		}
	}

	private static String buildCondition(JCoTable options) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < options.getNumRows(); i++) {
			options.setRow(i);
			String text = options.getString("TEXT");
			if (text == null) {
				continue;
			}
			if (text.length() > MAX_OPTION_LENGTH) {
				text = text.substring(0, MAX_OPTION_LENGTH); // the TEXT field has only 72 chars
			}
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(text);
		}
		return sb.toString();
	}

	private static void throwConfiguredError(String error) throws JCoException {
		int pos = error.indexOf(':');
		String key = (pos > 0 ? error.substring(0, pos) : error).trim();
		String message = pos > 0 ? error.substring(pos + 1).trim() : "Simulated error";
		if (ABAP_EXCEPTIONS.contains(key)) {
			throw new AbapException(key, message);
		} else {
			throw new JCoException(JCoException.JCO_ERROR_SYSTEM_FAILURE, key, message);
		}
	}

	private static int parseInt(String s, String name) throws JCoException {
		s = MockSystem.trim(s);
		if (s == null) {
			return 0;
		}
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			throw new JCoException(JCoException.JCO_ERROR_SYSTEM_FAILURE, "CONVERSION_ERROR", "Value <" + s + "> for parameter " + name + " is not a number");
		}
	}

	private static String rightPad(String s, int length) {
		StringBuilder sb = new StringBuilder(s == null ? "" : s);
		while (sb.length() < length) {
			sb.append(' ');
		}
		return sb.toString();
	}

	private static String leftPad(String s, int length) {
		StringBuilder sb = new StringBuilder();
		while (sb.length() + s.length() < length) {
			sb.append('0');
		}
		return sb.append(s).toString();
	}

}
