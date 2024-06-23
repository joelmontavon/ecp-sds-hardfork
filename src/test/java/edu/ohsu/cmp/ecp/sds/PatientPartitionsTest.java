package edu.ohsu.cmp.ecp.sds;

import static java.util.stream.Collectors.joining;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.function.Predicate;

import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Linkage;
import org.hl7.fhir.r4.model.Linkage.LinkageItemComponent;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import ca.uhn.fhir.jpa.starter.AppTestMockPrincipalRegistry;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;

@ActiveProfiles( "auth-aware-test")
public class PatientPartitionsTest extends BaseSuppplementalDataStoreTest {

	@Autowired
	AppTestMockPrincipalRegistry mockPrincipalRegistry ;
	
	private static final String FOREIGN_PARTITION_NAME = "http://my.ehr.org/fhir/R4/" ;
	private static final String FOREIGN_PARTITION_NAME_OTHER = "http://other.ehr.org/fhir/R4/" ;

	private IIdType authorizedPatientId ;
	private IGenericClient clientLocal ;
	private IGenericClient client ;
	private IGenericClient client2 ;
	
	@BeforeEach
	public void setupAuthorization() {
		authorizedPatientId = new IdType( FOREIGN_PARTITION_NAME, "Patient", createTestSpecificId(), null ) ;
		String token = mockPrincipalRegistry.register().principal( "MyPatient", authorizedPatientId.toString() ).token() ;

		clientLocal = authenticatingClient( token ) ;
		client = authenticatingClientTargetingPartition( token, FOREIGN_PARTITION_NAME ) ;
		client2 = authenticatingClientTargetingPartition( token, FOREIGN_PARTITION_NAME_OTHER ) ;
	}
	
	@Test
	@Disabled("OBSOLETE REQUIREMENT: see #PatientLinkingTest")
	void cannotStoreOtherResourceInForeignPartitionBeforePatient() {
		String otherPatientId = createTestSpecificId() ;

		Condition cnd = initCondition( new IdType( "Patient", otherPatientId), createTestSpecificId() ) ;
		
		ForbiddenOperationException ex =
				assertThrows(
					ForbiddenOperationException.class,
					() -> {
						client.update().resource(cnd).execute().getId();
					}
				);
		
		assertThat( ex.getMessage(), containsString("Access denied by rule: no access rules grant permission") ) ;
	}

	@Test
	void canStoreOtherResourceInForeignPartitionAfterPatient() {
		String otherPatientId = createTestSpecificId() ;
		
		Patient pat = initPatient( otherPatientId ) ;
		Condition cnd = initCondition( new IdType( "Patient", otherPatientId), createTestSpecificId() ) ;

		IIdType patId = client.update().resource(pat).execute().getId();
		Assertions.assertNotNull( patId ) ;
		
		IIdType cndId = client.update().resource(cnd).execute().getId();
		Assertions.assertNotNull( cndId ) ;
	}
	
	@Test
	void cannotReadOtherPatientResourceInForeignPartition() {
		String otherPatientId = createTestSpecificId() ;
		
		Patient pat = initPatient( otherPatientId ) ;
		
		ForbiddenOperationException ex =
			assertThrows(
				ForbiddenOperationException.class,
				() -> {
					client.read().resource(Patient.class).withId( pat.getIdElement() ).execute();
				}
			);
		
		assertThat( ex.getMessage(), containsString("Access denied by rule: no access rules grant permission") ) ;
	}
	
	@Test
	void canStoreAndRetrieveSelfPatientResourceInForeignPartition() {
		Patient pat = initPatient( authorizedPatientId ) ;
		IIdType patId = client.update().resource(pat).execute().getId();
		
		Patient readPatient = client.read().resource(Patient.class).withId(patId).execute();
		
		Assertions.assertNotNull( readPatient );
		
	}
	
	private static Predicate<LinkageItemComponent> refersTo( IIdType id ) {
		return (i) -> {
			if ( ! i.hasResource() )
				return false ;
			IIdType itemId = i.getResource().getReferenceElement() ;
			if ( itemId.hasVersionIdPart() && !itemId.getVersionIdPart().equals(id.getVersionIdPart()))
				return false ;
			if ( itemId.hasBaseUrl() && !itemId.getBaseUrl().equals(id.getBaseUrl()))
				return false ;
			if ( itemId.hasResourceType() && !itemId.getResourceType().equals(id.getResourceType()))
				return false ;
			if ( !itemId.hasIdPart() )
				return false ;
			return itemId.getIdPart().equals( id.getIdPart() ) ;
		} ;
	}
	
	private static LinkageItemComponent requireAlternateItemReferringTo( Linkage linkage, IIdType ref ) throws AssertionFailedError {
		String itemsDesc = linkage.getItem().stream().map( i -> String.format("%1$s: \"%2$s\"", i.getType(), i.getResource().getReference() ) ).collect( joining(", ", "[", "]") ) ;
		LinkageItemComponent alternateItem =
			linkage.getItem().stream()
				.filter( i -> i.getType() == Linkage.LinkageType.ALTERNATE )
				.filter( refersTo( ref ) )
				.findFirst()
				.orElseThrow( () -> new AssertionFailedError( "linkage did not contain an ALTERNATE item with id \"" + ref.getIdPart() + "\" among " + itemsDesc ) )
				;
		Assertions.assertNotNull( alternateItem ) ;
		return alternateItem ;
	}
	
