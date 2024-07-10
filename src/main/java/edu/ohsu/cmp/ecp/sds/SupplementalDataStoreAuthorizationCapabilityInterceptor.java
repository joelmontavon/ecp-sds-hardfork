package edu.ohsu.cmp.ecp.sds;

import java.net.URI;
import java.net.URISyntaxException;

import javax.inject.Inject;

import org.hl7.fhir.instance.model.api.IBaseConformance;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties.Opaquetoken;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;

@Interceptor
@Component
public class SupplementalDataStoreAuthorizationCapabilityInterceptor {
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(SupplementalDataStoreAuthorizationCapabilityInterceptor.class);

	@Inject
	SupplementalDataStoreAuth auth;

	@Inject
	OAuth2ResourceServerProperties properties;
	
	@Hook(Pointcut.SERVER_CAPABILITY_STATEMENT_GENERATED)
	public void customize(IBaseConformance theCapabilityStatement) {
		Opaquetoken opaqueToken = properties.getOpaquetoken();
		if ( null == opaqueToken )
			return ;
		
		String introspectionUri = opaqueToken.getIntrospectionUri();
		if ( null == introspectionUri )
			return ;
		
		try {
			URI uri = new URI( introspectionUri ) ;
			URI authorizeUri = uri.resolve( "authorize" ) ;
			URI tokenUri = uri.resolve( "token" ) ;

			auth.addAuthCapability(theCapabilityStatement, authorizeUri, tokenUri );
		} catch (URISyntaxException ex) {
			ourLog.error("failed to configure capability statement with introspection uri", ex);
		}
	}
}
