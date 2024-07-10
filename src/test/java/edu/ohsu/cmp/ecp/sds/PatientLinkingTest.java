package edu.ohsu.cmp.ecp.sds;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Goal;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Linkage;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import ca.uhn.fhir.jpa.starter.AppTestMockPrincipalRegistry;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import edu.ohsu.cmp.ecp.sds.base.FhirResourceComparison;

@ActiveProfiles( "auth-aware-test")
public class PatientLinkingTest extends BaseSuppplementalDataStoreTest {

	@Autowired
	AppTestMockPrincipalRegistry mockPrincipalRegistry ;

	private static final String FOREIGN_PARTITION_NAME = "http://my.ehr.org/fhir/R4/" ;
	private static final String FOREIGN_PARTITION_NAME_OTHER = "http://other.ehr.org/fhir/R4/" ;

	private IIdType authorizedPatientId ;
	private IIdType otherPatientId ;
	private IGenericClient clientLocal ;
	private IGenericClient clientForeign1 ;
	private IGenericClient clientForeign2 ;

	@BeforeEach
	public void setupAuthorization() {
		authorizedPatientId = new IdType( FOREIGN_PARTITION_NAME, "Patient", createTestSpecificId(), null ) ;
		String token = mockPrincipalRegistry.register().principal( "MyPatient", authorizedPatientId.toString() ).token() ;

		otherPatientId = new IdType( FOREIGN_PARTITION_NAME_OTHER, "Patient", createTestSpecificId(), null ) ;

		clientLocal = authenticatingClient( token ) ;
		clientForeign1 = authenticatingClientTargetingPartition( token, FOREIGN_PARTITION_NAME ) ;
		clientForeign2 = authenticatingClientTargetingPartition( token, FOREIGN_PARTITION_NAME_OTHER ) ;
	}

	/*
	 *  setup: no resources
	 * action: search for a local QuestionnaireResponse belonging to foreign (authorized) patient
	 * result: - still no linkages in local partition
	 *         - still no patient in foreign (authorized) partition
	 */
	@Test
	void canSearchForLocalResourcesBelongingToAuthorizedPatientWithoutGeneratingNewResources() {
		Bundle questionnaireResponseBundle =
			clientLocal.search()
				.forResource( QuestionnaireResponse.class )
				.where( QuestionnaireResponse.SUBJECT.hasId(authorizedPatientId) )
				.returnBundle(Bundle.class)
				.execute()
				;
		assertThat( questionnaireResponseBundle, notNullValue() ) ;

		assertCompartmentUnclaimedAndUnlinked( clientForeign1, authorizedPatientId ) ;
	}

	/*
	 *  setup: no resources
	 * action: search for a local Linkages belonging to foreign (authorized) patient
	 * result: - still no linkages in local partition
	 *         - still no patient in foreign (authorized) partition
	 */
	@Test
	void canSearchForLinkagesForAuthorizedPatientWithoutGeneratingNewResources() {
		Bundle linkageBundle =
			clientLocal.search()
				.forResource( Linkage.class )
				.where( Linkage.ITEM.hasId(authorizedPatientId.toUnqualifiedVersionless()) )
				.returnBundle(Bundle.class)
				.execute()
				;
		assertThat( linkageBundle.getEntry(), empty() ) ;
		
		assertCompartmentUnclaimedAndUnlinked( clientForeign1, authorizedPatientId ) ;
	}
	
	/*
	 *  setup: no resources
	 * action: search for a local Linkages belonging to foreign (authorized) patient
	 * result: - still no linkages in local partition
	 *         - still no patient in foreign (authorized) partition
	 */
	@Test
	void canSearchForLinkagesForAnyPatientWithoutGeneratingNewResources() {
		Bundle linkageBundle =
			clientLocal.search()
				.forResource( Linkage.class )
				.returnBundle(Bundle.class)
				.execute()
				;
		assertThat( linkageBundle.getEntry(), empty() ) ;
		
		assertCompartmentUnclaimedAndUnlinked( clientForeign1, authorizedPatientId ) ;
	}
	
	/*
	 *  setup: no resources
	 * action: search for a local QuestionnaireResponse belonging to other patient
	 * result: - forbidden
	 *         - still no linkages in local partition
	 *         - still no patient in foreign (authorized) partition
	 *         - still no patient in other partition
	 */
	@Test
	void cannotSearchForLocalResourcesBelongingToOtherPatient() {
		assertThrows( ForbiddenOperationException.class, () -> {
			clientLocal.search()
				.forResource( QuestionnaireResponse.class )
				.where( QuestionnaireResponse.SUBJECT.hasId(otherPatientId) )
				.returnBundle(Bundle.class)
				.execute()
				;
		});

		assertCompartmentUnclaimedAndUnlinked( clientForeign1, authorizedPatientId ) ;
		assertCompartmentUnclaimedAndUnlinked( clientForeign2, otherPatientId ) ;
	}

