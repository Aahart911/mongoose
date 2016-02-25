package com.emc.mongoose.integ.feature.circularity;

import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.appenders.RunIdFileManager;
import com.emc.mongoose.core.api.item.data.HttpDataItem;
import com.emc.mongoose.core.api.item.base.ItemDst;
import com.emc.mongoose.core.api.io.task.IOTask;
import com.emc.mongoose.core.impl.item.data.BasicHttpData;
import com.emc.mongoose.core.impl.item.data.ContentSourceBase;
import com.emc.mongoose.core.impl.item.base.ItemCSVFileDst;
import com.emc.mongoose.integ.base.DistributedClientTestBase;
import com.emc.mongoose.integ.tools.StdOutUtil;
import com.emc.mongoose.integ.tools.BufferingOutputStream;
import com.emc.mongoose.util.client.api.StorageClient;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

import static com.emc.mongoose.integ.tools.LogPatterns.CONSOLE_METRICS_AVG_CLIENT;
import static com.emc.mongoose.integ.tools.LogPatterns.CONSOLE_METRICS_SUM_CLIENT;

/**
 * Created by gusakk on 03.11.15.
 */
public class CircularAppendDistributedTest
extends DistributedClientTestBase {
	//
	private static final Logger LOG = LogManager.getLogger();
	//
	private static final int ITEM_MAX_QUEUE_SIZE = 65536;
	private static final int BATCH_SIZE = 100;
	private static final String DATA_SIZE = "128B";
	//
	private static final int WRITE_COUNT = 1234;
	private static final int APPEND_COUNT = 12340;
	//
	private static final int COUNT_OF_APPEND = 10;
	private static final int MIN_SIZE_AFTER_APPEND = 1280;
	//
	private static long COUNT_WRITTEN, COUNT_APPENDED;
	private static byte[] STD_OUT_CONTENT;
	//
	@BeforeClass
	public static void setUpClass() {
		try {
			System.setProperty(
				RunTimeConfig.KEY_RUN_ID, CircularAppendDistributedTest.class.getCanonicalName()
			);
			DistributedClientTestBase.setUpClass();
			//
			final AppConfig appConfig = BasicConfig.THREAD_CONTEXT.get();
			appConfig.set(RunTimeConfig.KEY_LOAD_CIRCULAR, true);
			appConfig.set(RunTimeConfig.KEY_ITEM_QUEUE_MAX_SIZE, ITEM_MAX_QUEUE_SIZE);
			appConfig.set(RunTimeConfig.KEY_ITEM_SRC_BATCH_SIZE, BATCH_SIZE);
			RunTimeConfig.setContext(appConfig);
			//
			try (
				final StorageClient<HttpDataItem> client = CLIENT_BUILDER
					.setAPI("s3")
					.setLimitTime(0, TimeUnit.SECONDS)
					.setLimitCount(WRITE_COUNT)
					.build()
			) {
				final ItemDst<HttpDataItem> writeOutput = new ItemCSVFileDst<HttpDataItem>(
					BasicHttpData.class, ContentSourceBase.getDefault()
				);
				COUNT_WRITTEN = client.write(
					null, writeOutput, WRITE_COUNT, 10, SizeInBytes.toFixedSize(DATA_SIZE)
				);
				TimeUnit.SECONDS.sleep(1);
				RunIdFileManager.flushAll();
				//
				try (
					final BufferingOutputStream
						stdOutInterceptorStream = StdOutUtil.getStdOutBufferingStream()
				) {
					if (COUNT_WRITTEN > 0) {
						COUNT_APPENDED = client.append(writeOutput.getItemSrc(), null, APPEND_COUNT, 10,
							SizeInBytes.toFixedSize(DATA_SIZE));
					} else {
						throw new IllegalStateException("Failed to append");
					}
					TimeUnit.SECONDS.sleep(1);
					STD_OUT_CONTENT = stdOutInterceptorStream.toByteArray();
				}
			}
			//
			RunIdFileManager.flushAll();
		} catch(final Exception e) {
			LogUtil.exception(LOG, Level.ERROR, e, "Failed");
		}
	}
	//
	@AfterClass
	public static void tearDownClass() {
		try {
			StdOutUtil.reset();
			DistributedClientTestBase.tearDownClass();
		} catch(final Exception e) {
			LogUtil.exception(LOG, Level.ERROR, e, "Failed");
		}
	}
	//
	@Test
	public void checkAppendedCount()
	throws Exception {
		Assert.assertEquals(
				COUNT_WRITTEN * COUNT_OF_APPEND, COUNT_APPENDED, (COUNT_APPENDED * 5) / 100
		);
	}
	//
	@Test
	public void checkDataItemsSize()
	throws Exception {
		try (
			final BufferedReader
				in = Files.newBufferedReader(FILE_LOG_DATA_ITEMS.toPath(), StandardCharsets.UTF_8)
		) {
			final Iterable<CSVRecord> recIter = CSVFormat.RFC4180.parse(in);
			for(final CSVRecord nextRec : recIter) {
				Assert.assertTrue(Integer.parseInt(nextRec.get(2)) >= MIN_SIZE_AFTER_APPEND);
			}
		}
	}
	//
	@Test
	public void checkConsoleAvgMetricsLogging()
	throws Exception {
		boolean passed = false;
		long lastSuccCount = 0;
		try(
			final BufferedReader in = new BufferedReader(
				new InputStreamReader(new ByteArrayInputStream(STD_OUT_CONTENT))
			)
		) {
			String nextStdOutLine;
			Matcher m;
			do {
				nextStdOutLine = in.readLine();
				if(nextStdOutLine == null) {
					break;
				} else {
					m = CONSOLE_METRICS_AVG_CLIENT.matcher(nextStdOutLine);
					if(m.find()) {
						Assert.assertTrue(
							"Load type is not " + AppConfig.LoadType.APPEND.name() + ": " + m.group("typeLoad"),
							AppConfig.LoadType.APPEND.name().equalsIgnoreCase(m.group("typeLoad"))
						);
						long
							nextSuccCount = Long.parseLong(m.group("countSucc")),
							nextFailCount = Long.parseLong(m.group("countFail"));
						Assert.assertTrue(
							"Next appended items count " + nextSuccCount +
								" is less than previous: " + lastSuccCount,
							nextSuccCount >= lastSuccCount
						);
						lastSuccCount = nextSuccCount;
						Assert.assertTrue("There are failures reported", nextFailCount == 0);
						passed = true;
					}
				}
			} while (true);
		}
		Assert.assertTrue(
			"Average metrics line matching the pattern was not met in the stdout", passed
		);
	}
	//
	@Test
	public void checkConsoleSumMetricsLogging()
	throws Exception {
		boolean passed = false;
		try(
			final BufferedReader in = new BufferedReader(
				new InputStreamReader(new ByteArrayInputStream(STD_OUT_CONTENT))
			)
		) {
			String nextStdOutLine;
			Matcher m;
			do {
				nextStdOutLine = in.readLine();
				if(nextStdOutLine == null) {
					break;
				} else {
					m = CONSOLE_METRICS_SUM_CLIENT.matcher(nextStdOutLine);
					if(m.find()) {
						Assert.assertTrue(
							"Load type is not " + AppConfig.LoadType.APPEND.name() + ": " + m.group("typeLoad"),
							AppConfig.LoadType.APPEND.name().equalsIgnoreCase(m.group("typeLoad"))
						);
						long
							countFail = Long.parseLong(m.group("countFail"));
						Assert.assertTrue("There are failures reported", countFail == 0);
						Assert.assertFalse("Summary metrics are printed twice at least", passed);
						passed = true;
					}
				}
			} while(true);
		}
		Assert.assertTrue(
			"Summary metrics line matching the pattern was not met in the stdout", passed
		);
	}
}
