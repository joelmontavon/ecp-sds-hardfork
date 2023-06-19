package edu.ohsu.cmp.ecp.sds;

import static java.util.stream.Collectors.toList;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Linkage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
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
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
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
	}

	private int testSpecificIdCount ;
	private String testSpecificIdBase ;
	
	protected String createTestSpecificId() {
		return String.format( "%1$s-%2$03d", testSpecificIdBase, testSpecificIdCount++ ) ;
	}
	
	@BeforeEach
	public void resetTestSpecificIdComponents(TestInfo testInfo) {
		String testName = testInfo.getTestMethod().map( Method::getName ).orElse("TESTNAME") ;
		int testNameHashCode = testInfo.getDisplayName().hashCode() ;
		testSpecificIdCount = 0 ;
		testSpecificIdBase =
			String.format(
				"%1$s-%2$08x",
				testName.substring(0, Math.min(testName.length(), 16)),
				testNameHashCode
			) ;
	}
	
	protected IGenericClient client() { return ctx.newRestfulGenericClient(ourServerBase) ; }
	
	protected IGenericClient clientTargetingPartition( String partitionName ) {
		IGenericClient client = client() ;
		client.registerInterceptor( new PartitionNameHeaderClientInterceptor( partitionName ) );
		return client ;

	}

	protected IGenericClient authenticatingClient( String token ) {
		IGenericClient client = client() ;
		client.registerInterceptor( new BearerTokenAuthInterceptor( token ) );
		return client ;
	}

	protected IGenericClient authenticatingClientTargetingPartition( String token, String partitionName ) {
		IGenericClient client = client() ;
		client.registerInterceptor( new PartitionNameHeaderClientInterceptor( partitionName ) );
		client.registerInterceptor( new BearerTokenAuthInterceptor( token ) );
		return client ;
	}
	
	protected BundleBuilder bundleBuilder() { return new BundleBuilder(ctx) ; }

	protected <T extends IBaseResource> List<T> resourcesFromBundle( Bundle bundle, Class<T> resourceType ) {
		List<T> resources =
			bundle.getEntry().stream()
				.filter( Bundle.BundleEntryComponent::hasResource )
				.map( Bundle.BundleEntryComponent::getResource )
				.filter( resourceType::isInstance )
				.map( resourceType::cast )
				.collect( toList() )
				;
		return resources ;

	}
}
