package com.emc.mongoose.common.conf;
//
import org.apache.commons.configuration.Configuration;

import java.io.Externalizable;
import java.util.Map;
/**
 Created by kurila on 03.02.16.
 */
public interface AppConifg
extends Cloneable, Configuration, Externalizable {
	//
	void override(final String branch, final Map<String, ?> tree);
}
