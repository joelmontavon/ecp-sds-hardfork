package ca.uhn.fhir.jpa.starter;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

import edu.ohsu.cmp.ecp.sds.AuthAwareTestConfig;

@Configuration
public class AppTestMockOAuth2SecurityConfig {

	private static final String MOCK_TOKEN = "MOCK-TOKEN" ;
	private static final String MOCK_USER_NAME = "MOCK-USERNAME" ;
	private static final String MOCK_SUBJECT = "Patient/MOCK-SUBJECT" ;

	private static final BearerTokenResolver MOCK_TOKEN_RESOLVER =
			new MockBearerTokenResolver(MOCK_TOKEN) ;

	@Autowired
	AppTestMockPrincipalRegistry principalRegistry ;
	
	@Bean @Primary
	public OpaqueTokenIntrospector mockOpaqueTokenIntrospector() {
		return new MockOpaqueTokenIntrospector() ;
	}
	
	@Bean @Primary
	@ConditionalOnMissingBean( AuthAwareTestConfig.class )
	public BearerTokenResolver mockBearerTokenResolver() {
		principalRegistry.register( MOCK_TOKEN ).principal(MOCK_USER_NAME, MOCK_SUBJECT) ;
		return MOCK_TOKEN_RESOLVER ;
	}
	
	private class MockOpaqueTokenIntrospector implements OpaqueTokenIntrospector {
		
		@Override
		public OAuth2AuthenticatedPrincipal introspect(String token) {
			return
				principalRegistry.principalForToken(token)
					.orElseThrow( () -> new OAuth2IntrospectionException("expected mock token but found \"" + token + "\"; try one of " + principalRegistry.registeredTokens() ) )
					;
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
	
}
