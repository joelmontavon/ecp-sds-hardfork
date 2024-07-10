package edu.ohsu.cmp.ecp.sds;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.RequestDefinition;
import org.mockserver.springtest.MockServerPort;
import org.mockserver.springtest.MockServerTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import ca.uhn.fhir.rest.client.api.IGenericClient;

@MockServerTest
@ActiveProfiles( { "auth-aware-test", "http-aware-test" } )
@TestPropertySource(properties = {
	"spring.security.oauth2.resourceserver.opaque-token.introspection-uri=http://localhost:${mockServerPort}/oauth2/introspect"
})
public class RelatedPersonAppClientTest extends BaseSuppplementalDataStoreTest {

	private MockServerClient mockServerClient ;
	
    @MockServerPort
    Integer mockServerPort;

	@Test
	void canStoreQuestionnaireWhereSubjectIsAuthorizedRelatedPerson() {

		String token = createTestSpecificId() ;
		IIdType authorizedRelatedPersonId = new IdType( "https://my.ehr.org/FHIR/R4", "Patient", createTestSpecificId(), null ) ;
		IIdType authorizedPatientId = new IdType( "https://my.ehr.org/FHIR/R4", "Patient", createTestSpecificId(), null ) ;

		Expectation[] oauth2Expectations =
			mockServerClient
				.when( oauth2IntrospectRequest(token) )
				.respond( oauth2IntrospectResponse(authorizedRelatedPersonId) )
				;
		
		mockServerClient.when( metadataRequest() ).respond( metadataResponse() );
		Expectation[] relatedPersonExpectations =
			mockServerClient
				.when( relatedPersonRequest(authorizedRelatedPersonId, token) )
				.respond( relatedPersonResponse(authorizedRelatedPersonId, authorizedPatientId) )
				;
		
		IGenericClient patientAppClient = authenticatingClient( token ) ;
		
		Reference authorizedPatient = new Reference( authorizedPatientId );
		String questId = createTestSpecificId();
		
		QuestionnaireResponse questionnaireResponse  = new QuestionnaireResponse() ;
		questionnaireResponse.setSubject( authorizedPatient ) ;
		questionnaireResponse.setQuestionnaire( questId ) ;
		IIdType questRespId = patientAppClient.create().resource(questionnaireResponse).execute().getId();
		Assertions.assertNotNull( questRespId );
		
		mockServerClient
			.verify(
				oauth2Expectations[0].getId(),
				relatedPersonExpectations[0].getId()
			);
	}

	private RequestDefinition oauth2IntrospectRequest( String token ) {
		return request()
			.withMethod( "POST" )
			.withPath( "/oauth2/introspect" )
			.withHeader( "Authorization", "Bearer " + token )
			.withBody( "token=" + token )
			;
	}
	
	private HttpResponse oauth2IntrospectResponse( IIdType relatedPersonId ) {
		String baseUrl = "http://localhost:" + mockServerPort + "/fhir/" ;
		IIdType relatedPerson = new IdType( baseUrl, "RelatedPerson", relatedPersonId.getIdPart(), null ) ;
		String jsonBody =
			String.format(
				"{ \"active\": true, \"sub\": \"%1$s\", \"exp\": %2$d }",
				relatedPerson.toString(),
				System.currentTimeMillis() + 60000
				); 
		return response()
			.withStatusCode( 200 )
			.withBody( json( jsonBody ) )
			;
		
	}
	
	private HttpRequest metadataRequest() {
		return request()
			.withMethod("GET")
			.withPath("/fhir/metadata")
			;
	}
	
	private HttpResponse metadataResponse() {
		String jsonBody =
			"{ \"resourceType\": \"CapabilityStatement\" }"
			;
		return response()
			.withStatusCode( 200 )
			.withBody( json( jsonBody ) )
			;
		
	}
	
	private RequestDefinition relatedPersonRequest( IIdType relatedPersonId, String token ) {
		return request()
			.withMethod( "GET" )
			.withPath( "/fhir/RelatedPerson/" + relatedPersonId.getIdPart() )
			.withHeader( "Authorization", "Bearer " + token )
			;
	}
	
	private HttpResponse relatedPersonResponse( IIdType relatedPersonId, IIdType patientId ) {
		String jsonBody =
			String.format(
				"{ \"resourceType\": \"RelatedPerson\", \"id\": \"%2$s\", \"patient\": { \"reference\": \"%1$s\" } }",
				patientId,
				relatedPersonId
				); 
		return response()
			.withStatusCode( 200 )
			.withBody( json( jsonBody ) )
			;
		
	}
	
}
