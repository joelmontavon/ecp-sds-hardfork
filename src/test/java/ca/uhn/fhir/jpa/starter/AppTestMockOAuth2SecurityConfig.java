package ca.uhn.fhir.jpa.starter;

import static java.util.Arrays.asList;

import java.util.Collection;
import java.util.HashMap;
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
	private static final String MOCK_USER_NAME = "MOCK-USERNAME" ;
	private static final String MOCK_SUBJECT = "MOCK-SUBJECT" ;

	private static final BearerTokenResolver MOCK_TOKEN_RESOLVER =
			new MockBearerTokenResolver(MOCK_TOKEN) ;

	private static final OpaqueTokenIntrospector MOCK_INTROSPECTOR =
		new MockOpaqueTokenIntrospector(MOCK_TOKEN, MOCK_USER_NAME, MOCK_SUBJECT) ;

	
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
		private final String subject;

		public MockOpaqueTokenIntrospector(String matchToken, String username, String subject) {
			this.matchToken = matchToken;
			this.username = username;
			this.subject = subject;
		}

		@Override
		public OAuth2AuthenticatedPrincipal introspect(String token) {
			if ( matchToken.equals( token ) ) {
				return new MockOAuth2AuthenticatedPrincipal(username, subject) ;
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
		private final String subject;
		
		public MockOAuth2AuthenticatedPrincipal(String name, String subject) {
			this.name = name;
			this.subject = subject;
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
			Map<String,Object> attr = new HashMap<>() ;
			attr.put( "sub", subject ) ;
			return attr;
		}
	}		
}