	/*
	 *  setup: no resources
	 * action: store the foreign (authorized) patient
	 * result: - patient is created in foreign (authorized) partition
	 *         - still no linkages in local partition
	 */
	@Test
	void canStoreForeignAuthorizedPatientWithoutGeneratingNewResources() {
		Patient patient = patient( authorizedPatientId ) ;
		clientForeign1.update().resource( patient ).execute();

		assertCompartmentClaimed( clientForeign1, authorizedPatientId ) ;
		assertLinkagesAbsent( authorizedPatientId ) ;
	}
	
	/*
	 *  setup: no resources
	 * action: store the local patient
	 * result: - local patient is created
	 *         - patient is created in foreign (authorized) partition as a STUB
	 *         - linkage is created local patient -> foreign patient
	 */
	@Test
	void canStoreLocalPatient() {
		Patient patient = new Patient() ;
		MethodOutcome outcome = clientLocal.create().resource( patient ).execute();
		assertThat( outcome.getCreated(), equalTo(true) ) ;
		assertThat( outcome.getId(), notNullValue() ) ;

		IIdType localPatientId = outcome.getId() ;
		
		assertCompartmentClaimed( clientLocal, localPatientId ) ;
		assertCompartmentClaimed( clientForeign1, authorizedPatientId ) ;
		assertLinkagesPresent( authorizedPatientId ) ;
	}

	/*
	Scenario 1: Create Local Patient First
	create local Patient à
	  - creates local Patient resource
	  - auto-creates Linkage resource
	  - does NOT auto-create foreign Patient stub
	  - searching for Linkage by local Patient ID works
	  - searching for Linkage by foreign Patient ID does NOT work (this is expected, as the foreign Patient stub wasn’t created)
	  - create foreign Patient à
	    - creates foreign Patient resource
	    - searching for Linkage by local Patient ID works
	    - searching for Linkage by foreign Patient ID does NOT work (this is not expected, since both local and foreign Patient resources exist at this point)
	 */
	@Test
	void scenario1_CreateLocalPatientFirst() {
		Patient localPatient = new Patient() ;
		IIdType localPatientId = clientLocal.create().resource( localPatient ).execute().getId();

		assertCompartmentClaimed( clientLocal, localPatientId ) ;
		Patient stubPatient = assertCompartmentClaimed( clientForeign1, authorizedPatientId ) ;
		assertThat( stubPatient, isStub() ) ;

		assertLinked( authorizedPatientId, localPatientId ) ;
		assertLinked( localPatientId, authorizedPatientId ) ;

		Patient authorizedPatient = patient( authorizedPatientId ) ;
		authorizedPatient.addName( new HumanName().addGiven("TEST") ) ;
		clientForeign1.update().resource( authorizedPatient ).execute();

		Patient nonStubPatient = assertCompartmentClaimed( clientForeign1, authorizedPatientId ) ;
		assertThat( nonStubPatient, not( isStub() ) ) ;

		assertLinked( authorizedPatientId, localPatientId ) ;
		assertLinked( localPatientId, authorizedPatientId ) ;
	}

	/*
	Scenario 2: Create Foreign Patient First
	create foreign Patient à
	  - creates foreign Patient resource
	  - does NOT create Linkage (I think this is expected)
	  - does NOT create local Patient resource (I think this is also expected)
	  - create local Patient à
	    - creates local Patient resource
	    - auto-creates Linkage resource
	    - searching for Linkage by foreign Patient ID works
	    - searching for Linkage by local Patient ID works (it’s strange to me that this search works in this scenario but not in the other)
	*/
	@Test
	void scenario2_CreateForeignPatientFirst() {
		Patient authorizedPatient = patient( authorizedPatientId ) ;
		authorizedPatient.addName( new HumanName().addGiven("TEST") ) ;
		clientForeign1.update().resource( authorizedPatient ).execute();

		assertLinkagesAbsent( authorizedPatientId ) ;

		Patient nonStubPatient = assertCompartmentClaimed( clientForeign1, authorizedPatientId ) ;
		assertThat( nonStubPatient, not( isStub() ) ) ;

		Patient localPatient = new Patient() ;
		IIdType localPatientId = clientLocal.create().resource( localPatient ).execute().getId();

		Patient nonStubLocalPatient = assertCompartmentClaimed( clientLocal, localPatientId ) ;
		assertThat( nonStubLocalPatient, not( isStub() ) ) ;

		assertLinked( authorizedPatientId, localPatientId ) ;
		assertLinked( localPatientId, authorizedPatientId ) ;
	}

