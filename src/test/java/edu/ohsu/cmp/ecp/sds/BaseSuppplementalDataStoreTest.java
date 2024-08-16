package edu.ohsu.cmp.ecp.sds;

import static java.util.stream.Collectors.toList;

import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.model.entity.StorageSettings;
import ca.uhn.fhir.jpa.starter.Application;
import ca.uhn.fhir.jpa.starter.JpaStarterWebsocketDispatcherConfig;
import ca.uhn.fhir.parser.IParser;
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
@org.springframework.transaction.annotation.Transactional
public abstract class BaseSuppplementalDataStoreTest {

	@LocalServerPort
	private int port;

	@Autowired
	private SupplementalDataStoreProperties sdsProperties ;

	@Autowired
	private StorageSettings myStorageSettings;

	private String ourServerBase;
	private FhirContext ctx;

	protected IParser jsonResourceParser() {
		return this.ctx.newJsonParser() ;
	}

	@BeforeEach
	void setUp() {
		ctx = FhirContext.forR4();
		ctx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		ctx.getRestfulClientFactory().setSocketTimeout(1200 * 1000);
		ourServerBase = "http://localhost:" + port + "/fhir/";

		Set<String> baseUrls = new HashSet<>( myStorageSettings.getTreatBaseUrlsAsLocal() ) ;
		baseUrls.add( ourServerBase ) ;
		myStorageSettings.setTreatBaseUrlsAsLocal( baseUrls ) ;
	}

	protected String fhirServerlBase() {
		return ourServerBase ;
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
				testName.replaceAll("[^-.A-Za-z0-9]",".").substring(0, Math.min(testName.length(), 16)),
				testNameHashCode
			) ;
	}
	
	protected IGenericClient client() { return ctx.newRestfulGenericClient(ourServerBase) ; }
	
	protected IGenericClient clientTargetingPartition( String partitionName ) {
		IGenericClient client = client() ;
		client.registerInterceptor( new PartitionNameHeaderClientInterceptor( sdsProperties.getPartition().getHttpHeader(), partitionName ) );
		return client ;

	}

	protected IGenericClient authenticatingClient( String token ) {
		IGenericClient client = client() ;
		client.registerInterceptor( new BearerTokenAuthInterceptor( token ) );
		return client ;
	}

	protected IGenericClient authenticatingClientTargetingPartition( String token, String partitionName ) {
		IGenericClient client = client() ;
		client.registerInterceptor( new PartitionNameHeaderClientInterceptor( sdsProperties.getPartition().getHttpHeader(), partitionName ) );
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

	protected Patient initPatient( String id ) {
		return initPatient( new IdType( "Patient", id ) ) ;
	}

	protected Patient initPatient( IIdType id ) {
		Calendar cal = Calendar.getInstance() ;
		cal.set(0, 0, 0) ;
		Date birthDate = cal.getTime() ;
		
		Patient pat = new Patient();
		pat.setId( id ) ;
		pat.setBirthDate( birthDate ) ;
		
		return pat ;
	}

	protected Condition initCondition( Patient subject, String id ) {
		return initCondition( subject.getIdElement(), id ) ;
	}
	
	protected Condition initCondition( IIdType subject, String id ) {
		Condition condition = new Condition() ;
		condition.setId( new IdType( "Condition", id ) ) ;
		condition.setSubject( new Reference( subject ) ) ;
		return condition ;
	}

}
