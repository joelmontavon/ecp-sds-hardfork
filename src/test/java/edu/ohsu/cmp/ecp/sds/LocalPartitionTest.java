package edu.ohsu.cmp.ecp.sds;

import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import ca.uhn.fhir.rest.client.api.IGenericClient;

public class LocalPartitionTest extends BaseSuppplementalDataStoreTest {

	@Test
	void canStoreAndRetrieveResourceInLocalPartition() {
		IGenericClient client = client() ;

		Patient pat = new Patient();
		IIdType patId = client.create().resource(pat).execute().getId();

		Questionnaire questionnaire = new Questionnaire() ; 
		IIdType questId = client.create().resource(questionnaire).execute().getId();

		QuestionnaireResponse questionnaireResponse  = new QuestionnaireResponse() ;
		questionnaireResponse.setSubject( new Reference(patId) ) ;
		questionnaireResponse.setQuestionnaire( questId.getValue() ) ;
		IIdType questRespId = client.create().resource(questionnaireResponse).execute().getId();

		QuestionnaireResponse readQuestResp = client.read().resource(QuestionnaireResponse.class).withId(questRespId).execute();

		Assertions.assertNotNull( readQuestResp );

	}

	@Test
	void canStoreAndRetrieveConditionResourceInLocalPartition() {
		IGenericClient client = client() ;
		
		Patient pat = new Patient();
		IIdType patId = client.create().resource(pat).execute().getId();
		
		Condition condition = new Condition() ;
		condition.setSubject( new Reference(patId) );
		IIdType conditionId = client.create().resource(condition).execute().getId();
		
		Condition readCondition = client.read().resource(Condition.class).withId(conditionId).execute();
		
		Assertions.assertNotNull( readCondition );
		
	}
}
