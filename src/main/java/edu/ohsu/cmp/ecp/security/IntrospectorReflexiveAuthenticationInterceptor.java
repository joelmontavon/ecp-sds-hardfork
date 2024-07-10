package edu.ohsu.cmp.ecp.security;

import com.google.common.base.Charsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.regex.Pattern;

/*
 * a ClientHttpRequestInterceptor that applies an Authorization header
 * containing a Bearer token that is pulled from the body of the request
 *
 * only useful on an OAuth2 introspection endpoint, which expects a POST body containing
 * one field named "token"
 */
public class IntrospectorReflexiveAuthenticationInterceptor implements ClientHttpRequestInterceptor {
	/*
	 * matches "token=xyz"
	 * when "token" is at the beginning of the string or preceded by an ampersand
	 * including the whole "xyz" up until an ampersand or the end of the string, but can include entities like "&abc;"
	 */
	private static final Pattern TOKEN_BODY_PATTERN = java.util.regex.Pattern.compile("(?:^|&)token=([^&]*)");

	@Override
	public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {

		if (null != body && 0 != body.length) {
			String postBody = java.net.URLDecoder.decode(new String(body), Charsets.UTF_8);
			java.util.regex.Matcher m = TOKEN_BODY_PATTERN.matcher(postBody);
			if (m.matches()) {
				final String token = m.group(1);

				HttpHeaders headers = request.getHeaders();
				if (!headers.containsKey(HttpHeaders.AUTHORIZATION)) {
					headers.setBearerAuth(token);
				}

			}
		}
		ClientHttpResponse response = execution.execute(request, body);
		if (response.getStatusCode() == HttpStatus.UNAUTHORIZED) {
			/*
			 * we're using the token from the body as our authorization, so a 401 means the same as a 200 with body { "active": false }
			 */
			return new ClientHttpResponseInactiveToken(response);
		} else {
			return response;
		}
	}

	private static class ClientHttpResponseInactiveToken implements ClientHttpResponse {

		private static final String RESPONSE_BODY = "{ \"active\": false }";

		private static final Charset RESPONSE_BODY_CHARSET = Charset.defaultCharset();
		private static final byte[] RESPONSE_BODY_BYTES = RESPONSE_BODY.getBytes(RESPONSE_BODY_CHARSET);
		private final InputStream inputStream;
		private final HttpHeaders headers = new HttpHeaders();
		private final ClientHttpResponse originalResponse;
		private String statusText;

		public ClientHttpResponseInactiveToken(ClientHttpResponse originalResponse) {
			this.originalResponse = originalResponse;
			headers.addAll(originalResponse.getHeaders());

			inputStream = new ByteArrayInputStream(RESPONSE_BODY_BYTES);
			headers.set(HttpHeaders.CONTENT_TYPE, "application/json; charset=" + RESPONSE_BODY_CHARSET);
			headers.set(HttpHeaders.CONTENT_LENGTH, Integer.toString(RESPONSE_BODY_BYTES.length));
		}

		@Override
		public InputStream getBody() throws IOException {
			return inputStream;
		}

		@Override
		public HttpHeaders getHeaders() {
			return headers;
		}

		@Override
		public HttpStatus getStatusCode() throws IOException {
			return HttpStatus.OK;
		}

		@Override
		public int getRawStatusCode() throws IOException {
			return HttpStatus.OK.value();
		}

		@Override
		public String getStatusText() throws IOException {
			if (null == statusText) {
				statusText = "token inactive (was " + originalResponse.getRawStatusCode() + " - \"" + originalResponse.getStatusText() + "\")";
			}
			return statusText;
		}

		@Override
		public void close() {
			originalResponse.close();
		}
	}
}