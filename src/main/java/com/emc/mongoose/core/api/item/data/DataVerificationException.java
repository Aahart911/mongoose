package com.emc.mongoose.core.api.item.data;
import java.io.IOException;
/**
 Created by andrey on 26.06.15.
 */
public abstract class DataVerificationException
extends IOException {
	public long offset;
}
