package edu.ohsu.cmp.ecp.sds;

import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
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
		
		String baseUrl = authorizedUserId.getBaseUrl();
		IGenericClient fhirClient = fhirContext.newRestfulGenericClient( baseUrl ) ;
		
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
