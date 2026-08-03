package com.googlecode.jsonrpc4j.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collection;

@SuppressWarnings("unused")
public interface FakeServiceInterface {
	void doSomething();
	
	int returnPrimitiveInt(int arg);
	
	CustomClass returnCustomClass(int arg1, String arg2);
	
	void throwSomeException(String message);
	
	class CustomClass {
		
		public final int integer;
		public final String string;
		@JsonProperty
		public final Collection<String> list = new ArrayList<>();
		
		public CustomClass() {
			this(0, "");
		}
		
		@JsonCreator
		CustomClass(@JsonProperty("integer") final int integer, @JsonProperty("string") final String string) {
			this.integer = integer;
			this.string = string;
		}
	}
}
