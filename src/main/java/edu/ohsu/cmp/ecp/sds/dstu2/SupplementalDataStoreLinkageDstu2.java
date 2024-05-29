package edu.ohsu.cmp.ecp.sds.dstu2;

import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.hl7.fhir.dstu2.model.BooleanType;
import org.hl7.fhir.dstu2.model.DomainResource;
import org.hl7.fhir.dstu2.model.Extension;
import org.hl7.fhir.dstu2.model.Patient;
import org.hl7.fhir.dstu2.model.Practitioner;
import org.hl7.fhir.dstu2.model.Reference;
import org.hl7.fhir.dstu2.model.RelatedPerson;
import org.hl7.fhir.dstu2.model.StringType;
import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.api.model.DaoMethodOutcome;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.jpa.starter.annotations.OnDSTU2Condition;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreProperties;
import edu.ohsu.cmp.ecp.sds.base.SupplementalDataStoreLinkageBase;

@Component
@Conditional(OnDSTU2Condition.class)
public class SupplementalDataStoreLinkageDstu2 extends SupplementalDataStoreLinkageBase {

	@Inject
	SupplementalDataStoreProperties sdsProperties;

	@Inject
	IFhirResourceDao<Patient> daoPatientDstu2;
	
	@Inject
	IFhirResourceDao<Practitioner> daoPractitionerDstu2;

	@Inject
	IFhirResourceDao<RelatedPerson> daoRelatedPersonDstu2;

	private RuntimeException linkageResourceNotDefinedInDstu2() {
		return new UnsupportedOperationException( "SDS does not support FHIR version DSTU2 because it does not include a definition for the Linkage resource" );
	}
	
	@Override
	protected List<IBaseResource> searchLinkageResources( SearchParameterMap linkageSearchParamMap, RequestDetails theRequestDetails ) {
		throw linkageResourceNotDefinedInDstu2() ;
	}

	@Override
	protected List<IBaseResource> filterLinkageResourcesHavingAlternateItem( List<IBaseResource> allLinkageResources, IIdType nonLocalPatientId ) {
		throw linkageResourceNotDefinedInDstu2() ;
	}

	@Override
	protected List<Reference> patientsFromLinkageResources(List<IBaseResource> linkageResources) {
		throw linkageResourceNotDefinedInDstu2() ;
	}

	@Override
	protected void createLinkage( IIdType sourcePatientId, IIdType alternatePatientId, RequestDetails theRequestDetails ) {
		throw linkageResourceNotDefinedInDstu2() ;
	}

	@Override
	protected Set<? extends IBaseReference> alternatePatientsFromLinkageResources(List<? extends IBaseResource> linkageResources) {
		throw linkageResourceNotDefinedInDstu2() ;
	}

	@Override
	protected Set<? extends IBaseReference> sourcePatientsFromLinkageResources(List<? extends IBaseResource> linkageResources) {
		throw linkageResourceNotDefinedInDstu2() ;
	}
	
	@Override
	public IBaseResource createLocalPatient( RequestDetails theRequestDetails ) {
		Patient patient = new Patient();
		DaoMethodOutcome createOutcome = daoPatientDstu2.create(patient, theRequestDetails);
		return createOutcome.getResource();
	}

	@Override
	public IBaseResource createLocalPractitioner( RequestDetails theRequestDetails ) {
		Practitioner practitioner = new Practitioner();
		DaoMethodOutcome createOutcome = daoPractitionerDstu2.create(practitioner, theRequestDetails );
		return createOutcome.getResource();
	}

	@Override
	public IBaseResource createLocalRelatedPerson( RequestDetails theRequestDetails ) {
		RelatedPerson relatedPerson = new RelatedPerson();
		DaoMethodOutcome createOutcome = daoRelatedPersonDstu2.create(relatedPerson, theRequestDetails );
		return createOutcome.getResource();
	}

	@Override
	public IIdType fullyQualifiedIdForStubUser( IBaseResource userResource ) {
		DomainResource res = (DomainResource)userResource ;
		IIdType id = res.getIdElement() ;
		Extension ext = res.getExtension().stream().filter( e -> EXTENSION_URL_RESOURCE_SDS_LINKAGE_TARGET_STUB.equals( e.getUrl() ) ).findFirst().orElse(null);
		if ( null == ext || !ext.hasValue() || false == ext.getValue().castToBoolean(ext.getValue()).booleanValue() )
			throw new IllegalArgumentException( String.format( "%1$s resource \"%2$s\" is not a stub user", userResource.fhirType(), id.getIdPart() ) ) ;
		Extension ext2 = res.getExtension().stream().filter( e -> EXTENSION_URL_SDS_PARTITION_NAME.equals( e.getUrl() ) ).findFirst().orElse(null);
		if ( null == ext2 || !ext2.hasValue() )
			throw new IllegalArgumentException( String.format( "%1$s resource \"%2$s\" is not a stub user", userResource.fhirType(), id.getIdPart() ) ) ;
		String baseUrl = ext2.getValue().castToString(ext2.getValue()).getValue() ;
		return id.withServerBase(baseUrl, id.getResourceType() ) ;
	}

	@Override
	public IBaseResource createNonLocalStubPatient( IIdType nonLocalPatientId, RequestDetails theRequestDetails ) {
		Patient patient = new Patient();
		patient.setId( nonLocalPatientId ) ;
		patient.setId( nonLocalPatientId.toUnqualifiedVersionless() ) ;
		patient.addExtension().setUrl( EXTENSION_URL_RESOURCE_SDS_LINKAGE_TARGET_STUB ).setValue( new BooleanType(true) );
		patient.addExtension().setUrl( EXTENSION_URL_SDS_PARTITION_NAME).setValue( new StringType( nonLocalPatientId.getBaseUrl() ) );
		DaoMethodOutcome createOutcome = daoPatientDstu2.update(patient, theRequestDetails);
		return createOutcome.getResource();
	}
	
	@Override
	public IBaseResource createNonLocalStubPractitioner( IIdType nonLocalPractitionerId, RequestDetails theRequestDetails ) {
		Practitioner practitioner = new Practitioner();
		practitioner.setId( nonLocalPractitionerId.toUnqualifiedVersionless() ) ;
		practitioner.addExtension().setUrl( EXTENSION_URL_RESOURCE_SDS_LINKAGE_TARGET_STUB ).setValue( new BooleanType(true) );
		practitioner.addExtension().setUrl( EXTENSION_URL_SDS_PARTITION_NAME).setValue( new StringType( nonLocalPractitionerId.getBaseUrl() ) );
		DaoMethodOutcome createOutcome = daoPractitionerDstu2.update(practitioner, theRequestDetails );
		return createOutcome.getResource();
	}
	
	@Override
	public IBaseResource createNonLocalStubRelatedPerson( IIdType nonLocalRelatedPersonId, RequestDetails theRequestDetails ) {
		RelatedPerson relatedPerson = new RelatedPerson();
		relatedPerson.setId( nonLocalRelatedPersonId.toUnqualifiedVersionless() ) ;
		relatedPerson.addExtension().setUrl( EXTENSION_URL_RESOURCE_SDS_LINKAGE_TARGET_STUB ).setValue( new BooleanType(true) );
		relatedPerson.addExtension().setUrl( EXTENSION_URL_SDS_PARTITION_NAME).setValue( new StringType( nonLocalRelatedPersonId.getBaseUrl() ) );
		DaoMethodOutcome createOutcome = daoRelatedPersonDstu2.update(relatedPerson, theRequestDetails );
		return createOutcome.getResource();
	}
}
