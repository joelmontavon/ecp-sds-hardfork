package edu.ohsu.cmp.ecp.sds;

import static edu.ohsu.cmp.ecp.sds.SupplementalDataStoreLinkingInterceptor.getPermissions;

import java.util.List;

import javax.inject.Inject;

import org.hl7.fhir.instance.model.api.IIdType;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.interceptor.auth.AuthorizationInterceptor;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRule;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRuleBuilder;
import ca.uhn.fhir.rest.server.interceptor.auth.RuleBuilder;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreLinkingInterceptor.Permissions;

@Interceptor
@Component
public class SupplementalDataStoreAuthorizationInterceptor extends AuthorizationInterceptor {
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(SupplementalDataStoreAuthorizationInterceptor.class);

	@Inject
	SupplementalDataStoreProperties sdsProperties;

	@Inject
	SupplementalDataStoreAuth auth;

	@Override
	public List<IAuthRule> buildRuleList(RequestDetails theRequestDetails) {
		IAuthRuleBuilder ruleBuilder = new RuleBuilder();
		
		ruleBuilder = ruleBuilder
			.allow( "capability statement" )
			.metadata()
			.andThen()
			;

		Permissions permissions = getPermissions(theRequestDetails);

		if ( null == permissions ) {
			/* return early, no details of the authorization are available */
			return ruleBuilder
				.denyAll("expected user to be authorized for patient read and/or write")
				.build();
		}

		if ( permissions.readAllPatients().isEmpty() && permissions.readAndWriteSpecificPatient().isEmpty() ) {
			/* return early, no details of the patient identity are available */
			return ruleBuilder
					.denyAll("expected user \"" + permissions.authorizedNonLocalUserId() + "\" to be authorized for patient read and/or write")
					.build();
		}
		
		if ( permissions.readAllPatients().isPresent() )
			ruleBuilder = buildRuleListForPermissions( ruleBuilder, permissions.readAllPatients().get() ) ;

		if ( permissions.readAndWriteSpecificPatient().isPresent() )
			ruleBuilder = buildRuleListForPermissions( ruleBuilder, permissions.readAndWriteSpecificPatient().get() ) ;

		return ruleBuilder.denyAll("no access rules grant permission").build();
	}

	private IAuthRuleBuilder buildRuleListForPermissions( IAuthRuleBuilder ruleBuilder, Permissions.ReadAllPatients readAllPatients ) {
		/* permit access to all sds-foreign records for any patient in each partition */
		ruleBuilder = ruleBuilder
			.allow("read any non-local patient")
			.read()
			.allResources()
			.withAnyId()
			.andThen()
		;

		/* permit access to all sds-local linkages that link to specific patient */
		ruleBuilder = ruleBuilder
			.allow("read linkages for any non-local patient")
			.read()
			.resourcesOfType("Linkage")
			.withAnyId()
			.andThen()
			;

		return ruleBuilder ;
	}

	private IAuthRuleBuilder buildRuleListForPermissions( IAuthRuleBuilder ruleBuilder, Permissions.ReadAndWriteSpecificPatient readAndWriteSpecificPatients ) {
		/*
		 * IF the request is a foreign Patient create
		 * THEN add the new Patient id to the local patient Linkage
		 */

		/*
		 * IF the request is a foreign Patient update
		 * AND the Patient id is not already linked to an existing local patient
		 * THEN add the Patient id to the local patient Linkage
		 */
		
		final IIdType localPatientId = readAndWriteSpecificPatients.patientId().localUserId() ;

		/* permit access to all sds-local records for specific patient */
		ruleBuilder = ruleBuilder
			.allow("read local patient " + localPatientId)
			.read()
			.allResources()
			.inCompartment("Patient", localPatientId)
			.andThen()
			.deny( "write local patient " + localPatientId )
			.write()
			.resourcesOfType( "Patient" )
			.inCompartment("Patient", localPatientId)
			.andThen()
			.allow("write local patient " + localPatientId + " related resources")
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

		/* permit access to all sds-foreign records for specific patient in each partition */
		for (IIdType nonLocalPatientId : readAndWriteSpecificPatients.patientId().nonLocalUserIds() ) {
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

			if ( nonLocalPatientId.hasBaseUrl() ) {
				IIdType nonLocalPatientIdWithoutBaseUrl = nonLocalPatientId.toUnqualified();
				ruleBuilder = ruleBuilder
					.allow("read non-local patient " + nonLocalPatientIdWithoutBaseUrl )
					.read()
					.allResources()
					.inCompartment("Patient", nonLocalPatientIdWithoutBaseUrl)
					.andThen()
					.allow("write non-local patient " + nonLocalPatientIdWithoutBaseUrl)
					.write()
					.allResources()
					.inCompartment("Patient", nonLocalPatientIdWithoutBaseUrl)
					.andThen()
					;
			}

			/* permit access to all sds-local linkages that link to specific patient */
			ruleBuilder = ruleBuilder
				.allow("read linkages for non-local patient " + nonLocalPatientId)
				.read()
				.resourcesOfType("Linkage")
				.withFilter( "item=" + nonLocalPatientId.getIdPart() )
				.andThen()
				;
		}

		return ruleBuilder;
	}
}
