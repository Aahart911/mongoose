package com.emc.mongoose.util.client.api;
//
import com.emc.mongoose.core.api.data.DataItem;
import com.emc.mongoose.core.api.data.model.DataItemInput;
import com.emc.mongoose.core.api.data.model.DataItemOutput;
//
import java.io.Closeable;
import java.io.IOException;
/**
 Created by kurila on 15.06.15.
 The client class supporting the following storage I/O methods: write, read, delete, update, append.
 Note that all the methods are blocking. Use a low-level load execution
 builders and jobs interface if a non-blocking approach is required.
 <p>Every method accepts an {@link com.emc.mongoose.core.api.data.model.DataItemInput} stream as a 1st
 argument which is used as the source of the data items descriptors which should be processed
 (e.g. written/read/deleted/etc). The resulting behavior is different for write methods and the
 remaining methods. If the value of the 1st argument is null write methods will generate new data
 items and read/delete/update/append methods will try to get the data items list from the specified
 bucket/container. Otherwise (1st argument is not null) write methods will re-create (possibly
 overwrite) exactly the same data items (same ids, sizes and contents) as specified by the source
 stream content and the remaining methods will process them as expected (read/delete/etc).</p>
 <p>The 2nd argument is always a destination for the data items which is used to serialize them
 after being processed successfully.</p>
 <p>Also each method returns the count of the successfully processed data items.</p>
 */
