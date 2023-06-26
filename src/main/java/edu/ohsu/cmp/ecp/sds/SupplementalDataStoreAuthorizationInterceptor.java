package edu.ohsu.cmp.ecp.sds;

import static edu.ohsu.cmp.ecp.sds.SupplementalDataStoreLinkingInterceptor.*;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.inject.Inject;

import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IIdType;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.interceptor.auth.AuthorizationInterceptor;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRule;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRuleBuilder;
import ca.uhn.fhir.rest.server.interceptor.auth.RuleBuilder;
import edu.ohsu.cmp.ecp.sds.base.FhirResourceComparison;

@Interceptor
@Component
public class SupplementalDataStoreAuthorizationInterceptor extends AuthorizationInterceptor {
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(SupplementalDataStoreAuthorizationInterceptor.class);

	@Inject
	SupplementalDataStoreProperties sdsProperties;

	@Inject
	SupplementalDataStoreAuth auth;

	@Inject
	SupplementalDataStoreLinkage linkage;
	
	@Override
	public List<IAuthRule> buildRuleList(RequestDetails theRequestDetails) {
		IAuthRuleBuilder ruleBuilder = new RuleBuilder();
		
		ruleBuilder = ruleBuilder
			.allow( "capability statement" )
			.metadata()
			.andThen()
			;

		IIdType authorizedNonLocalUserId = getAuthorizedNonLocalPatientId(theRequestDetails);

		if ( null == authorizedNonLocalUserId ) {
			/* return early, no details of the patient identity are available */
			return ruleBuilder
				.denyAll("expected user to be authorized")
				.build();
		}

		if ( !"Patient".equalsIgnoreCase(authorizedNonLocalUserId.getResourceType())) {
			/* return early, no details of the patient identity are available */
			return ruleBuilder
					.denyAll("expected user to be authorized as \"Patient\" but encountered \"" + authorizedNonLocalUserId.getResourceType() + "\"")
					.build();
		}
		
		IIdType authorizedLocalUserId = getAuthorizedLocalPatientId(theRequestDetails);

		/*
		 * IF the request is a foreign Patient create
		 * THEN add the new Patient id to the local patient Linkage
		 */

		/*
		 * IF the request is a foreign Patient update
		 * AND the Patient id is not already linked to an existing local patient
		 * THEN add the Patient id to the local patient Linkage
		 */
		
		final IIdType localPatientId = authorizedLocalUserId;

		/* permit access to all sds-local records for specific patient */
		ruleBuilder = ruleBuilder
			.allow("read local patient " + localPatientId)
			.read()
			.allResources()
			.inCompartment("Patient", localPatientId)
			.andThen()
			.allow("write local patient " + localPatientId)
			.write()
			.allResources()
			.inCompartment("Patient", localPatientId)
			.andThen()
		;
		
		/* permit access to all sds-local linkages that link to specific patient */
		ruleBuilder = ruleBuilder
			.allow("read linkages for local patient " + localPatientId)
			.read()
			.resourcesOfType("Linkage")
			.withFilter( "item=" + localPatientId.getIdPart() )
			.andThen()
			;

		Set<IIdType> nonLocalPatientIds = FhirResourceComparison.idTypes().createSet() ;
		
		/* permit access to all sds-foreign records for specific patient in each partition */
		for ( IBaseReference nonLocalPatient : linkage.patientsLinkedTo(localPatientId) ) {
			IIdType nonLocalPatientId = nonLocalPatient.getReferenceElement();
			nonLocalPatientIds.add( nonLocalPatientId );
		}
		
		/* permit access to all sds-foreign records for specific patient in each partition */
		IIdType claimingNonLocalUserId = getClaimingNonLocalPatientId(theRequestDetails);
		if ( null != claimingNonLocalUserId ) {
			nonLocalPatientIds.add( claimingNonLocalUserId ) ;
		}
		
		for (IIdType nonLocalPatientId : nonLocalPatientIds ) {
			ruleBuilder = ruleBuilder
				.allow("read non-local patient " + nonLocalPatientId)
				.read()
				.allResources()
				.inCompartment("Patient", nonLocalPatientId)
				.andThen()
				.allow("write non-local patient " + nonLocalPatientId)
				.write()
				.allResources()
				.inCompartment("Patient", nonLocalPatientId)
				.andThen()
			;

			/* permit access to all sds-local linkages that link to specific patient */
			ruleBuilder = ruleBuilder
				.allow("read linkages for non-local patient " + nonLocalPatientId)
				.read()
				.resourcesOfType("Linkage")
				.withFilter( "item=" + nonLocalPatientId.getIdPart() )
				.andThen()
				;
		}

		return ruleBuilder.denyAll("everything else").build();
	}
}
