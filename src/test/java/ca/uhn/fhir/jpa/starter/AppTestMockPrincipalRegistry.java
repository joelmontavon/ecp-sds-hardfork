package ca.uhn.fhir.jpa.starter;

import static java.util.Arrays.asList;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.stereotype.Component;

@Component
public class AppTestMockPrincipalRegistry {
	
	private int tokenCount = 1000 ;
	
	private final Map<String,OAuth2AuthenticatedPrincipal> registeredPrincipals = new HashMap<>() ;
	
	public interface Registration {
		CompleteRegistration principal( OAuth2AuthenticatedPrincipal principal ) ;
		CompleteRegistration principal( String name, String subject ) ;
	}
	
	public interface CompleteRegistration {
		String token() ;
	}

	public Registration register() {
		final int tokenIndex = tokenCount++ ;
		return new Registration() {
			
			public CompleteRegistration principal( String name, String subject ) {
				MockOAuth2AuthenticatedPrincipal principal = new MockOAuth2AuthenticatedPrincipal( name, subject ) ;
				return principal( principal ) ; 
			}
			
			public CompleteRegistration principal( OAuth2AuthenticatedPrincipal principal ) {
			
				return new CompleteRegistration() {

					public String token() {
						String token = String.format( "MOCK-TOKEN-%04d", tokenIndex );
						registeredPrincipals.put(token, principal) ;
						return token ;
					}
					
				};
			
			}
			
		};
	}
	
	public Registration register( String token ) {
		return new Registration() {
			
			public CompleteRegistration principal( String name, String subject ) {
				MockOAuth2AuthenticatedPrincipal principal = new MockOAuth2AuthenticatedPrincipal( name, subject ) ;
				return principal( principal ) ; 
			}
			
			public CompleteRegistration principal( OAuth2AuthenticatedPrincipal principal ) {

				registeredPrincipals.put(token, principal) ;

				return new CompleteRegistration() {
					public String token() { return token ; }
				};
				
			}
			
		};
	}
	
	public Optional<OAuth2AuthenticatedPrincipal> principalForToken( String token ) {
		if ( registeredPrincipals.containsKey( token ) ) {
			return Optional.of( registeredPrincipals.get( token ) );
		} else {
			return Optional.empty();
		}
	}

	public Set<String> registeredTokens() {
		return new HashSet<>( registeredPrincipals.keySet() ) ;
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