	private static LinkageItemComponent requireSourceItemReferringTo( Linkage linkage ) throws AssertionFailedError {
		String itemsDesc = linkage.getItem().stream().map( i -> String.format("%1$s: \"%2$s\"", i.getType(), i.getResource().getReference() ) ).collect( joining(", ", "[", "]") ) ;
		LinkageItemComponent sourceItem =
			linkage.getItem().stream()
				.filter( i -> i.getType() == Linkage.LinkageType.SOURCE )
				.findFirst()
				.orElseThrow( () -> new AssertionFailedError( "linkage did not contain a SOURCE item among " + itemsDesc ) )
				;
		Assertions.assertNotNull( sourceItem ) ;
		return sourceItem ;
	}
	
	@Test
	@Disabled("OBSOLETE REQUIREMENT: see #PatientLinkingTest")
	void canEstablishLocalPatientBySearchingLocalPartition() {
		List<Linkage> linkages = new TestClientSearch( clientLocal ).searchLinkagesWhereItemRefersTo( authorizedPatientId ) ;
		
		assertThat( linkages.size(), greaterThanOrEqualTo(1) ) ;
	}
	
	private void establishForeignPartition( String foreignPartitionName ) {
		Patient pat = initPatient( authorizedPatientId.toUnqualifiedVersionless() ) ;
		client.update().resource(pat).execute().getId();
	}
	
	@Test
	@Disabled("OBSOLETE REQUIREMENT: see #PatientLinkingTest")
	void canEstablishLocalPatientBySearchingForeignPartition() {
		establishForeignPartition( FOREIGN_PARTITION_NAME ) ;
		
		client.search().forResource( "Condition" ).where( Condition.SUBJECT.hasId(authorizedPatientId) ).execute();
		
		List<Linkage> linkages = new TestClientSearch( clientLocal ).searchLinkagesWhereItemRefersTo( authorizedPatientId ) ;
		
		assertThat( linkages.size(), greaterThanOrEqualTo(1) ) ;
		
		Linkage linkage = linkages.get(0) ;
		
		requireAlternateItemReferringTo( linkage, authorizedPatientId ) ;
		requireSourceItemReferringTo( linkage ) ;
	}
	
	@Test
	@Disabled("OBSOLETE REQUIREMENT: see #PatientLinkingTest")
	void cannotSearchingForeignPartitionThatIsEmpty() {
		ResourceNotFoundException ex =
			assertThrows(
					ResourceNotFoundException.class,
				() -> {
					client.search().forResource( "Condition" ).where( Condition.SUBJECT.hasId(authorizedPatientId) ).execute();
				}
			);
		
		assertThat( ex.getMessage(), containsString("Partition name \"" + FOREIGN_PARTITION_NAME + "\" is not valid") ) ;
	}
	
	@Test
	@Disabled("OBSOLETE REQUIREMENT: see #PatientLinkingTest")
	void canEstablishLocalPatientByStoringSelfPatientResourceInForeignPartition() {
		Patient pat = initPatient( authorizedPatientId ) ;
		IIdType patId = client.update().resource(pat).execute().getId();
		
		List<Linkage> linkages = new TestClientSearch( clientLocal ).searchLinkagesWhereItemRefersTo(patId) ;
		
		assertThat( linkages.size(), greaterThanOrEqualTo(1) ) ;
		
		Linkage linkage = linkages.get(0) ;
		
		LinkageItemComponent alternateItem = requireAlternateItemReferringTo( linkage, patId ) ;
		LinkageItemComponent sourceItem = requireSourceItemReferringTo( linkage ) ;
		
		IIdType localPatientId = sourceItem.getResource().getReferenceElement() ;
		Patient readLocalPatient = clientLocal.read().resource(Patient.class).withId(localPatientId).execute();
		
		Assertions.assertNotNull( readLocalPatient ) ;
	}
	
	@Test
	void canStoreAndRetrieveSelfPatientResourcesInDisparateForeignPartitions() {
		String otherPatientId = createTestSpecificId() ;
		
		Patient pat1 = initPatient( authorizedPatientId ) ;
		IIdType patId1 = client.update().resource(pat1).execute().getId();
		
		Patient pat2 = initPatient( otherPatientId ) ;
		IIdType patId2 = client2.update().resource(pat2).execute().getId();
		
		Patient readPatient1 = client.read().resource(Patient.class).withId(patId1).execute();
		Patient readPatient2 = client2.read().resource(Patient.class).withId(patId2).execute();
		
		Assertions.assertNotNull( readPatient1 );
		Assertions.assertNotNull( readPatient2 );
		
	}
	
}
