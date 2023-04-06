package ca.uhn.fhir.jpa.starter;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;

import java.util.Collection;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

@Configuration
public class AppTestMockOAuth2SecurityConfig {

	private static final String MOCK_TOKEN = "MOCK-TOKEN" ;
	private static final String MOCK_USER_NAME= "MOCK-USERNAME" ;

	private static final BearerTokenResolver MOCK_TOKEN_RESOLVER =
			new MockBearerTokenResolver(MOCK_TOKEN) ;

	private static final OpaqueTokenIntrospector MOCK_INTROSPECTOR =
		new MockOpaqueTokenIntrospector(MOCK_TOKEN, MOCK_USER_NAME) ;

	
	@Bean @Primary
	public OpaqueTokenIntrospector mockOpaqueTokenIntrospector() {
		return MOCK_INTROSPECTOR ;
	}
	
	@Bean @Primary
	public BearerTokenResolver mockBearerTokenResolver() {
		return MOCK_TOKEN_RESOLVER ;
	}
	
	private static class MockOpaqueTokenIntrospector implements OpaqueTokenIntrospector {
		
		private final String matchToken;
		private final String username;

		public MockOpaqueTokenIntrospector(String matchToken, String username) {
			this.matchToken = matchToken;
			this.username = username;
		}

		@Override
		public OAuth2AuthenticatedPrincipal introspect(String token) {
			if ( matchToken.equals( token ) ) {
				return new MockOAuth2AuthenticatedPrincipal(username) ;
			} else {
				throw new OAuth2IntrospectionException("expected mock token \"" + matchToken + "\" but found \"" + token + "\"" );
			}
		}
	}
	
	private static class MockBearerTokenResolver implements BearerTokenResolver {

		private final String produceToken;

		public MockBearerTokenResolver(String matchToken) {
			this.produceToken = matchToken;
		}

		@Override
		public String resolve(HttpServletRequest request) {
			return produceToken ;
		}
	}
	
	private static class MockOAuth2AuthenticatedPrincipal implements OAuth2AuthenticatedPrincipal {
		private final String name;
		
		public MockOAuth2AuthenticatedPrincipal(String name) {
			this.name = name;
		}

		@Override
		public String getName() {
			return this.name;
		}
		
		@Override
		public Collection<? extends GrantedAuthority> getAuthorities() {
			return asList( new SimpleGrantedAuthority("USER") );
		}
		
		@Override
		public Map<String, Object> getAttributes() {
			return emptyMap();
		}
	}		
}
