package de.jlo.sapjco.mock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Properties;

import org.junit.Test;

import com.sap.conn.jco.JCoException;

public class TestLogon extends MockTestBase {

	private void assertLogonFails(Properties p, int expectedGroup, String expectedText) throws Exception {
		try {
			destination(p).ping();
			fail("Logon must fail");
		} catch (JCoException e) {
			assertEquals(e.getMessage(), expectedGroup, e.getGroup());
			assertTrue(e.getMessage(), e.getMessage().contains(expectedText));
		}
	}

	@Test
	public void testMessageServerLogon() throws Exception {
		destination(messageServer("Test1234!")).ping();
	}

	@Test
	public void testMessageServerWithMsServ() throws Exception {
		Properties p = messageServer("Test1234!");
		p.setProperty("jco.client.msserv", "3600");
		destination(p).ping();
		p.setProperty("jco.client.msserv", "sapmsMCK");
		destination(p).ping();
		p.setProperty("jco.client.msserv", "3601");
		assertLogonFails(p, JCoException.JCO_ERROR_COMMUNICATION, "not reached");
	}

	@Test
	public void testWrongPassword() throws Exception {
		assertLogonFails(messageServer("wrong"), JCoException.JCO_ERROR_LOGON_FAILURE, "Name or password is incorrect");
	}

	@Test
	public void testPasswordIsCaseSensitive() throws Exception {
		assertLogonFails(messageServer("test1234!"), JCoException.JCO_ERROR_LOGON_FAILURE, "Name or password is incorrect");
	}

	@Test
	public void testUnknownUser() throws Exception {
		Properties p = messageServer("Test1234!");
		p.setProperty("jco.client.user", "NOBODY");
		assertLogonFails(p, JCoException.JCO_ERROR_LOGON_FAILURE, "Name or password is incorrect");
	}

	@Test
	public void testLockedUser() throws Exception {
		Properties p = messageServer("Locked1234!");
		p.setProperty("jco.client.user", "LOCKED");
		assertLogonFails(p, JCoException.JCO_ERROR_LOGON_FAILURE, "User is locked");
	}

	@Test
	public void testUnknownClient() throws Exception {
		Properties p = messageServer("Test1234!");
		p.setProperty("jco.client.client", "999");
		assertLogonFails(p, JCoException.JCO_ERROR_LOGON_FAILURE, "Client 999 is not available");
	}

	@Test
	public void testUnknownMessageServer() throws Exception {
		Properties p = messageServer("Test1234!");
		p.setProperty("jco.client.mshost", "other.host");
		assertLogonFails(p, JCoException.JCO_ERROR_COMMUNICATION, "hostname 'other.host' unknown");
	}

	@Test
	public void testWrongR3Name() throws Exception {
		Properties p = messageServer("Test1234!");
		p.setProperty("jco.client.r3name", "XYZ");
		assertLogonFails(p, JCoException.JCO_ERROR_COMMUNICATION, "service 'sapmsXYZ' unknown");
	}

	@Test
	public void testUnknownGroup() throws Exception {
		Properties p = messageServer("Test1234!");
		p.setProperty("jco.client.group", "NOGROUP");
		assertLogonFails(p, JCoException.JCO_ERROR_COMMUNICATION, "Group NOGROUP not found");
	}

	@Test
	public void testApplicationServer() throws Exception {
		Properties p = messageServer("Test1234!");
		p.remove("jco.client.mshost");
		p.setProperty("jco.client.ashost", "sapapp.mock.local");
		p.setProperty("jco.client.sysnr", "00");
		destination(p).ping();
		p.setProperty("jco.client.sysnr", "01");
		assertLogonFails(p, JCoException.JCO_ERROR_COMMUNICATION, "sapgw01");
	}

	@Test
	public void testUnknownDestination() throws Exception {
		try {
			com.sap.conn.jco.JCoDestinationManager.getDestination("DOES_NOT_EXIST");
			fail("Destination must not exist");
		} catch (JCoException e) {
			assertEquals(JCoException.JCO_ERROR_RESOURCE, e.getGroup());
		}
	}

	@Test(expected = IllegalStateException.class)
	public void testProviderCanOnlyBeRegisteredOnce() {
		com.sap.conn.jco.ext.Environment.registerDestinationDataProvider(com.sap.conn.jco.ext.Environment.getDestinationDataProvider());
	}

}