	/*
	 *  setup: store the local patient (automatically linked to foreign (authorized) patient STUB)
	 * action: store a patient-contributed goal for the local patient
	 * result: - goal is created
	 * action: read the goal
	 * result: - permitted
	 * action: search for the goal by subject
	 * result: - permitted
	 */
	@Test
	void canStoreLocalPatientContributedGoal() {
		IIdType localPatientId = clientLocal.create().resource( new Patient() ).execute().getId().toUnqualifiedVersionless() ;

		Goal goal = patientContributedGoalFor( localPatientId ) ;
		IIdType goalId = clientLocal.create().resource( goal ).execute().getId() ;

		assertThat( clientLocal.read().resource( Goal.class).withId( goalId ).execute(), notNullValue() ) ;
		assertThat( clientLocal.search().forResource( Goal.class).where( Goal.SUBJECT.hasId(localPatientId) ).execute(), notNullValue() ) ;
	}
	
	@Test
	void canStoreLocalPatientContributedQuestionnaireResponse() {
		IIdType localPatientId = clientLocal.create().resource( new Patient() ).execute().getId().toUnqualifiedVersionless() ;
		
		QuestionnaireResponse questionnaireResponse = patientContributedQuestionnaireResponseFor( localPatientId ) ;
		IIdType questionnaireResponseId = clientLocal.create().resource( questionnaireResponse ).execute().getId() ;
		
		assertThat( clientLocal.read().resource( QuestionnaireResponse.class).withId( questionnaireResponseId ).execute(), notNullValue() ) ;
		assertThat( clientLocal.search().forResource( QuestionnaireResponse.class).where( QuestionnaireResponse.SUBJECT.hasId(localPatientId) ).execute(), notNullValue() ) ;
	}
	
	@Test
	void canStoreLocalPatientContributedQuestionnaireResponseAsAuthor() {
		IIdType localPatientId = clientLocal.create().resource( new Patient() ).execute().getId().toUnqualifiedVersionless() ;
		
		QuestionnaireResponse questionnaireResponse = patientContributedQuestionnaireResponseAuthoredBy( localPatientId ) ;
		IIdType questionnaireResponseId = clientLocal.create().resource( questionnaireResponse ).execute().getId() ;
		
		assertThat( clientLocal.read().resource( QuestionnaireResponse.class).withId( questionnaireResponseId ).execute(), notNullValue() ) ;
		assertThat( clientLocal.search().forResource( QuestionnaireResponse.class).where( QuestionnaireResponse.AUTHOR.hasId(localPatientId) ).execute(), notNullValue() ) ;
	}
	
	/*
	 *  setup: no resources
	 * action: store another patient (e.g. records from another institution) different from foreign (authorized) patient
	 * result: - patient is created in other partition
	 *         - local patient is created
	 *         - linkage is created local patient -> other patient
	 *         - patient is created in foreign (authorized) partition as a STUB
	 *         - linkage is created local patient -> foreign patient
	 * action: retrieve other patient
	 * result: - allowed
	 */
	@Test
	void canStoreForeignOtherPatient() {
		Patient patient = patient( otherPatientId ) ;
		clientForeign2.update().resource( patient ).execute();

		assertCompartmentClaimed( clientForeign1, authorizedPatientId ) ;
		assertCompartmentClaimed( clientForeign2, otherPatientId ) ;
		assertLinkagesPresent( authorizedPatientId ) ;
		assertLinkagesPresent( otherPatientId ) ;

		assertThat( clientForeign2.read().resource( Patient.class).withId( otherPatientId ).execute(), notNullValue() ) ;
	}

	/*
	 *  setup: no resources
	 * action: store Observation belonging to foreign (authorized) patient (e.g. records from another institution)
	 * result: - Observation is created in foreign (authorized) partition
	 *         - patient is created in foreign (authorized) partition as a STUB
	 *         - still no linkages in local partition
	 * action: retrieve Observation(s)
	 * result: - allowed
	 */
	@Test
	void canStoreForeignResourceBelongingToAuthorizedPatientWithoutClaimingLocalCompartment() {
		Observation observation = foreignObservationFor( authorizedPatientId ) ;
		clientForeign1.update().resource( observation ).execute();

		assertCompartmentClaimed( clientForeign1, authorizedPatientId ) ;
		assertLinkagesAbsent( authorizedPatientId ) ;

		assertThat( clientForeign1.search().forResource( Observation.class).where( Observation.SUBJECT.hasId(authorizedPatientId) ).execute(), notNullValue() ) ;
	}

