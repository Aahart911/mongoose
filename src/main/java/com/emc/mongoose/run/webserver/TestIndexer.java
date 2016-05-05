package com.emc.mongoose.run.webserver;

import com.emc.mongoose.common.conf.AppConfig;
import com.emc.mongoose.common.log.LogUtil;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 Created by kurila on 05.05.16.
 */
public class TestIndexer
extends Thread {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	private volatile AppConfig appConfig = null;
	//
	private TestIndexer(final AppConfig appConfig) {
		super(TestIndexer.class.getName());
		setDaemon(true);
		this.appConfig = appConfig;
	}
	//
	private static volatile TestIndexer INSTANCE;
	//
	public synchronized static TestIndexer getInstance(final AppConfig appConfig) {
		if(INSTANCE == null) {
			INSTANCE = new TestIndexer(appConfig);
		} else {
			INSTANCE.appConfig = appConfig;
		}
		return INSTANCE;
	}
	//
	@Override
	public final void run() {
		final boolean indexerEnabledFlag = appConfig.getRunIndexEnabled();
		if(indexerEnabledFlag) {
			final String jdbcUri = appConfig.getRunIndexJdbcUri();
			final String jdbcUsername = appConfig.getRunIndexJdbcUsername();
			final String jdbcPassword = appConfig.getRunIndexJdbcPassword();
			final String jdbcProvider = appConfig.getRunIndexJdbcProvider();
			final String jdbcDriver = appConfig.getRunIndexJdbcDriver();
			runUsingJdbc(jdbcUri, jdbcUsername, jdbcPassword, jdbcProvider, jdbcDriver);
		}
	}
	//
	private void runUsingJdbc(
		final String jdbcUri, final String jdbcUsername, final String jdbcPassword,
		final String jdbcProvider, final String jdbcDriver
	) {
		if(jdbcUsername == null) {
			try(
				final Connection jdbcConnection = DriverManager.getConnection(jdbcUri)
			) {
				runUsingJdbcConnection(jdbcConnection);
			} catch(final SQLException e) {
				LogUtil.exception(LOG, Level.WARN, e, "Failed to open the DB");
			}
		} else {
			try(
				final Connection jdbcConnection = DriverManager.getConnection(
					jdbcUri, jdbcUsername, jdbcPassword
				)
			) {
			} catch(final SQLException e) {
				LogUtil.exception(LOG, Level.WARN, e, "Failed to open the DB");
			}
		}
	}
	//
	private void runUsingJdbcConnection(final Connection
}
