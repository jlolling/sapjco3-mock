package de.jlo.sapjco.mock;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.sap.conn.jco.JCoException;
import com.sap.conn.jco.JCoFunction;

/**
 * Simulated SAP system. It is configured with a properties file which is located by:
 * <ol>
 * <li>system property sapjco.mock.config</li>
 * <li>environment variable SAPJCO_MOCK_CONFIG</li>
 * <li>file sapjco-mock.properties in the current directory</li>
 * </ol>
 * The file is reloaded when it was changed, so the mock can be reconfigured without restarting the application.
 */
public class MockSystem {

	public static final String CONFIG_PROPERTY = "sapjco.mock.config";
	public static final String CONFIG_ENV = "SAPJCO_MOCK_CONFIG";
	public static final String CONFIG_DEFAULT_FILE = "sapjco-mock.properties";

	private static final String FUNCTION_RFC_READ_TABLE = "RFC_READ_TABLE";
	private static final String FUNCTION_RFC_PING = "RFC_PING";
	private static final MockSystem instance = new MockSystem();

	private File configFile = null;
	private long configLastModified = -1;
	private Config config = null;

	/**
	 * Immutable snapshot of the configuration, so concurrent requests are not affected by a reload
	 */
	static class Config {
		String r3Name;
		String msHost;
		String msServ;
		List<String> groups = new ArrayList<>();
		String asHost;
		String sysNr;
		List<String> clients = new ArrayList<>();
		Map<String, String> passwords = new HashMap<>();
		Map<String, Boolean> locked = new HashMap<>();
		File tablesDir;
		long executeDelayMillis = 0;
		Properties raw;
	}

	private MockSystem() {}

	public static MockSystem getInstance() {
		return instance;
	}

	public static boolean isFunctionAvailable(String name) {
		return FUNCTION_RFC_READ_TABLE.equals(name) || FUNCTION_RFC_PING.equals(name);
	}

	/**
	 * Use the given configuration file instead of the lookup (mainly for tests)
	 */
	public synchronized void setConfigFile(File file) {
		this.configFile = file;
		this.config = null;
		this.configLastModified = -1;
	}

	private File findConfigFile() {
		if (configFile != null) {
			return configFile;
		}
		String path = System.getProperty(CONFIG_PROPERTY);
		if (path == null || path.trim().isEmpty()) {
			path = System.getenv(CONFIG_ENV);
		}
		if (path == null || path.trim().isEmpty()) {
			path = CONFIG_DEFAULT_FILE;
		}
		return new File(path.trim());
	}

	synchronized Config getConfig() throws JCoException {
		File f = findConfigFile();
		if (f.exists() == false) {
			throw new JCoException(JCoException.JCO_ERROR_CONFIGURATION, "JCO_ERROR_CONFIGURATION",
					"SAP mock system is not configured: config file " + f.getAbsolutePath() + " does not exist. Set it with -D" + CONFIG_PROPERTY + "=<file> or the environment variable " + CONFIG_ENV);
		}
		if (config == null || f.lastModified() != configLastModified) {
			Properties p = new Properties();
			try (InputStream in = new FileInputStream(f)) {
				p.load(in);
			} catch (Exception e) {
				throw new JCoException(JCoException.JCO_ERROR_CONFIGURATION, "JCO_ERROR_CONFIGURATION", "Cannot read SAP mock config file " + f.getAbsolutePath() + ": " + e.getMessage());
			}
			config = parseConfig(p, f.getAbsoluteFile().getParentFile());
			configLastModified = f.lastModified();
		}
		return config;
	}

	static Config parseConfig(Properties p, File baseDir) {
		Config c = new Config();
		c.raw = p;
		c.r3Name = trim(p.getProperty("system.r3name"));
		c.msHost = trim(p.getProperty("system.mshost"));
		c.msServ = trim(p.getProperty("system.msserv"));
		c.groups = splitList(p.getProperty("system.groups", "PUBLIC"));
		c.asHost = trim(p.getProperty("system.ashost"));
		c.sysNr = trim(p.getProperty("system.sysnr"));
		c.clients = splitList(p.getProperty("system.clients", "100"));
		for (String key : p.stringPropertyNames()) {
			if (key.startsWith("user.") && key.endsWith(".password")) {
				String prefix = key.substring(0, key.length() - ".password".length());
				String user = prefix.substring("user.".length()).toUpperCase();
				c.passwords.put(user, p.getProperty(key));
				c.locked.put(user, Boolean.parseBoolean(p.getProperty(prefix + ".locked", "false").trim()));
			}
		}
		String dir = trim(p.getProperty("tables.dir", "tables"));
		File tablesDir = new File(dir);
		if (tablesDir.isAbsolute() == false) {
			tablesDir = new File(baseDir, dir);
		}
		c.tablesDir = tablesDir;
		c.executeDelayMillis = parseLong(p.getProperty("execute.delayMillis"), 0);
		return c;
	}

	/**
	 * Checks the connection and logon parameters like a SAP message server / application server
	 * @param props JCo destination properties
	 */
	public void logon(Properties props) throws JCoException {
		Config c = getConfig();
		String msHost = trim(props.getProperty("jco.client.mshost"));
		String asHost = trim(props.getProperty("jco.client.ashost"));
		if (msHost != null) {
			checkMessageServer(c, props, msHost);
		} else if (asHost != null) {
			checkApplicationServer(c, props, asHost);
		} else {
			throw new JCoException(JCoException.JCO_ERROR_CONFIGURATION, "JCO_ERROR_CONFIGURATION", "Neither jco.client.ashost nor jco.client.mshost is set");
		}
		checkLogon(c, props);
	}

