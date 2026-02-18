package com.googlecode.jsonrpc4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SSLContext;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static com.googlecode.jsonrpc4j.JsonRpcBasicServer.ERROR;
import static com.googlecode.jsonrpc4j.JsonRpcBasicServer.ID;
import static com.googlecode.jsonrpc4j.JsonRpcBasicServer.JSONRPC;
import static com.googlecode.jsonrpc4j.JsonRpcBasicServer.METHOD;
import static com.googlecode.jsonrpc4j.JsonRpcBasicServer.PARAMS;
import static com.googlecode.jsonrpc4j.JsonRpcBasicServer.RESULT;

/**
 * Implements an asynchronous JSON-RPC 2.0 HTTP client using Java 11+ HttpClient.
 * <p>
 * This implementation replaces the previous Apache HttpComponents-based async client
 * with the standard Java HttpClient which provides native async support.
 * <p>
 * The following JVM system properties control the behavior:
 * <ul>
 * <li>com.googlecode.jsonrpc4j.async.connect.timeout - connection timeout in milliseconds, default is 30 seconds</li>
 * <li>com.googlecode.jsonrpc4j.async.request.timeout - request timeout in milliseconds, default is 30 seconds</li>
 * </ul>
 *
 * @author Brett Wooldridge (original)
 * @author jsonrpc4j contributors (Java 11+ HttpClient migration)
 */
@SuppressWarnings({"WeakerAccess", "unused"})
@Slf4j
public class JsonRpcHttpAsyncClient {

	private static final AtomicLong nextId = new AtomicLong();
	private static volatile SSLContext sslContext;

	private final ExceptionResolver exceptionResolver;
	private final Map<String, String> headers = new HashMap<>();
	private final ObjectMapper mapper;
	private final URL serviceUrl;
	private final HttpClient httpClient;

	/**
	 * Creates the {@link JsonRpcHttpAsyncClient} bound to the given {@code serviceUrl}.
	 *
	 * @param serviceUrl the service end-point URL
	 */
	public JsonRpcHttpAsyncClient(URL serviceUrl) {
		this(new ObjectMapper(), serviceUrl, new HashMap<>());
	}

	/**
	 * Creates the {@link JsonRpcHttpAsyncClient} using the specified {@code ObjectMapper} and bound to the given
	 * {@code serviceUrl}. The headers provided in the {@code headers} map are added to every request
	 * made to the {@code serviceUrl}.
	 *
	 * @param mapper     the {@link ObjectMapper} to use for json&lt;-&gt;java conversion
	 * @param serviceUrl the service end-point URL
	 * @param headers    the headers
	 */
	public JsonRpcHttpAsyncClient(ObjectMapper mapper, URL serviceUrl, Map<String, String> headers) {
		this(mapper, DefaultExceptionResolver.INSTANCE, serviceUrl, headers);
	}

	/**
	 * Creates the {@link JsonRpcHttpAsyncClient} using the specified
	 * {@link ObjectMapper} and {@link ExceptionResolver}, bound to the given
	 * {@code serviceUrl}. The headers provided in the {@code headers} map are
	 * added to every request made to the {@code serviceUrl}.
	 * The {@link ExceptionResolver} can not be null.
	 *
	 * @param mapper            the {@link ObjectMapper} to use for json&lt;-&gt;java conversion
	 * @param exceptionResolver the {@link ExceptionResolver} translating remote exceptions.
	 * @param serviceUrl        the service end-point URL
	 * @param headers           the headers
	 */
	public JsonRpcHttpAsyncClient(ObjectMapper mapper, ExceptionResolver exceptionResolver, URL serviceUrl, Map<String, String> headers) {
		this.mapper = mapper;
		this.serviceUrl = serviceUrl;
		this.headers.putAll(headers);
		this.exceptionResolver = exceptionResolver;

		if (this.exceptionResolver == null) {
			throw new IllegalArgumentException("ExceptionResolver can not be null");
		}

		this.httpClient = createHttpClient();
	}

	/**
	 * Creates the {@link JsonRpcHttpAsyncClient} bound to the given
	 * {@code serviceUrl}. The headers provided in the {@code headers} map are
	 * added to every request made to the {@code serviceUrl}.
	 *
	 * @param serviceUrl the service end-point URL
	 * @param headers    the headers
	 */
	public JsonRpcHttpAsyncClient(URL serviceUrl, Map<String, String> headers) {
		this(new ObjectMapper(), serviceUrl, headers);
	}

	/**
	 * Set the SSLContext to be used to create SSL connections.
	 *
	 * @param sslContext the {@code SSLContext to use}
	 */
	public static void setSSLContext(SSLContext sslContext) {
		JsonRpcHttpAsyncClient.sslContext = sslContext;
	}

	/**
	 * Creates the HttpClient instance with configured timeouts and SSL context.
	 */
	private HttpClient createHttpClient() {
		int connectTimeout = Integer.getInteger("com.googlecode.jsonrpc4j.async.connect.timeout", 30000);

		HttpClient.Builder builder = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofMillis(connectTimeout));

