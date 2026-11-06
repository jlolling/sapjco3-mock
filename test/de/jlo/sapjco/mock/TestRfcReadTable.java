package de.jlo.sapjco.mock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.sap.conn.jco.AbapException;
import com.sap.conn.jco.JCoDestination;
import com.sap.conn.jco.JCoException;
import com.sap.conn.jco.JCoFunction;
import com.sap.conn.jco.JCoTable;

public class TestRfcReadTable extends MockTestBase {

	private static List<List<String>> read(String table, String fields, String... options) throws Exception {
		return readTable(table, fields.split(","), options.length > 0 ? options : null, 0, 0);
	}

	private static void assertAbapException(String expectedKey, String table, String fields, String... options) throws Exception {
		try {
			read(table, fields, options);
			fail("AbapException " + expectedKey + " expected");
		} catch (AbapException e) {
			assertEquals(e.getMessage(), expectedKey, e.getKey());
		}
	}

	@Test
	public void testReadAllRows() throws Exception {
		List<List<String>> rows = read("T001", "BUKRS,BUTXT,LAND1");
		assertEquals(5, rows.size());
		assertEquals(Arrays.asList("1000", "Muster AG", "DE"), rows.get(0));
		assertEquals("CSV quoting with the separator", "Test; Semikolon SA", rows.get(3).get(1));
	}

	@Test
	public void testFieldOrderAndPadding() throws Exception {
		JCoDestination dest = destination(messageServer("Test1234!"));
		JCoFunction function = dest.getRepository().getFunctionTemplate("RFC_READ_TABLE").getFunction();
		JCoTable f = function.getTableParameterList().getTable("FIELDS");
		f.appendRows(2);
		f.firstRow();
		f.setValue("FIELDNAME", "LAND1");
		f.nextRow();
		f.setValue("FIELDNAME", "BUKRS");
		function.getImportParameterList().setValue("QUERY_TABLE", "t001");
		function.getImportParameterList().setValue("DELIMITER", "|");
		function.execute(dest);
		JCoTable data = function.getTableParameterList().getTable("DATA");
		assertEquals("fixed width fields like in SAP", "DE |1000", data.getString("WA"));
		// field description returned in FIELDS
		f.firstRow();
		assertEquals("LAND1", f.getString("FIELDNAME"));
		assertEquals("000000", f.getString("OFFSET"));
		assertEquals("000003", f.getString("LENGTH"));
		f.nextRow();
		assertEquals("000004", f.getString("OFFSET"));
	}

	@Test
	public void testWithoutDelimiter() throws Exception {
		JCoDestination dest = destination(messageServer("Test1234!"));
		JCoFunction function = dest.getRepository().getFunctionTemplate("RFC_READ_TABLE").getFunction();
		JCoTable f = function.getTableParameterList().getTable("FIELDS");
		f.appendRows(2);
		f.firstRow();
		f.setValue("FIELDNAME", "BUKRS");
		f.nextRow();
		f.setValue("FIELDNAME", "LAND1");
		function.getImportParameterList().setValue("QUERY_TABLE", "T001");
		function.execute(dest);
		assertEquals("1000DE ", function.getTableParameterList().getTable("DATA").getString("WA"));
	}

	@Test
	public void testFilters() throws Exception {
		assertEquals(4, read("T001", "BUKRS", "MANDT = '100'").size());
		assertEquals(4, read("T001", "BUKRS", "MANDT EQ '100'").size());
		assertEquals(3, read("T001", "BUKRS", "MANDT = '100' AND WAERS = 'EUR'").size());
		assertEquals(2, read("T001", "BUKRS", "MANDT = '100' AND ( LAND1 = 'AT' OR LAND1 = 'US' )").size());
		assertEquals(2, read("T001", "BUKRS", "MANDT = '100' AND LAND1 IN ('AT','US')").size());
		assertEquals(2, read("T001", "BUKRS", "MANDT = '100' AND LAND1 NOT IN ('DE','FR')").size());
		assertEquals(2, read("T001", "BUKRS", "BUTXT LIKE 'Muster%'").size());
		assertEquals(3, read("T001", "BUKRS", "NOT BUTXT LIKE 'Muster%'").size());
		assertEquals(2, read("T001", "BUKRS", "MANDT = '100' AND BUKRS BETWEEN '2000' AND '3000'").size());
		assertEquals(3, read("T001", "BUKRS", "MANDT = '100' AND BUKRS > '1000' AND BUKRS <> '4000' OR MANDT = '200'").size());
		assertEquals("IS NOT NULL is always true", 5, read("T001", "BUKRS", "BUKRS IS NOT NULL").size());
		assertEquals("IS NULL is always false", 0, read("T001", "BUKRS", "BUKRS IS NULL").size());
		assertEquals("quote escaping", 1, read("MAKT", "MATNR", "MAKTX = 'Klingel \"Ding-Dong\"'").size());
		assertEquals("initial value", 1, read("MARA", "MATNR", "MATKL = ' '").size());
	}

