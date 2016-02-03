package com.emc.mongoose.integ.base;
//
import com.emc.mongoose.common.conf.BasicConfig;
//
import org.junit.AfterClass;
import org.junit.BeforeClass;
/**
 Created by kurila on 02.11.15.
 */
public class CambridgeLabViprTestBase
extends LoggingTestBase {
	//
	private static String STORAGE_ADDRS_DEFAULT;
	//
	@BeforeClass
	public static void setUpClass()
	throws Exception {
		LoggingTestBase.setUpClass();
		final BasicConfig rtConfig = BasicConfig.getContext();
		STORAGE_ADDRS_DEFAULT = rtConfig.getString(BasicConfig.KEY_STORAGE_ADDRS);
		rtConfig.set(BasicConfig.KEY_STORAGE_ADDRS, "10.249.237.73,10.249.237.74,10.249.237.75");
	}
	//
	@AfterClass
	public static void tearDownClass()
	throws Exception {
		LoggingTestBase.tearDownClass();
		final BasicConfig rtConfig = BasicConfig.getContext();
		rtConfig.set(BasicConfig.KEY_STORAGE_ADDRS, STORAGE_ADDRS_DEFAULT); // reset to default
	}
}
