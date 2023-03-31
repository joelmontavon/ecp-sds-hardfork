package edu.ohsu.cmp.ecp.sds;

import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.interceptor.auth.AuthorizationInterceptor;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRule;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRuleBuilder;
import ca.uhn.fhir.rest.server.interceptor.auth.RuleBuilder;
import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IIdType;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.List;

@Interceptor
@Component
public class SupplementalDataStoreAuthorizationInterceptor extends AuthorizationInterceptor {

	@Inject
	SupplementalDataStoreProperties sdsProperties;

	@Inject
	SupplementalDataStoreAuth auth;

	@Inject
	SupplementalDataStoreLinkage linkage;

	@Override
	public List<IAuthRule> buildRuleList(RequestDetails theRequestDetails) {
		IAuthRuleBuilder ruleBuilder = new RuleBuilder();

		IIdType authorizedNonLocalUserId = auth.authorizedPatientId(theRequestDetails);

		IIdType authorizedLocalUserId = linkage.establishLocalUserFor(authorizedNonLocalUserId);

		if (!"Patient".equalsIgnoreCase(authorizedNonLocalUserId.getResourceType())) {
			/* return early, no details of the patient identity are available */
			return ruleBuilder
				.denyAll("expected user to be authorized as \"Patient\" but encountered \"" + authorizedNonLocalUserId.getResourceType() + "\"")
				.build();
		}

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

		/* permit access to all sds-foreign records for specific patient in each tenant */
		for (IBaseReference nonLocalPatient : linkage.patientsLinkedTo(localPatientId)) {
			IIdType nonLocalPatientId = nonLocalPatient.getReferenceElement();
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
		}

		return ruleBuilder.denyAll("everything else").build();
	}
}
