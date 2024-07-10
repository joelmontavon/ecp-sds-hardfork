package edu.ohsu.cmp.ecp.sds;

import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IClientInterceptor;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreRelatedPersonPermissions;

@Component
public class SupplementalDataStoreRelatedPersonPermissions implements SupplementalDataStorePermissions {
	
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(SupplementalDataStoreRelatedPersonPermissions.class) ;

	private final FhirContext fhirContext ;
	private final SupplementalDataStoreRelatedPerson sdsRelatedPerson;
	
	public SupplementalDataStoreRelatedPersonPermissions(FhirContext fhirContext, SupplementalDataStoreRelatedPerson sdsRelatedPerson ) {
		this.fhirContext = fhirContext;
		this.sdsRelatedPerson = sdsRelatedPerson;
	}

	@Override
	public IIdType resolveWritablePatientIdFor(IIdType authorizedUserId, Authentication authentication) {
		
		if ( !authorizedUserId.hasBaseUrl() ) {
			ourLog.warn( "cannot resolve writable patient id for \"" + authorizedUserId + "\" without base url"  ) ;
			return null ;
		}
		
		Object credentials = authentication.getCredentials();
		if ( null == credentials ) {
			ourLog.warn( "cannot resolve writable patient id for \"" + authorizedUserId + "\" without oauth2 token"  ) ;
			return null ;
		}
		
		if ( !(credentials instanceof OAuth2Token) ) {
			String credentialsType = credentials.getClass().getSimpleName();
			ourLog.warn( "cannot resolve writable patient id for \"" + authorizedUserId + "\" without oauth2 token (got a " + credentialsType + " credential)"  ) ;
			return null ;
		}
		OAuth2Token token = (OAuth2Token)credentials ;
		
		String baseUrl = authorizedUserId.getBaseUrl();
		IGenericClient fhirClient = fhirContext.newRestfulGenericClient( baseUrl ) ;
		
		IClientInterceptor authorizationInterceptor = new BearerTokenAuthInterceptor( token.getTokenValue() ) ;
		fhirClient.registerInterceptor( authorizationInterceptor );
		
		try {
			
			IBaseResource relatedPerson = fhirClient.read().resource("RelatedPerson").withId(authorizedUserId).execute() ;
			
			IBaseReference patientRef = patientAuthorizedForRelatedPersonToWrite( relatedPerson );
			
			return patientRef.getReferenceElement() ;
			
		} catch ( Throwable ex ) {
			ourLog.error( "failed to resolve writable patient id for \"" + authorizedUserId + "\"", ex  ) ;
			return null ;
		}
	}


	private IBaseReference patientAuthorizedForRelatedPersonToWrite( IBaseResource relatedPerson ) {
		return sdsRelatedPerson.patientFromRelatedPerson( relatedPerson ) ;
	}
	
}
