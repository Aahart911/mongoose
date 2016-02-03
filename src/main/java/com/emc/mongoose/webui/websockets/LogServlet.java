package com.emc.mongoose.webui.websockets;
//
import com.emc.mongoose.common.conf.BasicConfig;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

import java.util.concurrent.TimeUnit;

/**
 * Created by gusakk on 10/24/14.
 */
public final class LogServlet
extends WebSocketServlet {
	//
	@Override
	public final void configure(final WebSocketServletFactory factory) {
		final String[] websocketIdleTimeoutArray = BasicConfig.getContext()
			.getWebUIWSTimeout().split("\\.");
		factory.register(LogSocket.class);
		factory.getPolicy().setIdleTimeout(TimeUnit.valueOf(websocketIdleTimeoutArray[1].toUpperCase())
			.toMillis(Integer.valueOf(websocketIdleTimeoutArray[0])));
	}
}
