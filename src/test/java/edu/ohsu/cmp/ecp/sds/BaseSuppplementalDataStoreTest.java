package edu.ohsu.cmp.ecp.sds;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.model.entity.ModelConfig;
import ca.uhn.fhir.jpa.starter.Application;
import ca.uhn.fhir.jpa.starter.JpaStarterWebsocketDispatcherConfig;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.util.BundleBuilder;

@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	classes = {
		Application.class,
		JpaStarterWebsocketDispatcherConfig.class
		},
	properties = {
		"spring.datasource.url=jdbc:h2:mem:dbr4",
		"hapi.fhir.fhir_version=r4"
		}
	)
@ActiveProfiles( "partition-aware-test")
public abstract class BaseSuppplementalDataStoreTest {

	@LocalServerPort
	private int port;

	@Autowired
	private ModelConfig myModelConfig;

	private String ourServerBase;
	private FhirContext ctx;

	@BeforeEach
	void setUp() {
		ctx = FhirContext.forR4();
		ctx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		ctx.getRestfulClientFactory().setSocketTimeout(1200 * 1000);
		ourServerBase = "http://localhost:" + port + "/fhir/";

		Set<String> baseUrls = new HashSet<>( myModelConfig.getTreatBaseUrlsAsLocal() ) ;
		baseUrls.add( ourServerBase ) ;
		myModelConfig.setTreatBaseUrlsAsLocal( baseUrls ) ;
		
		//client.registerInterceptor( new PartitionNameHeaderClientInterceptor( "FAKE_PARTITION" ) );
	}

	protected IGenericClient client() { return ctx.newRestfulGenericClient(ourServerBase) ; }

	protected BundleBuilder bundleBuilder() { return new BundleBuilder(ctx) ; }

}
