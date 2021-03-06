package com.emc.mongoose.common.net;
//
import com.emc.mongoose.common.concurrent.LifeCycle;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 Created by kurila on 07.05.14.
 A remote service which has a name for resolution by URI.
 */
public interface Service
extends Remote, LifeCycle {
	//
	String getName()
	throws RemoteException;
	//
}
