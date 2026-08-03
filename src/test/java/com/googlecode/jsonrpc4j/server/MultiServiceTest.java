package com.googlecode.jsonrpc4j.server;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import com.googlecode.jsonrpc4j.JsonRpcMultiServer;
import com.googlecode.jsonrpc4j.JsonRpcParam;
import com.googlecode.jsonrpc4j.JsonRpcServer;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.Mock;
import org.easymock.MockType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.beans.Transient;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static com.googlecode.jsonrpc4j.JsonRpcBasicServer.RESULT;
import static com.googlecode.jsonrpc4j.util.Util.decodeAnswer;
import static com.googlecode.jsonrpc4j.util.Util.messageWithMapParamsStream;
import static com.googlecode.jsonrpc4j.util.Util.param1;
import static com.googlecode.jsonrpc4j.util.Util.param2;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(EasyMockRunner.class)
public class MultiServiceTest {
	
	private static final String serviceName = "Test";
	@Mock(type = MockType.NICE)
	private ServiceInterfaceWithParamNameAnnotation mockService;
	private JsonRpcMultiServer multiServer;
	private ByteArrayOutputStream byteArrayOutputStream;
	
	@Before
	public void setup() {
		multiServer = new JsonRpcMultiServer();
		multiServer.addService(serviceName, mockService, ServiceInterfaceWithParamNameAnnotation.class);
		byteArrayOutputStream = new ByteArrayOutputStream();
	}
	
	@Test
	public void callMethodExactNumberOfParametersNamed() throws Exception {
		EasyMock.expect(mockService.testMethod(param2)).andReturn("success");
		EasyMock.replay(mockService);
		
		multiServer.handleRequest(messageWithMapParamsStream(serviceName + JsonRpcMultiServer.DEFAULT_SEPARATOR + "testMethod", param1, param2), byteArrayOutputStream);
		
		assertEquals("success", decodeAnswer(byteArrayOutputStream).get(RESULT).asString());
	}

	/** Test that verifies the custom ObjectMapper is actually used for serialization
	 *  by adding a custom String serializer that converts to uppercase. */
	@Test
	public void testCustomObjectMapperConstructor() throws Exception {
		
		ObjectMapper customMapper = JsonMapper.builder()
			.addModule(new SimpleModule()
				.addSerializer(String.class, new ValueSerializer<>() {
					@Override
					public void serialize(String value, JsonGenerator gen, SerializationContext serializers) {
						gen.writeString(value.toUpperCase());
					}
				}))
			.build();
		
		JsonRpcMultiServer customServer = new JsonRpcMultiServer(customMapper);
		customServer.addService(serviceName, mockService, ServiceInterfaceWithParamNameAnnotation.class);
		
		EasyMock.expect(mockService.testMethod(param2)).andReturn("custom-result-lowercase");
		EasyMock.replay(mockService);
		
		ByteArrayOutputStream customOutput = new ByteArrayOutputStream();
		customServer.handleRequest(messageWithMapParamsStream(serviceName + JsonRpcMultiServer.DEFAULT_SEPARATOR + "testMethod", param1, param2), customOutput);
		
		// If the custom ObjectMapper is used, the result should be in uppercase
		String result = decodeAnswer(customOutput).get(RESULT).asString();
		assertEquals("CUSTOM-RESULT-LOWERCASE", result);
	}

	public interface ServiceInterfaceWithParamNameAnnotation {
		String testMethod(@JsonRpcParam("param1") String param1);
	}
	
}