	private void checkMessageServer(Config c, Properties props, String msHost) throws JCoException {
		String r3Name = trim(props.getProperty("jco.client.r3name"));
		String msServ = trim(props.getProperty("jco.client.msserv"));
		String group = trim(props.getProperty("jco.client.group"));
		String params = "Connection parameters: TYPE=B MSHOST=" + msHost + (msServ != null ? " MSSERV=" + msServ : "") + " R3NAME=" + r3Name + " GROUP=" + group;
		if (c.msHost == null) {
			throw communicationError("Connect to message server host failed " + params + " ERROR partner '" + msHost + "' not reached (the mock system has no message server configured)");
		}
		if (c.msHost.equalsIgnoreCase(msHost) == false) {
			throw communicationError("Connect to message server host failed " + params + " ERROR hostname '" + msHost + "' unknown");
		}
		if (msServ == null) {
			// without msserv the service name sapms<R3NAME> is resolved
			if (r3Name == null) {
				throw new JCoException(JCoException.JCO_ERROR_CONFIGURATION, "JCO_ERROR_CONFIGURATION", "Parameter jco.client.r3name or jco.client.msserv is required for a message server connection");
			}
			if (r3Name.equalsIgnoreCase(c.r3Name) == false) {
				throw communicationError("Connect to message server host failed " + params + " ERROR service 'sapms" + r3Name + "' unknown");
			}
		} else {
			// msserv can be the port or the service name
			if (msServ.equals(c.msServ) == false && msServ.equalsIgnoreCase("sapms" + c.r3Name) == false) {
				throw communicationError("Connect to message server host failed " + params + " ERROR partner '" + msHost + ":" + msServ + "' not reached");
			}
			if (r3Name != null && r3Name.equalsIgnoreCase(c.r3Name) == false) {
				throw communicationError("Connect to message server failed " + params + " ERROR system " + r3Name + " is not served by this message server");
			}
		}
		if (group == null) {
			throw new JCoException(JCoException.JCO_ERROR_CONFIGURATION, "JCO_ERROR_CONFIGURATION", "Parameter jco.client.group is required for a message server connection");
		}
		if (containsIgnoreCase(c.groups, group) == false) {
			throw communicationError("Connect to message server failed " + params + " ERROR Group " + group + " not found");
		}
	}

	private void checkApplicationServer(Config c, Properties props, String asHost) throws JCoException {
		String sysNr = trim(props.getProperty("jco.client.sysnr"));
		String params = "Connection parameters: TYPE=A ASHOST=" + asHost + " SYSNR=" + sysNr;
		if (c.asHost == null || c.asHost.equalsIgnoreCase(asHost) == false) {
			throw communicationError("Connect to SAP gateway failed " + params + " ERROR hostname '" + asHost + "' unknown");
		}
		if (sysNr == null || sysNr.equals(c.sysNr) == false) {
			throw communicationError("Connect to SAP gateway failed " + params + " ERROR partner '" + asHost + ":sapgw" + sysNr + "' not reached");
		}
	}

	private void checkLogon(Config c, Properties props) throws JCoException {
		String client = trim(props.getProperty("jco.client.client"));
		String user = trim(props.getProperty("jco.client.user"));
		String password = props.getProperty("jco.client.passwd");
		if (client == null || containsIgnoreCase(c.clients, client) == false) {
			throw logonError("Client " + client + " is not available in this system");
		}
		if (user == null || password == null) {
			throw logonError("Name or password is incorrect (repeat logon)");
		}
		user = user.toUpperCase(); // SAP user names are case-insensitive, passwords are not
		String expected = c.passwords.get(user);
		if (expected == null || expected.equals(password) == false) {
			throw logonError("Name or password is incorrect (repeat logon)");
		}
		if (Boolean.TRUE.equals(c.locked.get(user))) {
			throw logonError("User is locked. Please notify the person responsible");
		}
	}

	/**
	 * Execute the function within the mock system
	 */
	public void execute(JCoFunction function) throws JCoException {
		Config c = getConfig();
		sleep(c.executeDelayMillis);
		if (FUNCTION_RFC_READ_TABLE.equals(function.getName())) {
			new RfcReadTable(c).execute(function);
		} else if (FUNCTION_RFC_PING.equals(function.getName()) == false) {
			throw new JCoException(JCoException.JCO_ERROR_FUNCTION_NOT_FOUND, "JCO_ERROR_FUNCTION_NOT_FOUND", "Function " + function.getName() + " not found");
		}
	}

	static void sleep(long millis) {
		if (millis > 0) {
			try {
				Thread.sleep(millis);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private static JCoException communicationError(String message) {
		return new JCoException(JCoException.JCO_ERROR_COMMUNICATION, "JCO_ERROR_COMMUNICATION", message);
	}

	private static JCoException logonError(String message) {
		return new JCoException(JCoException.JCO_ERROR_LOGON_FAILURE, "JCO_ERROR_LOGON_FAILURE", message);
	}

	static String trim(String s) {
		if (s == null || s.trim().isEmpty()) {
			return null;
		}
		return s.trim();
	}

	static long parseLong(String s, long defaultValue) {
		s = trim(s);
		if (s == null) {
			return defaultValue;
		}
		return Long.parseLong(s);
	}

	static List<String> splitList(String s) {
		List<String> list = new ArrayList<>();
		if (s != null) {
			for (String part : Arrays.asList(s.split(","))) {
				if (part.trim().isEmpty() == false) {
					list.add(part.trim());
				}
			}
		}
		return list;
	}

	private static boolean containsIgnoreCase(List<String> list, String value) {
		for (String s : list) {
			if (s.equalsIgnoreCase(value)) {
				return true;
			}
		}
		return false;
	}

}
