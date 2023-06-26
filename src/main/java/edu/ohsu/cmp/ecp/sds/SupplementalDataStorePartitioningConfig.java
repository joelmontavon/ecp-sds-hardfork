package edu.ohsu.cmp.ecp.sds;

import ca.uhn.fhir.jpa.model.config.PartitionSettings;
import ca.uhn.fhir.jpa.model.config.PartitionSettings.CrossPartitionReferenceMode;
import ca.uhn.fhir.jpa.searchparam.matcher.AuthorizationSearchParamMatcher;
import ca.uhn.fhir.jpa.searchparam.matcher.SearchParamMatcher;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthorizationSearchParamMatcher;
import ca.uhn.fhir.rest.server.interceptor.consent.ConsentInterceptor;
import ca.uhn.fhir.rest.server.interceptor.consent.RuleFilteringConsentService;

import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

@Configuration
public class SupplementalDataStorePartitioningConfig {

	@Inject
	SupplementalDataStoreProperties sdsProperties;

	@Inject
	RestfulServer server;

	@Inject
	PartitionSettings partitionSettings;

	@Inject
	SupplementalDataStoreAuthorizationInterceptor authorizationInterceptor;

	@Inject
	SupplementalDataStoreAuthorizationCapabilityInterceptor authorizationCapabilityInterceptor;
	
	@Inject
	SupplementalDataStoreLinkingInterceptor linkingInterceptor;

	@Inject
	SupplementalDataStorePartitionInterceptor partitionInterceptor;

	@Inject
	SearchParamMatcher searchParamMatcher;
	
	@PostConstruct
	public void configurePartitioning() {
		partitionSettings.setPartitioningEnabled(true);
		partitionSettings.setAllowReferencesAcrossPartitions(CrossPartitionReferenceMode.ALLOWED_UNQUALIFIED);
		server.registerInterceptor(partitionInterceptor);
	}

	@PostConstruct
	public void configureLinking() {
		server.registerInterceptor(linkingInterceptor);
	}
	
	@PostConstruct
	public void configureAuthorization() {
		server.registerInterceptor(authorizationCapabilityInterceptor);
		
		IAuthorizationSearchParamMatcher theAuthorizationSearchParamMatcher = new AuthorizationSearchParamMatcher(searchParamMatcher);
		authorizationInterceptor.setAuthorizationSearchParamMatcher(theAuthorizationSearchParamMatcher);
		server.registerInterceptor(authorizationInterceptor);
		
		ConsentInterceptor consentInterceptor = new ConsentInterceptor();
		consentInterceptor.registerConsentService(new RuleFilteringConsentService(authorizationInterceptor));
		server.registerInterceptor(consentInterceptor);
	}
}