		if (sslContext != null) {
			builder.sslContext(sslContext);
		}

		return builder.build();
	}

	/**
	 * Invokes the given method with the given arguments and returns
	 * immediately. The {@code Future} object that is returned can be used to
	 * retrieve the result.
	 *
	 * @param methodName the name of the method to invoke
	 * @param argument   the arguments to the method
	 * @return the response {@code Future<T>}
	 */
	public Future<Object> invoke(String methodName, Object argument) {
		return invoke(methodName, argument, Object.class, new HashMap<>());
	}

	/**
	 * Invokes the given method with the given arguments and returns
	 * immediately. The {@code Future<T>} object that is returned can be used to
	 * retrieve the result.
	 *
	 * @param methodName the name of the method to invoke
	 * @param argument   the arguments to the method
	 * @param returnType the return type
	 * @param <T>        the return type
	 * @return the response {@code Future<T>}
	 */
	public <T> Future<T> invoke(String methodName, Object argument, Class<T> returnType) {
		return invoke(methodName, argument, returnType, new HashMap<>());
	}

	/**
	 * Invokes the given method with the given arguments and returns
	 * immediately. The {@code extraHeaders} are added to the request. The
	 * {@code Future<T>} object that is returned can be used to retrieve the
	 * result.
	 *
	 * @param methodName   the name of the method to invoke
	 * @param argument     the argument to the method
	 * @param returnType   the return type
	 * @param extraHeaders extra headers to add to the request
	 * @param <T>          the return type
	 * @return the response {@code Future<T>}
	 */
	private <T> Future<T> invoke(String methodName, Object argument, Class<T> returnType, Map<String, String> extraHeaders) {
		return doInvoke(methodName, argument, returnType, extraHeaders);
	}

	/**
	 * Invokes the given method with the given arguments and invokes the
	 * {@code JsonRpcCallback} with the result.
	 *
	 * @param methodName the name of the method to invoke
	 * @param argument   the arguments to the method
	 * @param callback   the {@code JsonRpcCallback}
	 */
	public void invoke(String methodName, Object argument, JsonRpcCallback<Object> callback) {
		invoke(methodName, argument, Object.class, callback);
	}

	/**
	 * Invokes the given method with the given arguments and invokes the
	 * {@code JsonRpcCallback} with the result cast to the given
	 * {@code returnType}, or null if void.
	 *
	 * @param methodName the name of the method to invoke
	 * @param argument   the arguments to the method
	 * @param returnType the return type
	 * @param <T>        the return type
	 * @param callback   the {@code JsonRpcCallback}
	 */
	public <T> void invoke(String methodName, Object argument, Class<T> returnType, JsonRpcCallback<T> callback) {
		doInvokeWithCallback(methodName, argument, returnType, new HashMap<>(), callback);
	}

	/**
	 * Performs the actual invocation and returns a Future.
	 */
	@SuppressWarnings("unchecked")
	private <T> Future<T> doInvoke(String methodName, Object argument, Class<T> returnType, Map<String, String> extraHeaders) {
		try {
			byte[] requestBody = createRequestBody(methodName, argument);
			HttpRequest request = buildHttpRequest(requestBody, extraHeaders);

			int requestTimeout = Integer.getInteger("com.googlecode.jsonrpc4j.async.request.timeout", 30000);

			CompletableFuture<T> future = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
					.orTimeout(requestTimeout, TimeUnit.MILLISECONDS)
					.thenApply(response -> {
						try {
							return handleResponse(response, returnType);
						} catch (Throwable t) {
							throw new RuntimeException(t);
						}
					});

			return new JsonRpcFuture<>(future);
		} catch (IOException e) {
			CompletableFuture<T> failedFuture = new CompletableFuture<>();
			failedFuture.completeExceptionally(e);
			return new JsonRpcFuture<>(failedFuture);
		}
	}

	/**
	 * Performs the actual invocation with a callback.
	 */
	private <T> void doInvokeWithCallback(String methodName, Object argument, Class<T> returnType,
										  Map<String, String> extraHeaders, JsonRpcCallback<T> callback) {
		try {
			byte[] requestBody = createRequestBody(methodName, argument);
			HttpRequest request = buildHttpRequest(requestBody, extraHeaders);

			int requestTimeout = Integer.getInteger("com.googlecode.jsonrpc4j.async.request.timeout", 30000);

			httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
					.orTimeout(requestTimeout, TimeUnit.MILLISECONDS)
					.whenComplete((response, throwable) -> {
						if (throwable != null) {
							callback.onError(throwable);
						} else {
							try {
								T result = handleResponse(response, returnType);
								callback.onComplete(result);
							} catch (Throwable t) {
								callback.onError(t);
							}
						}
					});
		} catch (IOException e) {
			callback.onError(e);
		}
	}

	/**
	 * Creates the JSON-RPC request body.
	 */
	private byte[] createRequestBody(String methodName, Object arguments) throws IOException {
		ObjectNode request = mapper.createObjectNode();
		request.put(ID, nextId.getAndIncrement());
		request.put(JSONRPC, JsonRpcBasicServer.VERSION);
		request.put(METHOD, methodName);

		if (arguments != null && arguments.getClass().isArray()) {
			Object[] args = (Object[]) arguments;
			if (args.length > 0) {
				request.set(PARAMS, mapper.valueToTree(args));
			}
		} else if (arguments instanceof Collection) {
			Collection<?> collection = (Collection<?>) arguments;
			if (!collection.isEmpty()) {
				request.set(PARAMS, mapper.valueToTree(arguments));
			}
		} else if (arguments instanceof Map) {
			Map<?, ?> map = (Map<?, ?>) arguments;
			if (!map.isEmpty()) {
				request.set(PARAMS, mapper.valueToTree(arguments));
			}
		} else if (arguments != null) {
			request.set(PARAMS, mapper.valueToTree(arguments));
		}

		log.debug("JSON-RPC Request: {}", request);

		ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
		mapper.writeValue(baos, request);
		return baos.toByteArray();
	}

	/**
	 * Builds the HTTP request with headers.
	 */
	private HttpRequest buildHttpRequest(byte[] body, Map<String, String> extraHeaders) {
		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(serviceUrl.toString()))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofByteArray(body));

		// Add default headers
		for (Map.Entry<String, String> header : headers.entrySet()) {
			builder.header(header.getKey(), header.getValue());
		}

		// Add extra headers (may override defaults)
		for (Map.Entry<String, String> header : extraHeaders.entrySet()) {
			builder.header(header.getKey(), header.getValue());
		}

		return builder.build();
	}

	/**
	 * Handles the HTTP response and parses the JSON-RPC result.
	 */
	private <T> T handleResponse(HttpResponse<InputStream> response, Class<T> returnType) throws Throwable {
		int statusCode = response.statusCode();

		if (statusCode == 200) {
			return readResponse(returnType, response.body());
		} else {
			// Read the error response body for a more descriptive error message
			String errorBody = readErrorBody(response.body());
			throw new Exception(statusCode + " " + getStatusMessage(statusCode) + (errorBody.isEmpty() ? "" : ": " + errorBody));
		}
	}

	/**
	 * Reads the response body as a string for error reporting.
	 */
	private String readErrorBody(InputStream inputStream) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] buffer = new byte[1024];
			int len;
			while ((len = inputStream.read(buffer)) != -1) {
				baos.write(buffer, 0, len);
			}
			return baos.toString("UTF-8").trim();
		} catch (IOException e) {
			return "";
		}
	}

	/**
	 * Returns a human-readable message for HTTP status codes.
	 */
	private String getStatusMessage(int statusCode) {
		switch (statusCode) {
			case 400: return "Bad Request";
			case 401: return "Unauthorized";
			case 403: return "Forbidden";
			case 404: return "Not Found";
			case 405: return "HTTP method POST is not supported by this URL";
			case 500: return "Internal Server Error";
			case 502: return "Bad Gateway";
			case 503: return "Service Unavailable";
			default: return "HTTP Error";
		}
	}

	/**
	 * Reads a JSON-RPC response from the server.
	 *
	 * @param returnType the expected return type
	 * @param ips        the {@link InputStream} to read from
	 * @return the object returned by the JSON-RPC response
	 * @throws Throwable on error
	 */
	private <T> T readResponse(Type returnType, InputStream ips) throws Throwable {
		JsonNode response = mapper.readTree(new NoCloseInputStream(ips));
		log.debug("JSON-RPC Response: {}", response);

		if (!response.isObject()) {
			throw new JsonRpcClientException(0, "Invalid JSON-RPC response", response);
		}

		ObjectNode jsonObject = (ObjectNode) response;

		if (jsonObject.has(ERROR) && jsonObject.get(ERROR) != null && !jsonObject.get(ERROR).isNull()) {
			throw exceptionResolver.resolveException(jsonObject);
		}

		if (jsonObject.has(RESULT) && !jsonObject.get(RESULT).isNull() && jsonObject.get(RESULT) != null) {
			JsonParser returnJsonParser = mapper.treeAsTokens(jsonObject.get(RESULT));
			JavaType returnJavaType = mapper.getTypeFactory().constructType(returnType);
			return mapper.readValue(returnJsonParser, returnJavaType);
		}

		return null;
	}

	/**
	 * A Future implementation that wraps CompletableFuture for backwards compatibility.
	 */
	private static class JsonRpcFuture<T> implements Future<T> {

		private final CompletableFuture<T> delegate;

		JsonRpcFuture(CompletableFuture<T> delegate) {
			this.delegate = delegate;
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			return delegate.cancel(mayInterruptIfRunning);
		}

		@Override
		public boolean isCancelled() {
			return delegate.isCancelled();
		}

		@Override
		public boolean isDone() {
			return delegate.isDone();
		}

		@Override
		public T get() throws InterruptedException, ExecutionException {
			return delegate.get();
		}

		@Override
		public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
			return delegate.get(timeout, unit);
		}
	}
}
