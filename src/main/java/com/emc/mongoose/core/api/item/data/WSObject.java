package com.emc.mongoose.core.api.item.data;
//
import com.emc.mongoose.common.conf.BasicConfig;
//
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
//
/**
 Created by kurila on 29.09.14.
 Web storage data object.
 */
public interface WSObject
extends MutableDataItem, HttpEntity {
	//
	Header HEADER_CONTENT_TYPE = new BasicHeader(
		HTTP.CONTENT_TYPE, BasicConfig.getContext().getHttpContentType()
	);
	boolean
		IS_CONTENT_CHUNKED = BasicConfig.getContext().getHttpContentChunked(),
		IS_CONTENT_REPEATABLE = BasicConfig.getContext().getHttpContentRepeatable();
}
