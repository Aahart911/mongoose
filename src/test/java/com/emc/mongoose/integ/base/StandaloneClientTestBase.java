package com.emc.mongoose.integ.base;
//
import com.emc.mongoose.core.api.item.data.WSObject;
import com.emc.mongoose.util.client.api.StorageClient;
import com.emc.mongoose.util.client.api.StorageClientBuilder;
import com.emc.mongoose.util.client.impl.BasicStorageClientBuilder;
//
import org.junit.AfterClass;
import org.junit.BeforeClass;
/**
 Created by andrey on 13.08.15.
 */
public abstract class StandaloneClientTestBase
extends WSMockTestBase {
	//
	protected static StorageClientBuilder<WSObject, StorageClient<WSObject>>
		CLIENT_BUILDER;
	//
	@BeforeClass
	public static void setUpClass()
	throws Exception {
		WSMockTestBase.setUpClass();
		CLIENT_BUILDER = new BasicStorageClientBuilder<WSObject, StorageClient<WSObject>>()
			.setClientMode(null);
	}
	//
	@AfterClass
	public static void tearDownClass()
	throws Exception {
		WSMockTestBase.tearDownClass();
	}
}
