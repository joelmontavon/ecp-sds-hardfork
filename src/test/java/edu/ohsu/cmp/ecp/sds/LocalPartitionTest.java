package edu.ohsu.cmp.ecp.sds;

import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import ca.uhn.fhir.jpa.starter.AppTestMockPrincipalRegistry;
import ca.uhn.fhir.rest.client.api.IGenericClient;

@ActiveProfiles( "auth-aware-test")
public class LocalPartitionTest extends BaseSuppplementalDataStoreTest {

	@Autowired
	AppTestMockPrincipalRegistry mockPrincipalRegistry ;

	private static final String FOREIGN_PARTITION_NAME = "http://my.ehr.org/fhir/R4/" ;

	private IGenericClient client ;
	
	@BeforeEach
	public void setup() {
		IIdType authorizedPatientId = new IdType( FOREIGN_PARTITION_NAME, "Patient", createTestSpecificId(), null ) ;
		String token = mockPrincipalRegistry.register().principal( "MyPatient", authorizedPatientId.toString() ).token() ;

		client = authenticatingClient( token ) ;
	}
	
	@Test
	void canStoreAndRetrieveResourceInLocalPartition() {
		Patient pat = new Patient();
		IIdType patId = client.create().resource(pat).execute().getId();

		QuestionnaireResponse questionnaireResponse  = new QuestionnaireResponse() ;
		questionnaireResponse.setSubject( new Reference(patId) ) ;
		IIdType questRespId = client.create().resource(questionnaireResponse).execute().getId();

		QuestionnaireResponse readQuestResp = client.read().resource(QuestionnaireResponse.class).withId(questRespId).execute();

		Assertions.assertNotNull( readQuestResp );

	}

	@Test
	void canStoreAndRetrieveConditionResourceInLocalPartition() {
		Patient pat = new Patient();
		IIdType patId = client.create().resource(pat).execute().getId();
		
		Condition condition = new Condition() ;
		condition.setSubject( new Reference(patId) );
		IIdType conditionId = client.create().resource(condition).execute().getId();
		
		Condition readCondition = client.read().resource(Condition.class).withId(conditionId).execute();
		
		Assertions.assertNotNull( readCondition );
		
	}
}
