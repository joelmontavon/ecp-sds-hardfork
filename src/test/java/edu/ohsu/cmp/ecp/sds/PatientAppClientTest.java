package edu.ohsu.cmp.ecp.sds;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestComponent;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestSecurityComponent;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Type;
import org.hl7.fhir.r4.model.UriType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import ca.uhn.fhir.jpa.starter.AppTestMockPrincipalRegistry;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import junit.framework.AssertionFailedError;

@ActiveProfiles( "auth-aware-test")
public class PatientAppClientTest extends BaseSuppplementalDataStoreTest {

	@Autowired
	AppTestMockPrincipalRegistry mockPrincipalRegistry ;
	
	private static final String FOREIGN_PARTITION_NAME = "http://my.ehr.org/fhir/R4/" ;

	@Test
	void canFetchOAuth2Metadata() {
		IGenericClient patientAppClient = client() ;

		CapabilityStatement cap = patientAppClient.capabilities().ofType( CapabilityStatement.class ).execute() ;

		Assertions.assertNotNull( cap );

		assertThat( cap.getRest().size(), greaterThanOrEqualTo(1) ) ;
		CapabilityStatementRestComponent rest = cap.getRest().get(0) ;

		CapabilityStatementRestSecurityComponent security = rest.getSecurity() ;

		Extension extOauthUris =
			security.getExtension().stream()
				.filter( ext -> "http://fhir-registry.smarthealthit.org/StructureDefinition/oauth-uris".equals(ext.getUrl()) )
				.findFirst()
				.orElseThrow( () -> new AssertionFailedError("no REST extension for \"http://fhir-registry.smarthealthit.org/StructureDefinition/oauth-uris\"") )
				;
		Extension extAuthorize =
			extOauthUris.getExtension().stream()
				.filter( ext -> "authorize".equals(ext.getUrl()) )
				.findFirst()
				.orElseThrow( () -> new AssertionFailedError("no REST extension for \"authorize\"") )
				;
		Extension extToken =
			extOauthUris.getExtension().stream()
				.filter( ext -> "token".equals(ext.getUrl()) )
				.findFirst()
				.orElseThrow( () -> new AssertionFailedError("no REST extension for \"token\"") )
				;

		Type authorizeValue = extAuthorize.getValue();
		assertThat( authorizeValue, instanceOf(UriType.class) );
		assertThat( ((UriType)authorizeValue).asStringValue(), equalTo("http://my.ehr.org/oauth2/authorize") ) ;
		
		Type tokenValue = extToken.getValue();
		assertThat( tokenValue, instanceOf(UriType.class) );
		assertThat( ((UriType)tokenValue).asStringValue(), equalTo("http://my.ehr.org/oauth2/token") ) ;
	}
	
	@Test
	void canStoreQuestionnaireWhereSubjectIsAuthorizedPartientWithoutAdditionalSetup() {
		String authorizedPatientId = createTestSpecificId() ;
		String token = mockPrincipalRegistry.register().principal( "MyPatient", "Patient/" + authorizedPatientId ).token() ;
		
		IGenericClient patientAppClient = authenticatingClient( token ) ;
		
		Reference authorizedPatient = new Reference( new IdType( "Patient", authorizedPatientId ) );
		String questId = createTestSpecificId();
		
		QuestionnaireResponse questionnaireResponse  = new QuestionnaireResponse() ;
		questionnaireResponse.setSubject( authorizedPatient ) ;
		questionnaireResponse.setQuestionnaire( questId ) ;
		IIdType questRespId = patientAppClient.create().resource(questionnaireResponse).execute().getId();
		
		QuestionnaireResponse readQuestResp = patientAppClient.read().resource(QuestionnaireResponse.class).withId(questRespId).execute();
		
		Assertions.assertNotNull( readQuestResp );
	}
	
	@Test
	void cannotStoreQuestionnaireWhereSubjectIsNotAuthorizedPatient() {
		String authorizedPatientId = createTestSpecificId() ;
		String token = mockPrincipalRegistry.register().principal( "MyPatient", "Patient/" + authorizedPatientId ).token() ;
		
		IGenericClient patientAppClient = authenticatingClient( token ) ;
		
		Reference nonAuthorizedPatient = new Reference( new IdType( "Patient", createTestSpecificId() ) );
		String questId = createTestSpecificId();
		
		QuestionnaireResponse questionnaireResponse  = new QuestionnaireResponse() ;
		questionnaireResponse.setSubject( nonAuthorizedPatient ) ;
		questionnaireResponse.setQuestionnaire( questId ) ;
		
		ForbiddenOperationException exception =
			assertThrows( ForbiddenOperationException.class, () -> {
				patientAppClient.create().resource(questionnaireResponse).execute().getId();
			} );
		
		assertThat( exception.getMessage(), containsString( "Access denied by rule: everything else" ) ) ;
	}
}
