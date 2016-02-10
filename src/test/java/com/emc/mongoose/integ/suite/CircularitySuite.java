package com.emc.mongoose.integ.suite;
/**
 Created by kurila on 02.12.15.
 */
import com.emc.mongoose.integ.feature.circularity.CircularReadAfterUpdateTest;
import com.emc.mongoose.integ.feature.circularity.CircularReadDistributedTest;
import com.emc.mongoose.integ.feature.circularity.CircularReadFromBucketTest;
import com.emc.mongoose.integ.feature.circularity.CircularReadTest;
import com.emc.mongoose.integ.feature.circularity.CircularSequentialChainDistributedTest;
import com.emc.mongoose.integ.feature.circularity.CircularSequentialChainTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
@RunWith(Suite.class)
@Suite.SuiteClasses({
	//
	CircularReadAfterUpdateTest.class,
	CircularReadTest.class,
	CircularReadFromBucketTest.class,
	CircularSequentialChainTest.class,
	//
	CircularReadDistributedTest.class,
	CircularSequentialChainDistributedTest.class,
})
public class CircularitySuite {
}
