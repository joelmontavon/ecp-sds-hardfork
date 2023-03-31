package edu.ohsu.cmp.ecp.sds;

import ca.uhn.fhir.jpa.model.config.PartitionSettings;
import ca.uhn.fhir.jpa.model.config.PartitionSettings.CrossPartitionReferenceMode;
import ca.uhn.fhir.rest.server.RestfulServer;
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
	SupplementalDataStorePartitionInterceptor partitionInterceptor;

	@PostConstruct
	public void configurePartitioning() {
		partitionSettings.setPartitioningEnabled(true);
		partitionSettings.setAllowReferencesAcrossPartitions(CrossPartitionReferenceMode.ALLOWED_UNQUALIFIED);
		server.registerInterceptor(partitionInterceptor);
	}

	@PostConstruct
	public void configureAuthorization() {
		server.registerInterceptor(authorizationInterceptor);
	}
}
