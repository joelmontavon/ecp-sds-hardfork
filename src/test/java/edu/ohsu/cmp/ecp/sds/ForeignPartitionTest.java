package edu.ohsu.cmp.ecp.sds;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.function.Predicate;

import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Linkage;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.util.BundleBuilder;

public class ForeignPartitionTest extends BaseSuppplementalDataStoreTest {

	private static final String FOREIGN_PARTITION_NAME = "http://my.ehr.org/fhir/R4/" ;

	private Patient initPatient( String id ) {
		Calendar cal = Calendar.getInstance() ;
		cal.set(0, 0, 0) ;
		Date birthDate = cal.getTime() ;
		
		Patient pat = new Patient();
		pat.setId( new IdType( "Patient", id ) ) ;
		pat.setBirthDate( birthDate ) ;
		
		return pat ;
	}

	private Condition initCondition( Patient subject, String id ) {
		return initCondition( subject.getIdElement(), id ) ;
	}
	
	private Condition initCondition( IIdType subject, String id ) {
		Condition condition = new Condition() ;
		condition.setId( new IdType( "Condition", id ) ) ;
		condition.setSubject( new Reference( subject ) ) ;
		return condition ;
	}

	@Test
	void canStoreAndRetrievePatientResourceInForeignPartition() {
		IGenericClient client = clientTargetingPartition( FOREIGN_PARTITION_NAME );

		Patient pat = initPatient( createTestSpecificId() ) ;
		IIdType patId = client.update().resource(pat).execute().getId();

		Patient readPatient = client.read().resource(Patient.class).withId(patId).execute();

		Assertions.assertNotNull( readPatient );

	}
	
	@Test
	void canEstablishLocalUserByStoringPatientResourceInForeignPartition() {
		IGenericClient client = clientTargetingPartition( FOREIGN_PARTITION_NAME );
		
		Patient pat = initPatient( createTestSpecificId() ) ;
		IIdType patId = client.update().resource(pat).execute().getId();
		
		IGenericClient clientLocal = client() ;
		List<Linkage> linkages = new TestClientSearch( clientLocal ).searchLinkagesWhereItemRefersTo(patId) ;
		
		assertThat( linkages.size(), equalTo(1) ) ;
	}
	
	@Test
	void canStoreAndRetrievePatientResourceInDisparateForeignPartitions() {
		IGenericClient client1 = clientTargetingPartition( FOREIGN_PARTITION_NAME );
		IGenericClient client2 = clientTargetingPartition( FOREIGN_PARTITION_NAME + "-2" );

		Patient pat1 = initPatient( createTestSpecificId() ) ;
		IIdType patId1 = client1.update().resource(pat1).execute().getId();

		Patient pat2 = initPatient( createTestSpecificId() ) ;
		IIdType patId2 = client2.update().resource(pat2).execute().getId();

		Patient readPatient1 = client1.read().resource(Patient.class).withId(patId1).execute();
		Patient readPatient2 = client2.read().resource(Patient.class).withId(patId2).execute();

		Assertions.assertNotNull( readPatient1 );
		Assertions.assertNotNull( readPatient2 );
		Assertions.assertNotEquals( readPatient2, readPatient1 );

	}

	@Test
	void canStoreAndRetrievePatientResourceWithNonExistantReferenceInForeignPartition() {
		IGenericClient client = clientTargetingPartition( FOREIGN_PARTITION_NAME );
		
		Patient pat = initPatient( "0123456789" ) ;
		pat.addGeneralPractitioner( new Reference( new IdType("Practitioner", "xyz") ) );
		IIdType patId = client.update().resource(pat).execute().getId();
		
		Patient readPatient = client.read().resource(Patient.class).withId(patId).execute();
		
		Assertions.assertNotNull( readPatient );
		
	}

	@Test
	void canStoreAndRetrieveConditionResourceInForeignPartition() {
		IGenericClient client = clientTargetingPartition( FOREIGN_PARTITION_NAME );
		
		Patient pat = initPatient( "0123456789" ) ;
		IIdType patId = client.update().resource(pat).execute().getId();
		
		Condition condition = initCondition( patId, "0123456789-001" ) ;
		IIdType conditionId = client.create().resource(condition).execute().getId();
		
		Condition readCondition = client.read().resource(Condition.class).withId(conditionId).execute();
		
		Assertions.assertNotNull( readCondition );
		
	}
	
	@Test
	void canStoreAndRetrieveBundleOfResourcesInForeignPartition() {
		IGenericClient client = clientTargetingPartition( FOREIGN_PARTITION_NAME );
		
		Patient pat = initPatient( "0123456789" ) ;
		Condition condition = initCondition( pat, "0123456789-001" ) ;

		BundleBuilder builder = bundleBuilder() ;
		builder.setType( "transaction" ) ;
		builder.addTransactionUpdateEntry( pat ) ;
		builder.addTransactionUpdateEntry( condition ) ;

		Bundle bundle = (Bundle)builder.getBundle() ;
		bundle.setId( new IdType("0123456789-TX") ) ;
		
		Bundle readBundle = client.transaction().withBundle( bundle ).execute();
		
		Assertions.assertNotNull( readBundle );

		Patient readPatient= client.read().resource(Patient.class).withId( pat.getId() ).execute();
		Assertions.assertNotNull( readPatient );
		
		Condition readCondition= client.read().resource(Condition.class).withId( condition.getId() ).execute();
		Assertions.assertNotNull( readCondition );

	}
}
