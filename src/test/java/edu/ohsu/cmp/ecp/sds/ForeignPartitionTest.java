package edu.ohsu.cmp.ecp.sds;

import static java.util.Arrays.asList;

import java.util.Calendar;
import java.util.Date;

import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import ca.uhn.fhir.rest.client.api.IGenericClient;

public class ForeignPartitionTest extends BaseSuppplementalDataStoreTest {

	private static final String FOREIGN_PARTITION_NAME = "http://my.ehr.org/fhir/R4/" ;

	private Patient initPatient( String id ) {
		Calendar cal = Calendar.getInstance() ;
		cal.set(0, 0, 0) ;
		Date birthDate = cal.getTime() ;
		
		Patient pat = new Patient();
		pat.setId( new IdType( "0123456789" ) ) ;
		pat.setBirthDate( birthDate ) ;
		
		return pat ;
	}

	private Condition initCondition( Patient subject, String id ) {
		return initCondition( subject.getIdElement(), id ) ;
	}
	
	private Condition initCondition( IIdType subject, String id ) {
		Condition condition = new Condition() ;
		condition.setId( new IdType( id ) ) ;
		condition.setSubject( new Reference( subject ) ) ;
		return condition ;
	}
	
	@Test
	void canStoreAndRetrievePatientResourceInForeignPartition() {
		IGenericClient client = client() ;
		client.registerInterceptor( new PartitionNameHeaderClientInterceptor( FOREIGN_PARTITION_NAME ) );

		Patient pat = initPatient( "0123456789" ) ;
		IIdType patId = client.update().resource(pat).execute().getId();

		Patient readPatient = client.read().resource(Patient.class).withId(patId).execute();

		Assertions.assertNotNull( readPatient );

	}
	
	@Test
	void canStoreAndRetrievePatientResourceWithNonExistantReferenceInForeignPartition() {
		IGenericClient client = client() ;
		client.registerInterceptor( new PartitionNameHeaderClientInterceptor( FOREIGN_PARTITION_NAME ) );
		
		Patient pat = initPatient( "0123456789" ) ;
		pat.addGeneralPractitioner( new Reference( new IdType("Practitioner", "xyz") ) );
		IIdType patId = client.update().resource(pat).execute().getId();
		
		Patient readPatient = client.read().resource(Patient.class).withId(patId).execute();
		
		Assertions.assertNotNull( readPatient );
		
	}
	
	@Test
	void canStoreAndRetrieveConditionResourceInForeignPartition() {
		IGenericClient client = client() ;
		client.registerInterceptor( new PartitionNameHeaderClientInterceptor( FOREIGN_PARTITION_NAME ) );
		
		Patient pat = initPatient( "0123456789" ) ;
		IIdType patId = client.update().resource(pat).execute().getId();
		
		Condition condition = initCondition( patId, "0123456789-001" ) ;
		IIdType conditionId = client.create().resource(condition).execute().getId();
		
		Condition readCondition = client.read().resource(Condition.class).withId(conditionId).execute();
		
		Assertions.assertNotNull( readCondition );
		
	}
	
	@Test
	@Disabled("not tested yet")
	void canStoreAndRetrieveBundleOfResourcesInForeignPartition() {
		IGenericClient client = client() ;
		client.registerInterceptor( new PartitionNameHeaderClientInterceptor( FOREIGN_PARTITION_NAME ) );
		
		Patient pat = initPatient( "0123456789" ) ;
		Condition condition = initCondition( pat, "0123456789-001" ) ;
		
		Bundle bundle = new Bundle() ;
		bundle.setType( Bundle.BundleType.TRANSACTION ) ;
		bundle.addEntry().setResource( pat ).getRequest().setMethod( HTTPVerb.PUT ) ;
		bundle.addEntry().setResource( condition ).getRequest().setMethod( HTTPVerb.PUT ) ;
		
		Bundle readBundle = client.transaction().withBundle( bundle ).execute();
		
		Assertions.assertNotNull( readBundle );
		
	}
}
