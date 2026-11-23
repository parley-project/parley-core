package us.bringardner.parley.core.test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import us.bringardner.parley.core.IncludeResolver;

/** A resolver listed as a service for the tests: it knows the names that start with memory: */
public class MemoryIncludeResolver implements IncludeResolver {

	@Override
	public Found find(String from, String name) throws IOException {
		if( !name.startsWith("memory:") ) {
			return null;
		}
		if( name.equals("memory:host") ) {
			return new Found(name, new ByteArrayInputStream("setting.e=memory\nsetting.a=memory\n".getBytes(StandardCharsets.ISO_8859_1)));
		}
		throw new IOException("no such memory file " + name);
	}
}
