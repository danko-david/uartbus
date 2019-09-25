package eu.javaexperience.electronic.uartbus.cli.apps;

import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Date;
import java.util.List;
import java.util.Map;

import eu.javaexperience.cli.CliEntry;
import eu.javaexperience.cli.CliTools;
import eu.javaexperience.database.ConnectionBuilder;
import eu.javaexperience.database.JDBC;
import eu.javaexperience.electronic.uartbus.rpc.client.UartbusRpcClientTools;
import eu.javaexperience.electronic.uartbus.rpc.client.UartbusRpcClientTools.PacketStreamThread;
import eu.javaexperience.log.JavaExperienceLoggingFacility;
import eu.javaexperience.log.LogLevel;
import eu.javaexperience.log.Loggable;
import eu.javaexperience.log.Logger;
import eu.javaexperience.log.LoggingTools;
import eu.javaexperience.text.StringTools;

public class UartbusLogSql
{
	protected static final Logger LOG = JavaExperienceLoggingFacility.getLogger(new Loggable("UartbusLogSql"));
	
	protected static final CliEntry<String> SQL_CONNECTION = CliEntry.createFirstArgParserEntry
	(
		(e)->e,
		"Sql server connect string like: jdbc:mysql://127.0.0.1:3306/database",
		"s", "-sql"
	);
	
	protected static final CliEntry<String> SQL_TABLE = CliEntry.createFirstArgParserEntry
	(
		(e)->e,
		"Sql server table",
		"t", "-table"
	);
	
	protected static final CliEntry<String> SQL_USER = CliEntry.createFirstArgParserEntry
	(
		(e)->e,
		"Sql server username",
		"u", "-user"
	);

	protected static final CliEntry<String> SQL_PASSWORD = CliEntry.createFirstArgParserEntry
	(
		(e)->e,
		"Sql server password. Alternatively you can specify the password as UB_SQL_PASSWORD environment variable if you wan to keep it safe",
		"P", "-password"
	);
	
	protected static final CliEntry[] PROG_CLI_ENTRIES =
	{
		RPC_HOST,
		RPC_PORT,
		LOOPBACK,
		SQL_CONNECTION,
		SQL_TABLE,
		SQL_USER,
		SQL_PASSWORD
	};
	
	public static void printHelpAndExit(int exit)
	{
		System.err.println("Usage of UartbusLogSql:\n");
		System.err.println(CliTools.renderListAllOption(PROG_CLI_ENTRIES));
		System.exit(1);
	}
	
	protected static void prepareTable(Connection conn, String type, String table) throws Exception
	{
		if(!JDBC.isTableExists(conn, table))
		{
			switch (type)
			{
			case "mysql":
				JDBC.execute(conn, "CREATE TABLE `"+table+"` (`id` INT NOT NULL PRIMARY KEY AUTO_INCREMENT, `date` TIMESTAMP, `ms` BIGINT NOT NULL, `data` BLOB)");
				break;
				
			case "sqlite":
				JDBC.execute(conn, "CREATE TABLE `"+table+"` (`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, `date` TIMESTAMP, `ms` BIGINT NOT NULL, `data` BLOB)");
				break;
				
			default:
				throw new RuntimeException("Unknown database type: "+type);
			}
			
			JDBC.execute(conn, "ALTER TABLE `"+table+"` ADD INDEX `index_id` (`id`)");
			JDBC.execute(conn, "ALTER TABLE `"+table+"` ADD INDEX `index_date` (`date`)");
			JDBC.execute(conn, "ALTER TABLE `"+table+"` ADD INDEX `index_ms` (`ms`)");
			JDBC.execute(conn, "ALTER TABLE `"+table+"` ADD INDEX `index_data` (`data`(128))");
		}
	}
	
	public static void main(String[] args) throws Throwable
	{
		JavaExperienceLoggingFacility.addStdOut();
		Map<String, List<String>> pa = CliTools.parseCliOpts(args);
		String un = CliTools.getFirstUnknownParam(pa, PROG_CLI_ENTRIES);
		if(null != un)
		{
			printHelpAndExit(1);
		}
		
		//initializes the drivers
		ConnectionBuilder.values();
				
		//sql connect
		String sqlUrl = SQL_CONNECTION.getSimple(pa);
		
		String table = SQL_TABLE.getSimple(pa);
		
		String user = SQL_USER.getSimple(pa);
		
		String password = SQL_PASSWORD.getSimple(pa);
		
		if(null == password)
		{
			password = System.getenv("UB_SQL_PASSWORD");
		}
		
		String type = StringTools.getFirstBetween(sqlUrl, ":", ":");
		type = type.toLowerCase();
		
		Connection conn = DriverManager.getConnection(sqlUrl, user, password);
		prepareTable(conn, type, table);
		
		
		String insert = "INSERT INTO `"+table+"` (date, ms, data) VALUES (?,?,?)";
		
		PacketStreamThread stream = UartbusRpcClientTools.streamPackets
		(
			RPC_HOST.tryParseOrDefault(pa, "127.0.0.1"),
			RPC_PORT.tryParseOrDefault(pa, 2112),
			(e) ->
			{
				Date now = new Date();
				try
				{
					JDBC.executePrepared(conn, insert, now, now.getTime(), e);
				}
				catch(Exception e1)
				{
					LoggingTools.tryLogFormatException(LOG, LogLevel.ERROR, e1, "Exception ocurred while saving packet");
				}
			}
		);
		
		if(LOOPBACK.hasOption(pa))
		{
			stream.conn.setAttribute("loopback_send_packets", "true");
		}
	}
}
