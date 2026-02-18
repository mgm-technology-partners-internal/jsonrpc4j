package com.googlecode.jsonrpc4j;

import tools.jackson.core.JsonParser;
import tools.jackson.core.TreeNode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;

@SuppressWarnings("WeakerAccess")
public class ReadContext {
	
	private final InputStream input;
	private final ObjectMapper mapper;
	private JsonParser parser;
	
	private ReadContext(InputStream input, ObjectMapper mapper) {
		this.input = new NoCloseInputStream(input);
		this.mapper = mapper;
		this.parser = null;
	}
	
	public static ReadContext getReadContext(InputStream input, ObjectMapper mapper) {
		return new ReadContext(input, mapper);
	}
	
	public JsonNode nextValue() throws IOException {
		// Lazy initialization of parser - create once and reuse for streaming reads
		if (parser == null) {
			parser = mapper.createParser(input);
		}
		// Use readValueAsTree which reads exactly one JSON value and advances the parser
		// This properly handles streaming scenarios where multiple JSON objects are in the stream
		TreeNode tree = parser.readValueAsTree();
		return (JsonNode) tree;
	}
	
	public void assertReadable() throws IOException {
		try {
			if (input.markSupported()) {
				input.mark(1);
				if (input.read() == -1) {
					throw new StreamEndedException();
				}
				input.reset();
			}
		} catch(SocketException se) {
			throw new StreamEndedException();
		}
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + input.hashCode();
		result = prime * result + (mapper == null ? 0 : mapper.hashCode());
		return result;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ReadContext other = (ReadContext) obj;
		if (!input.equals(other.input))
			return false;
		if (mapper == null) {
			if (other.mapper != null)
				return false;
		} else if (!mapper.equals(other.mapper))
			return false;
		return true;
	}
	
}