	/*
	 *  setup: Observation belonging to foreign (authorized) patient stored, creating foreign (authorized) partition as a STUB
	 * action: store the foreign (authorized) patient
	 * result: - patient is updated in foreign (authorized) partition
	 *         - still no linkages in local partition
	 */
	@Test
	void canStoreForeignPatientAfterClaimingCompartmentWithStoreForeignResource() {
		Observation observation = foreignObservationFor( authorizedPatientId ) ;
		clientForeign1.update().resource( observation ).execute();

		Patient patient = patient( authorizedPatientId ) ;
		clientForeign1.update().resource( patient ).execute();

		assertCompartmentClaimed( clientForeign1, authorizedPatientId ) ;
		assertLinkagesAbsent( authorizedPatientId ) ;
	}
	
	/*
	 *  setup: no resources
	 * action: store Observation belonging to other patient (e.g. records from another institution)
	 * result: - Observation is created in foreign partition
	 *         - patient is created in other partition as a STUB
	 *         - local patient is created
	 *         - linkage is created local patient -> other patient
	 *         - patient is created in foreign (authorized) partition as a STUB
	 *         - linkage is created local patient -> foreign (authorized) patient
	 * action: retrieve Observation(s)
	 * result: - allowed
	 */
	@Test
	void canStoreForeignResourceBelongingToOtherPatient() {
		Observation observation = foreignObservationFor( otherPatientId ) ;
		clientForeign2.update().resource( observation ).execute();

		assertCompartmentClaimed( clientForeign1, authorizedPatientId ) ;
		assertCompartmentClaimed( clientForeign2, otherPatientId ) ;

		assertLinkagesPresent( authorizedPatientId ) ;
		assertLinkagesPresent( otherPatientId ) ;

		assertThat( clientForeign2.search().forResource( Observation.class).where( Observation.SUBJECT.hasId(otherPatientId) ).execute(), notNullValue() ) ;
	}

	/*
	 *  setup: the foreign (authorized) patient is present
	 * action: store another patient (e.g. records from another institution) different from foreign (authorized) patient
	 * result: - patient is created in other partition
	 *         - local patient is created
	 *         - linkage is created local patient -> other patient
	 *         - linkage is created local patient -> foreign (authorized) patient
	 */
	@Test
	void canLinkForeignPatientsFromTwoDifferentPartitions() {
		Patient patient1 = patient( authorizedPatientId ) ;
		clientForeign1.update().resource( patient1 ).execute();

		assertCompartmentClaimed( clientForeign1, authorizedPatientId ) ;

		Patient patient2 = patient( otherPatientId ) ;
		clientForeign2.update().resource( patient2 ).execute();

		assertCompartmentClaimed( clientForeign2, otherPatientId ) ;
	}
	
	/*
	 * SDS assertions
	 */

	private Matcher<Patient> isStub() {
		return new BaseMatcher<Patient>() {
			@Override
			public boolean matches(Object item) {
				if ( null == item )
					return false ;
				if ( !( item instanceof Patient ) )
					return false ;
				Patient patient = (Patient)item ;
				Extension ext = patient.getExtensionByUrl("urn:sds:linkage-target-stub") ;
				if ( null == ext )
					return false;
				Type extValue = ext.getValue();
				if ( !( extValue instanceof BooleanType ) )
					return false ;
				return true == ((BooleanType)extValue).getValue() ;
			}

			@Override
			public void describeTo(Description description) {
				description.appendText("is a patient with extension identifying it as a SDS stub") ;
			}

		} ;
	}

	private void assertCompartmentUnclaimedAndUnlinked( IGenericClient client, IIdType patientId ) {
		assertCompartmentUnclaimed( client, patientId ) ;
		assertLinkagesAbsent( patientId );
	}
	
	private void assertCompartmentUnclaimed( IGenericClient client, IIdType patientId ) {
		assertThrows( ResourceNotFoundException.class, () -> {
			client.read()
				.resource( Patient.class )
				.withId( authorizedPatientId.toUnqualifiedVersionless() )
				.execute()
				;
		});
	}
	
	private Patient assertCompartmentClaimed( IGenericClient clientForPatientCompartment, IIdType patientId ) {
		Patient patient =
			clientForPatientCompartment.read()
				.resource( Patient.class )
				.withId( patientId.toUnqualifiedVersionless() )
				.execute()
				;
		assertThat( patient, notNullValue() ) ;
		return patient ;
	}
	
