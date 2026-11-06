# sapjco3-mock

A drop-in replacement for the SAP JCo library `sapjco3.jar` that simulates a SAP system, without the native library and without a SAP server.
It is made to test applications using `RFC_READ_TABLE`, e.g. the [SAPRFCProxyServer](https://github.com/jlolling/SAPRFCProxyServer) and the Talend components tSAPRFCConnection / tSAPRFCTableInput.

It does **not** implement the RFC network protocol; it replaces the JCo API classes (`com.sap.conn.jco.*`) the application uses.

## What is simulated

* **Logon** like a message server (`jco.client.mshost`, `jco.client.r3name`, `jco.client.group`, optional `jco.client.msserv`) or an application server (`jco.client.ashost`, `jco.client.sysnr`), plus client, user and password.
  Errors are raised as `JCoException` with the group and a message similar to the real JCo, e.g.
  `(103) JCO_ERROR_LOGON_FAILURE: Name or password is incorrect (repeat logon)` or
  `(102) JCO_ERROR_COMMUNICATION: ... ERROR Group NOGROUP not found`.
  User names are case-insensitive, passwords are case-sensitive.
* **RFC_READ_TABLE** with tables read from CSV files:
  * `QUERY_TABLE`, `DELIMITER` (only the first character is used, like the CHAR1 parameter; empty or space means fixed width without delimiter), `ROWSKIPS`, `ROWCOUNT`, `NO_DATA`
  * `FIELDS`: no fields means all fields; on return the table contains `OFFSET`, `LENGTH`, `TYPE` and `FIELDTEXT`
  * `OPTIONS`: the lines are joined to the WHERE condition. Each line is cut after 72 characters, like the CHAR72 field in SAP.
    Supported: `=` `EQ` `<>` `NE` `<` `LT` `>` `GT` `<=` `LE` `>=` `GE`, `[NOT] LIKE` (`%`, `_`), `[NOT] IN (...)`, `[NOT] BETWEEN ... AND ...`, `IS [NOT] NULL`, `AND`, `OR`, `NOT`, parentheses.
    Fields of a numeric type (I, P, F) are compared by value, all others as text without trailing spaces.
  * `DATA-WA`: values are padded to the field length, like in SAP
  * ABAP exceptions (`AbapException`): `TABLE_NOT_AVAILABLE`, `FIELD_NOT_VALID`, `OPTION_NOT_VALID`, `DATA_BUFFER_EXCEEDED` (more than 512 characters per line)
* **Simulated delays and errors** per table, e.g. to test keep-alive handling or a short dump

## Configuration

The mock system is configured with a properties file, located by (first match wins):

1. system property `-Dsapjco.mock.config=<file>`
2. environment variable `SAPJCO_MOCK_CONFIG`
3. `sapjco-mock.properties` in the current directory

Changes of the file are picked up without restart. See [example/sapjco-mock.properties](example/sapjco-mock.properties):

| Property | Meaning |
|---|---|
| `system.r3name`, `system.mshost`, `system.msserv`, `system.groups` | message server: system ID, host, port or service name, logon groups (comma separated) |
| `system.ashost`, `system.sysnr` | application server |
| `system.clients` | available clients, comma separated |
| `user.<NAME>.password`, `user.<NAME>.locked` | users |
| `tables.dir` | directory with the table files, relative to the config file |
| `execute.delayMillis` | delay for every function call |
| `table.<NAME>.delayMillis` | delay for one table |
| `table.<NAME>.error` | `<KEY>:<message>`. RFC_READ_TABLE exception keys raise an `AbapException`, any other key a `JCoException` with group `JCO_ERROR_SYSTEM_FAILURE` |

### Table files

`<tables.dir>/<TABLE>.csv`, UTF-8, separator `;`, values starting with `"` are quoted (`""` for a quote).
The header line defines the fields as `NAME[:LENGTH[:TYPE[:TEXT]]]`. Without length the longest value defines it, the default type is `C`.
Empty lines and lines starting with `#` are ignored. Table names with `/` (namespaces) use `#` in the file name.

```
MANDT:3:C:Client;BUKRS:4:C:Company Code;BUTXT:25:C:Name of Company Code
100;1000;Muster AG
```

## Usage with the SAPRFCProxyServer

Build the mock (`mvn package`) and use `target/sapjco3-mock-<version>.jar` as `sapjco3.jar` next to the proxy jar (or as `sapjco3Path` for the build):

```
cp target/sapjco3-mock-0.2.jar <proxy dir>/sapjco3.jar
java -Dsapjco.mock.config=example/sapjco-mock.properties -jar saprfcproxyserver-3.0.jar
```

Example request (the example config contains this user):

```
curl -H 'Content-Type: application/json' -X POST localhost:9999/tableinput -d '{
  "destination": {"destinationType":"message_server", "host":"sapms.mock.local", "r3name":"MCK", "group":"PUBLIC",
                  "client":"100", "user":"TESTUSER", "password":"Test1234!", "language":"DE"},
  "tableName":"T001", "fields":"BUKRS,BUTXT,WAERS", "filter":"MANDT = '100' AND WAERS = 'EUR'"}'
```

Test tables of the example: `T001`, `MARA`, `MAKT`, `ZWIDE` (too wide for DATA-WA), `ZSLOW` (5 s delay), `ZDUMP` (short dump), `ZNOAUTH` (NOT_AUTHORIZED), `ZSLOWDUMP` (short dump after 5 s, to test an error after keep-alive output).
