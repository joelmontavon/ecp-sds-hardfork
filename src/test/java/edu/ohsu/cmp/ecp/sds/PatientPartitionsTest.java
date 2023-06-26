package edu.ohsu.cmp.ecp.sds;

import static java.util.stream.Collectors.joining;
import static org.hamcrest.CoreMatchers.containsString;
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

	@Test
	void cannotStoreOtherResourceInForeignPartitionBeforePatient() {
		String authorizedPatientId = createTestSpecificId() ;
		String otherPatientId = createTestSpecificId() ;
		String token = mockPrincipalRegistry.register().principal( "MyPatient", "Patient/" + authorizedPatientId ).token() ;
		
		IGenericClient client = authenticatingClientTargetingPartition( token, FOREIGN_PARTITION_NAME ) ;

		Condition cnd = initCondition( new IdType( "Patient", otherPatientId), createTestSpecificId() ) ;
		
		ForbiddenOperationException ex =
				assertThrows(
					ForbiddenOperationException.class,
					() -> {
						client.update().resource(cnd).execute().getId();
					}
				);
		
		assertThat( ex.getMessage(), containsString("Access denied by rule: everything else") ) ;
	}

	@Test
	void canStoreOtherResourceInForeignPartitionAfterPatient() {
		String authorizedPatientId = createTestSpecificId() ;
		String otherPatientId = createTestSpecificId() ;
		String token = mockPrincipalRegistry.register().principal( "MyPatient", "Patient/" + authorizedPatientId ).token() ;
		
		IGenericClient client = authenticatingClientTargetingPartition( token, FOREIGN_PARTITION_NAME ) ;
		
		Patient pat = initPatient( otherPatientId ) ;
		Condition cnd = initCondition( new IdType( "Patient", otherPatientId), createTestSpecificId() ) ;

		IIdType patId = client.update().resource(pat).execute().getId();
		Assertions.assertNotNull( patId ) ;
		
		IIdType cndId = client.update().resource(cnd).execute().getId();
		Assertions.assertNotNull( cndId ) ;
	}
	
	@Test
	void cannotReadOtherPatientResourceInForeignPartition() {
		String authorizedPatientId = createTestSpecificId() ;
		String otherPatientId = createTestSpecificId() ;
		String token = mockPrincipalRegistry.register().principal( "MyPatient", "Patient/" + authorizedPatientId ).token() ;
		
		IGenericClient client = authenticatingClientTargetingPartition( token, FOREIGN_PARTITION_NAME ) ;
		
		Patient pat = initPatient( otherPatientId ) ;
		
		ForbiddenOperationException ex =
			assertThrows(
				ForbiddenOperationException.class,
				() -> {
					client.read().resource(Patient.class).withId( pat.getIdElement() ).execute();
				}
			);
		
		assertThat( ex.getMessage(), containsString("Access denied by rule: everything else") ) ;
	}
	
	@Test
	void canStoreAndRetrieveSelfPatientResourceInForeignPartition() {
		String authorizedPatientId = createTestSpecificId() ;
		String token = mockPrincipalRegistry.register().principal( "MyPatient", "Patient/" + authorizedPatientId ).token() ;
		
		IGenericClient client = authenticatingClientTargetingPartition( token, FOREIGN_PARTITION_NAME ) ;
		
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
	void canEstablishLocalPatientBySearchingLocalPartition() {
		String authorizedPatientId = createTestSpecificId() ;
		String token = mockPrincipalRegistry.register().principal( "MyPatient", "Patient/" + authorizedPatientId ).token() ;
		
		IIdType authorizedNonLocalId = new IdType( "Patient", authorizedPatientId ) ;
		
		IGenericClient clientLocal = authenticatingClient( token ) ;
		
		List<Linkage> linkages = new TestClientSearch( clientLocal ).searchLinkagesWhereItemRefersTo( authorizedNonLocalId ) ;
		
		assertThat( linkages.size(), greaterThanOrEqualTo(1) ) ;
		
		Linkage linkage = linkages.get(0) ;

		requireAlternateItemReferringTo( linkage, authorizedNonLocalId ) ;
		requireSourceItemReferringTo( linkage ) ;
	}
	
	private void establishForeignPartition( String foreignPartitionName ) {
		String authorizedPatientId = createTestSpecificId() ;
		String token = mockPrincipalRegistry.register().principal( "MyPatient", "Patient/" + authorizedPatientId ).token() ;

		IGenericClient clientForeign = authenticatingClientTargetingPartition( token, foreignPartitionName ) ;
		
		Patient pat = initPatient( authorizedPatientId ) ;
		clientForeign.update().resource(pat).execute().getId();
	}
	
	@Test
	void canEstablishLocalPatientBySearchingForeignPartition() {
		establishForeignPartition( FOREIGN_PARTITION_NAME ) ;
		
		String authorizedPatientId = createTestSpecificId() ;
		String token = mockPrincipalRegistry.register().principal( "MyPatient", "Patient/" + authorizedPatientId ).token() ;
		
		IIdType authorizedNonLocalId = new IdType( "Patient", authorizedPatientId ) ;

		IGenericClient clientForeign = authenticatingClientTargetingPartition( token, FOREIGN_PARTITION_NAME ) ;

		clientForeign.search().forResource( "Condition" ).where( Condition.SUBJECT.hasId(authorizedNonLocalId) ).execute();
		
		IGenericClient clientLocal = authenticatingClient( token ) ;
		
		List<Linkage> linkages = new TestClientSearch( clientLocal ).searchLinkagesWhereItemRefersTo( authorizedNonLocalId ) ;
		
		assertThat( linkages.size(), greaterThanOrEqualTo(1) ) ;
		
		Linkage linkage = linkages.get(0) ;
		
		requireAlternateItemReferringTo( linkage, authorizedNonLocalId ) ;
		requireSourceItemReferringTo( linkage ) ;
	}
	
	@Test
	void cannotSearchingForeignPartitionThatIsEmpty() {
		String authorizedPatientId = createTestSpecificId() ;
		String token = mockPrincipalRegistry.register().principal( "MyPatient", "Patient/" + authorizedPatientId ).token() ;
		
		IIdType authorizedNonLocalId = new IdType( "Patient", authorizedPatientId ) ;
		
		IGenericClient clientForeign = authenticatingClientTargetingPartition( token, FOREIGN_PARTITION_NAME ) ;
		
		ResourceNotFoundException ex =
			assertThrows(
					ResourceNotFoundException.class,
				() -> {
					clientForeign.search().forResource( "Condition" ).where( Condition.SUBJECT.hasId(authorizedNonLocalId) ).execute();
				}
			);
		
		assertThat( ex.getMessage(), containsString("Partition name \"" + FOREIGN_PARTITION_NAME + "\" is not valid") ) ;
	}
	
	@Test
	void canEstablishLocalPatientByStoringSelfPatientResourceInForeignPartition() {
		String authorizedPatientId = createTestSpecificId() ;
		String token = mockPrincipalRegistry.register().principal( "MyPatient", "Patient/" + authorizedPatientId ).token() ;
		
		IGenericClient clientForeign = authenticatingClientTargetingPartition( token, FOREIGN_PARTITION_NAME ) ;
		
		Patient pat = initPatient( authorizedPatientId ) ;
		IIdType patId = clientForeign.update().resource(pat).execute().getId();
		
		IGenericClient clientLocal = authenticatingClient( token ) ;
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
		String authorizedPatientId = createTestSpecificId() ;
		String otherPatientId = createTestSpecificId() ;
		String token = mockPrincipalRegistry.register().principal( "MyPatient", "Patient/" + authorizedPatientId ).token() ;
		
		IGenericClient client1 = authenticatingClientTargetingPartition( token, FOREIGN_PARTITION_NAME ) ;
		IGenericClient client2 = authenticatingClientTargetingPartition( token, FOREIGN_PARTITION_NAME_OTHER ) ;
		
		Patient pat1 = initPatient( authorizedPatientId ) ;
		IIdType patId1 = client1.update().resource(pat1).execute().getId();
		
		Patient pat2 = initPatient( otherPatientId ) ;
		IIdType patId2 = client2.update().resource(pat2).execute().getId();
		
		Patient readPatient1 = client1.read().resource(Patient.class).withId(patId1).execute();
		Patient readPatient2 = client2.read().resource(Patient.class).withId(patId2).execute();
		
		Assertions.assertNotNull( readPatient1 );
		Assertions.assertNotNull( readPatient2 );
		
	}
	
}