	private void assertLinkagesAbsent( IIdType patientId ) {
		List<Linkage> linkages = linkagesFor( patientId ) ;
		assertThat( linkages, empty() ) ;
	}

	private List<Linkage> assertLinkagesPresent( IIdType patientId ) {
		List<Linkage> linkages = linkagesFor( patientId ) ;
		assertThat( linkages, not( empty() ) ) ;
		return linkages ;
	}

	private List<Linkage> assertLinked( IIdType patientId, IIdType expectedLinkedPatientId ) {
		List<Linkage> linkages = assertLinkagesPresent( patientId ) ;

		Predicate<Linkage.LinkageItemComponent> itemMatches = item -> {
				IIdType idReferencedByItem = item.getResource().getReferenceElement();
				return 0 == FhirResourceComparison.idTypes().comparator().compare( idReferencedByItem, expectedLinkedPatientId ) ;
			};
		List<Linkage> linkagesMatchingExpected =
			linkages.stream()
				.filter( k -> k.getItem().stream().anyMatch( itemMatches ) )
				.collect( toList() )
				;
		assertThat( linkagesMatchingExpected, not( empty() )) ;

		return linkagesMatchingExpected ;
	}


	private void validateLinkagesFor( IIdType patientId, List<Linkage> linkagesToValidate ) {
		Set<IIdType> linkageIdsToValidate = FhirResourceComparison.idTypes().createSet()  ;
		linkagesToValidate.stream().map( Linkage::getIdElement ).forEach( linkageIdsToValidate::add ) ;

		Bundle allLinkagesBundle =
			clientLocal.search()
				.forResource( Linkage.class )
				.where( Linkage.ITEM.hasId(authorizedPatientId.toUnqualifiedVersionless()) )
				.returnBundle(Bundle.class)
				.execute()
				;
		List<Linkage> allLinkages =
			allLinkagesBundle
				.getEntry().stream()
					.filter( Bundle.BundleEntryComponent::hasResource )
					.map( Bundle.BundleEntryComponent::getResource )
					.filter( Linkage.class::isInstance )
					.map( Linkage.class::cast )
					.collect( toList() )
					;
		Set<IIdType> allLinkageIds = FhirResourceComparison.idTypes().createSet()  ;
		allLinkages.stream().map( Linkage::getIdElement ).forEach( allLinkageIds::add ) ;

		assertThat( "linkage resources different than resources found by post-filtering", linkageIdsToValidate, equalTo(allLinkageIds) ) ;
	}

	private List<Linkage> linkagesFor( IIdType patientId ) {
		Bundle linkageBundle =
			clientLocal.search()
				.forResource( Linkage.class )
				.where( Linkage.ITEM.hasId(authorizedPatientId.toUnqualifiedVersionless()) )
				.returnBundle(Bundle.class)
				.execute()
				;

		List<Linkage> linkages =
			linkageBundle
				.getEntry().stream()
					.filter( Bundle.BundleEntryComponent::hasResource )
					.map( Bundle.BundleEntryComponent::getResource )
					.filter( Linkage.class::isInstance )
					.map( Linkage.class::cast )
					.collect( toList() )
					;
		validateLinkagesFor( patientId, linkages ) ;
		return linkages ;
	}

	private Patient patient( IIdType id ) {
		Patient patient = new Patient() ;
		patient.setId( id.toUnqualifiedVersionless() ) ;
		return patient ;
	}

	private Goal patientContributedGoalFor( IIdType subject ) {
		Goal goal = new Goal() ;
		goal.setSubject( new Reference(subject) ) ;
		return goal ;
	}
	
	private QuestionnaireResponse patientContributedQuestionnaireResponseAuthoredBy( IIdType author ) {
		QuestionnaireResponse questionnaireResponse = new QuestionnaireResponse() ;
		questionnaireResponse.setAuthor( new Reference(author) ) ;
		return questionnaireResponse ;
	}
	
	private QuestionnaireResponse patientContributedQuestionnaireResponseFor( IIdType subject ) {
		QuestionnaireResponse questionnaireResponse = new QuestionnaireResponse() ;
		questionnaireResponse.setSubject( new Reference(subject) ) ;
		return questionnaireResponse ;
	}
	
	private Observation foreignObservationFor( IIdType subject ) {
		Observation observation = new Observation() ;
		observation.setId( new IdType( "Observation", createTestSpecificId() ) ) ;
		observation.setSubject( new Reference(subject) ) ;
		observation.getCode().setText( "MY-OBSERVATION" );
		observation.setEffective( new DateTimeType( new Date() ) ) ;
		observation.setValue( new BooleanType( true ) ) ;
		return observation ;
	}

}