	@Test
	public void testNumericComparison() throws Exception {
		// BRGEW has type P, so it is compared by value and not as text
		assertEquals(3, read("MARA", "MATNR", "BRGEW >= 12").size());
		assertEquals(4, read("MARA", "MATNR", "BRGEW < '8.250'").size());
	}

	@Test
	public void testFilterSplitIntoSeveralOptionLines() throws Exception {
		assertEquals(1, read("T001", "BUKRS", "MANDT = '100' AND", "LAND1 = 'US'").size());
	}

	@Test
	public void testOptionLineIsTruncatedAfter72Chars() throws Exception {
		// the condition is cut within the literal, like in SAP this breaks the condition
		String option = "MANDT = '100' AND BUTXT = 'Muster AG                                    xyz'";
		assertTrue(option.length() > 72);
		assertAbapException("OPTION_NOT_VALID", "T001", "BUKRS", option);
	}

	@Test
	public void testRowSkipsAndRowCount() throws Exception {
		List<List<String>> rows = readTable("MARA", new String[] {"MATNR"}, null, 2, 3);
		assertEquals(3, rows.size());
		assertEquals("000000000000000103", rows.get(0).get(0));
		rows = readTable("MARA", new String[] {"MATNR"}, new String[] {"MTART = 'FERT'"}, 1, 0);
		assertEquals("skip is applied after the filter", 2, rows.size());
		assertEquals("000000000000000102", rows.get(0).get(0));
	}

	@Test
	public void testEmptyLastField() throws Exception {
		List<List<String>> rows = read("MARA", "MATNR,BRGEW", "MATNR = '000000000000000107'");
		assertEquals(Arrays.asList("000000000000000107", ""), rows.get(0));
	}

	@Test
	public void testErrors() throws Exception {
		assertAbapException("TABLE_NOT_AVAILABLE", "ZNOTEXIST", "ID");
		assertAbapException("FIELD_NOT_VALID", "T001", "BUKRS,NOFIELD");
		assertAbapException("OPTION_NOT_VALID", "T001", "BUKRS", "NOFIELD = 'X'");
		assertAbapException("OPTION_NOT_VALID", "T001", "BUKRS", "BUKRS = ");
		assertAbapException("OPTION_NOT_VALID", "T001", "BUKRS", "BUKRS = 'unterminated");
		assertAbapException("DATA_BUFFER_EXCEEDED", "ZWIDE", "ID,TEXT1,TEXT2,TEXT3");
		assertAbapException("NOT_AUTHORIZED", "ZNOAUTH", "ID");
		// two wide fields fit into the buffer
		assertEquals(1, read("ZWIDE", "TEXT1,TEXT2").size());
	}

	@Test
	public void testSimulatedShortDump() throws Exception {
		try {
			read("ZDUMP", "ID");
			fail("Error expected");
		} catch (JCoException e) {
			assertEquals(JCoException.JCO_ERROR_SYSTEM_FAILURE, e.getGroup());
			assertEquals("SYSTEM_FAILURE", e.getKey());
		}
	}

	@Test
	public void testExecuteRequiresValidLogon() throws Exception {
		JCoDestination dest = destination(messageServer("wrong"));
		try {
			dest.getRepository();
			fail("Logon must fail");
		} catch (JCoException e) {
			assertEquals(JCoException.JCO_ERROR_LOGON_FAILURE, e.getGroup());
		}
	}

	@Test
	public void testUnknownFunction() throws Exception {
		assertNull(destination(messageServer("Test1234!")).getRepository().getFunctionTemplate("Z_DOES_NOT_EXIST"));
	}

}