public interface StorageClient<T extends DataItem>
extends Closeable {

	/**
	 Write <b><i>new</i></b> data items of the specified size infinitely, do not store the written data items info
	 @param size the size of the data items to write.
	 @throws java.lang.IllegalArgumentException if negative value is passed
	 */
	long write(final long size)
	throws IllegalArgumentException, InterruptedException, IOException;

	/**
	 (Over|Re)Write the data items in a customized way using fixed data items sizes.
	 @param itemsInput data items info source
	 @param itemsOutput data items info destination, may be null
	 @param maxCount the count limit of the data items to write, 0 means no limit.
	 @param threadCount the count of the threads/connections per storage node
	 @param size the size of the data items to write.
	 @throws java.lang.IllegalArgumentException if negative value is passed
	 */
	long write(
		final DataItemInput<T> itemsInput, final DataItemOutput<T> itemsOutput,
		final long maxCount, final int threadCount, final long size
	) throws IllegalArgumentException, InterruptedException, IOException;

	/**
	 Write the data items in a customized way using specific data items sizes distribution.
	 @param itemsInput data items info source
	 @param itemsOutput data items info destination, may be null
	 @param maxCount the count limit of the data items to write, 0 means no limit.
	 @param threadCount the count of the threads/connections per storage node
	 @param minSize the minimum data item size
	 @param maxSize the maximum data item size
	 @param sizeBias see the
	 <a href="https://asdwiki.isus.emc.com:8443/display/OS/Mongoose+HowTo#MongooseHowTo-Howtodealwithdataitemsizedistribution">doc regarding this feature</a>
	 @return self.
	 @throws java.lang.IllegalArgumentException if negative value is passed
	 */
	long write(
		final DataItemInput<T> itemsInput, final DataItemOutput<T> itemsOutput,
		final long maxCount, final int threadCount,
		final long minSize, final long maxSize, final float sizeBias
	) throws IllegalArgumentException, InterruptedException, IOException;

	/**
	 Read the data items using the specified data items source, do not store the output data items info.
	 @param itemsInput data items info source
	 @throws java.lang.IllegalStateException if no data items list is available and no bucket/container is specified
	 */
	long read(final DataItemInput<T> itemsInput)
	throws IllegalStateException, InterruptedException, IOException;

	/**
	 Read the data items in a customized way.
	 @param itemsInput data items info source
	 @param itemsOutput data items info destination, may be null
	 @param maxCount the count limit of the data items to write, 0 means no limit.
	 @param threadCount the count of the threads/connections per storage node
	 @param verifyContentFlag To verify the content integrity or to not verify.
	 @throws java.lang.IllegalStateException if no data items list is available and no bucket/container is specified
	 */
	long read(
		final DataItemInput<T> itemsInput, final DataItemOutput<T> itemsOutput,
		final long maxCount, final int threadCount, final boolean verifyContentFlag
	) throws IllegalStateException, InterruptedException, IOException;

	/**
	 Delete the data items using the specified data items source, do not store the output data items info.
	 @param itemsInput data items info source
	 @throws java.lang.IllegalStateException if no data items list is available and no bucket/container is specified
	 */
	long delete(final DataItemInput<T> itemsInput)
	throws IllegalStateException, InterruptedException, IOException;

	/**
	 Delete the data items in a customized way.
	 @param itemsInput data items info source
	 @param itemsOutput data items info destination, may be null
	 @param maxCount the count limit of the data items to write, 0 means no limit.
	 @param threadCount the count of the threads/connections per storage node
	 @throws java.lang.IllegalStateException if no data items list is available and no bucket/container is specified
	 */
	long delete(
		final DataItemInput<T> itemsInput, final DataItemOutput<T> itemsOutput,
		final long maxCount, final int threadCount
	) throws IllegalStateException, InterruptedException, IOException;

	/**
	 Update the data items using the specified data items source, do not store the output data items info.
	 @param itemsInput data items info source
	 @throws java.lang.IllegalStateException if no data items list is available and no bucket/container is specified
	 */
	long update(final DataItemInput<T> itemsInput)
	throws IllegalStateException, InterruptedException, IOException;

	/**
	 Update the data items in a customized way.
	 @param itemsInput data items info source
	 @param itemsOutput data items info destination, may be null
	 @param maxCount the count limit of the data items to write, 0 means no limit.
	 @param threadCount the count of the threads/connections per storage node
	 @param countPerTime the count of the non-overlapping ranges to update per one request
	 @throws java.lang.IllegalArgumentException if non-positive value is passed
	 @throws java.lang.IllegalStateException if no data items list is available and no bucket/container is specified
	 */
	long update(
		final DataItemInput<T> itemsInput, final DataItemOutput<T> itemsOutput,
		final long maxCount, final int threadCount, final int countPerTime
	) throws IllegalArgumentException, IllegalStateException, InterruptedException, IOException;

	/**
	 Append the data items using the specified data items source and the specified fixed augment size, do not store the output data items info.
	 @param itemsInput data items info source
	 @param size the augment size to append to each data item
	 @throws java.lang.IllegalStateException if no data items list is available and no bucket/container is specified
	 */
	long append(final DataItemInput<T> itemsInput, final long size)
	throws IllegalStateException, InterruptedException, IOException;

	/**
	 Append the data items using the specified data items source and the specified fixed augment size.
	 @param itemsInput data items info source
	 @param itemsOutput data items info destination, may be null
	 @param maxCount the count limit of the data items to write, 0 means no limit.
	 @param threadCount the count of the threads/connections per storage node
	 @param size the augment size to append to each data item
	 @throws java.lang.IllegalStateException if no data items list is available and no bucket/container is specified
	 */
	long append(
		final DataItemInput<T> itemsInput, final DataItemOutput<T> itemsOutput,
		final long maxCount, final int threadCount, final long size
	) throws IllegalStateException, InterruptedException, IOException;

	/**
	 Append the data items in a customized way using the specified distribution of the augment size.
	 @param itemsInput data items info source
	 @param itemsOutput data items info destination, may be null
	 @param maxCount the count limit of the data items to write, 0 means no limit.
	 @param threadCount the count of the threads/connections per storage node
	 @param sizeMin the minimal size of the data augment to append
	 @param sizeMax the maximal size of the data augment to append
	 @param sizeBias see the
	 <a href="https://asdwiki.isus.emc.com:8443/display/OS/Mongoose+HowTo#MongooseHowTo-Howtodealwithdataitemsizedistribution">doc regarding this feature</a>
	 @throws IllegalArgumentException if non-positive value is passed
	 @throws IllegalStateException if no data items list is available and no bucket/container is specified
	 */
	long append(
		final DataItemInput<T> itemsInput, final DataItemOutput<T> itemsOutput,
		final long maxCount, final int threadCount,
		final long sizeMin, final long sizeMax, final float sizeBias
	) throws IllegalArgumentException, IllegalStateException, InterruptedException, IOException;
}
