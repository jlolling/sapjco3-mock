package de.jlo.sapjco.mock;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.BeforeClass;

import com.sap.conn.jco.JCoDestination;
import com.sap.conn.jco.JCoDestinationManager;
import com.sap.conn.jco.JCoFunction;
import com.sap.conn.jco.JCoParameterList;
import com.sap.conn.jco.JCoTable;
import com.sap.conn.jco.ext.DestinationDataEventListener;
import com.sap.conn.jco.ext.DestinationDataProvider;
import com.sap.conn.jco.ext.Environment;

/**
 * Uses the JCo API the same way as the SAPRFCProxyServer (TableInputImpl) does
 */
public abstract class MockTestBase {

	static final String DELIMITER = "\b";
	static final Map<String, Properties> destinations = new HashMap<>();

	@BeforeClass
	public static void setupMock() {
		MockSystem.getInstance().setConfigFile(new File("example/sapjco-mock.properties"));
		if (Environment.isDestinationDataProviderRegistered() == false) {
			Environment.registerDestinationDataProvider(new DestinationDataProvider() {

				@Override
				public Properties getDestinationProperties(String destinationName) {
					return destinations.get(destinationName);
				}

				@Override
				public void setDestinationDataEventListener(DestinationDataEventListener l) {}

				@Override
				public boolean supportsEvents() {
					return false;
				}
			});
		}
	}

	static Properties messageServer(String password) {
		Properties p = new Properties();
		p.setProperty("jco.client.mshost", "sapms.mock.local");
		p.setProperty("jco.client.r3name", "MCK");
		p.setProperty("jco.client.group", "PUBLIC");
		p.setProperty("jco.client.client", "100");
		p.setProperty("jco.client.user", "testuser");
		p.setProperty("jco.client.passwd", password);
		p.setProperty("jco.client.lang", "DE");
		return p;
	}

	static JCoDestination destination(Properties props) throws Exception {
		String name = "DEST" + destinations.size();
		destinations.put(name, props);
		return JCoDestinationManager.getDestination(name);
	}

	/**
	 * Runs RFC_READ_TABLE and returns the rows split by the delimiter and trimmed (like the proxy does)
	 */
	static List<List<String>> readTable(String table, String[] fields, String[] options, int rowSkips, int rowCount) throws Exception {
		JCoDestination dest = destination(messageServer("Test1234!"));
		JCoFunction function = dest.getRepository().getFunctionTemplate("RFC_READ_TABLE").getFunction();
		JCoParameterList tables = function.getTableParameterList();
		if (options != null) {
			JCoTable o = tables.getTable("OPTIONS");
			o.appendRows(options.length);
			o.firstRow();
			for (String text : options) {
				o.setValue("TEXT", text);
				o.nextRow();
			}
		}
		JCoTable f = tables.getTable("FIELDS");
		f.appendRows(fields.length);
		f.firstRow();
		for (String name : fields) {
			f.setValue("FIELDNAME", name);
			f.nextRow();
		}
		JCoParameterList imports = function.getImportParameterList();
		imports.setValue("QUERY_TABLE", table);
		imports.setValue("DELIMITER", DELIMITER);
		if (rowCount > 0) {
			imports.setValue("ROWCOUNT", String.valueOf(rowCount));
		}
		if (rowSkips > 0) {
			imports.setValue("ROWSKIPS", String.valueOf(rowSkips));
		}
		function.execute(dest);
		JCoTable data = tables.getTable("DATA");
		List<List<String>> result = new ArrayList<>();
		for (int i = 0; i < data.getNumRows(); i++) {
			String wa = data.getString("WA");
			List<String> row = new ArrayList<>();
			for (String v : wa.split(DELIMITER, -1)) {
				row.add(v.trim());
			}
			result.add(row);
			data.nextRow();
		}
		return result;
	}

}
