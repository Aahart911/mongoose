package com.emc.mongoose.core.api.load.executor;
//
import com.emc.mongoose.core.api.data.WSObject;
import com.emc.mongoose.core.api.io.task.WSIOTask;
import org.apache.http.concurrent.FutureCallback;
/**
 Created by kurila on 01.10.14.
 */
public interface WSLoadExecutor<T extends WSObject>
extends ObjectLoadExecutor<T>, FutureCallback<WSIOTask<T>> {
}
