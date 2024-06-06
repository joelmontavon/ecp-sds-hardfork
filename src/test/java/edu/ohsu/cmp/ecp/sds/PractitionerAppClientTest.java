package edu.ohsu.cmp.ecp.sds;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.verify.VerificationTimes.exactly;

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
public class PractitionerAppClientTest extends BaseSuppplementalDataStoreTest {

	private static final String MOCK_SERVER_BASE_URL = "https://my.ehr.org/FHIR/R4" ;
	
	private MockServerClient mockServerClient ;

	@MockServerPort
	Integer mockServerPort;

	private IIdType storeNewQuestionnaireResponseForPatient( String patientId ) {
		String token = createTestSpecificId();
		mockServerClient
			.when( oauth2IntrospectRequest(token) )
			.respond( oauth2IntrospectResponse( new IdType( MOCK_SERVER_BASE_URL, "Patient", patientId, null) ) )
			;

		IGenericClient patientAppClient = authenticatingClient( token ) ;

		QuestionnaireResponse questionnaireResponse  = new QuestionnaireResponse() ;
		questionnaireResponse.setSubject( new Reference( new IdType( "Patient", patientId ) ) ) ;
		questionnaireResponse.setQuestionnaire( createTestSpecificId() ) ;

		IIdType questRespId = patientAppClient.create().resource(questionnaireResponse).execute().getId();

		return questRespId ;
	}

	@Test
	void cannotReadMultipleDistinctPatientsWithPatientAuthorization() {

		String aardvarkPatientId = createTestSpecificId();
		IIdType questRespAardvarkId = storeNewQuestionnaireResponseForPatient( aardvarkPatientId ) ;
		IIdType questRespBasiliskId = storeNewQuestionnaireResponseForPatient( createTestSpecificId() ) ;
		IIdType questRespCrocodileId = storeNewQuestionnaireResponseForPatient( createTestSpecificId() ) ;

		String token = createTestSpecificId() ;

		Expectation[] oauth2Expectations =
				mockServerClient
				.when( oauth2IntrospectRequest(token) )
				.respond( oauth2IntrospectResponse("Patient", aardvarkPatientId) )
				;

		IGenericClient patientAppClient = authenticatingClient( token ) ;

		QuestionnaireResponse questRespAardvark = patientAppClient.read().resource(QuestionnaireResponse.class).withId(questRespAardvarkId).execute();
		Assertions.assertNotNull( questRespAardvark );

		assertThrows( Exception.class, () -> {
			patientAppClient.read().resource(QuestionnaireResponse.class).withId(questRespBasiliskId).execute();
		}) ;

		assertThrows( Exception.class, () -> {
			patientAppClient.read().resource(QuestionnaireResponse.class).withId(questRespCrocodileId).execute();
		}) ;
	}

	@Test
	void canReadMultipleDistinctPatientsWithPractitionerAuthorization() {

		IIdType questRespAardvarkId = storeNewQuestionnaireResponseForPatient( createTestSpecificId() ) ;
		IIdType questRespBasiliskId = storeNewQuestionnaireResponseForPatient( createTestSpecificId() ) ;
		IIdType questRespCrocodileId = storeNewQuestionnaireResponseForPatient( createTestSpecificId() ) ;

		String token = createTestSpecificId() ;
		String authorizedPractitionerId = createTestSpecificId() ;

		Expectation[] oauth2Expectations =
			mockServerClient
				.when( oauth2IntrospectRequest(token) )
				.respond( oauth2IntrospectResponse("Practitioner", authorizedPractitionerId) )
				;

		mockServerClient
			.when( metadataRequest() )
			.respond( metadataResponse() )
			;

		IGenericClient practitionerAppClient = authenticatingClient( token ) ;

		QuestionnaireResponse questRespAardvark = practitionerAppClient.read().resource(QuestionnaireResponse.class).withId(questRespAardvarkId).execute();
		QuestionnaireResponse questRespBasilisk = practitionerAppClient.read().resource(QuestionnaireResponse.class).withId(questRespBasiliskId).execute();
		QuestionnaireResponse questRespCrocodile = practitionerAppClient.read().resource(QuestionnaireResponse.class).withId(questRespCrocodileId).execute();
		Assertions.assertNotNull( questRespAardvark );
		Assertions.assertNotNull( questRespBasilisk );
		Assertions.assertNotNull( questRespCrocodile );

		mockServerClient.verify( oauth2Expectations[0].getId(), exactly(3) ) ;
	}

	private RequestDefinition oauth2IntrospectRequest( String token ) {
		return request()
			.withMethod( "POST" )
			.withPath( "/oauth2/introspect" )
			.withHeader( "Authorization", "Bearer " + token )
			.withBody( "token=" + token )
			;
	}

	private HttpResponse oauth2IntrospectResponse( String userResourceType, String userId ) {
		String baseUrl = fhirServerlBase() ;
		IIdType user = new IdType( baseUrl, userResourceType, userId, null ) ;
		return oauth2IntrospectResponse(user) ;
	}

	private HttpResponse oauth2IntrospectResponse( IIdType user ) {
		String jsonBody =
			String.format(
				"{ \"active\": true, \"sub\": \"%1$s\", \"exp\": %2$d }",
				user.toString(),
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
}
