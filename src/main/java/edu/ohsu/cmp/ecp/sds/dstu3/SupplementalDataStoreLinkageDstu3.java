package edu.ohsu.cmp.ecp.sds.dstu3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import javax.inject.Inject;

import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.dstu3.model.RelatedPerson;
import org.hl7.fhir.dstu3.model.Type;
import org.hl7.fhir.dstu3.model.StringType;
import org.hl7.fhir.dstu3.model.BooleanType;
import org.hl7.fhir.dstu3.model.DomainResource;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.Linkage;
import org.hl7.fhir.dstu3.model.Linkage.LinkageType;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Practitioner;
import org.hl7.fhir.dstu3.model.Reference;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.api.model.DaoMethodOutcome;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.jpa.starter.annotations.OnDSTU3Condition;
import ca.uhn.fhir.model.api.IQueryParameterType;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.ReferenceParam;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreProperties;
import edu.ohsu.cmp.ecp.sds.base.FhirResourceComparison;
import edu.ohsu.cmp.ecp.sds.base.SupplementalDataStoreLinkageBase;
import edu.ohsu.cmp.ecp.sds.r4.SupplementalDataStoreLinkageR4;

@Component
@Conditional(OnDSTU3Condition.class)
public class SupplementalDataStoreLinkageDstu3 extends SupplementalDataStoreLinkageBase {

	@Inject
	SupplementalDataStoreProperties sdsProperties;

	@Inject
	IFhirResourceDao<org.hl7.fhir.dstu3.model.Linkage> daoLinkageDstu3;

	@Inject
	IFhirResourceDao<org.hl7.fhir.dstu3.model.Patient> daoPatientDstu3;

	@Inject
	IFhirResourceDao<org.hl7.fhir.dstu3.model.Practitioner> daoPractitionerDstu3;

	@Inject
	IFhirResourceDao<org.hl7.fhir.dstu3.model.RelatedPerson> daoRelatedPersonDstu3;

	@Override
	protected List<IBaseResource> searchLinkageResources( SearchParameterMap linkageSearchParamMap, RequestDetails theRequestDetails ) {
		return daoLinkageDstu3.search(linkageSearchParamMap, theRequestDetails).getAllResources();
	}

	@Override
	protected List<IBaseResource> filterLinkageResourcesHavingAlternateItem( List<IBaseResource> allLinkageResources, IIdType nonLocalPatientId ) {
		List<IBaseResource> linkageResources = new ArrayList<>();
		for (IBaseResource res : allLinkageResources) {
			if (res instanceof Linkage) {
				Linkage linkage = (Linkage) res;
				for (Linkage.LinkageItemComponent linkageItem : linkage.getItem()) {
					if (linkageItem.getType() == LinkageType.ALTERNATE && linkageItem.hasResource() && linkageItem.getResource().hasReference() && nonLocalPatientId.equals( referenceFromLinkage( linkageItem.getResource() ).getReferenceElement())) {
						linkageResources.add(res);
						break;	// breaking to prevent re-adding res if there are multiple alternates
					}
				}
			}
		}
		return linkageResources;
	}

	@Override
	protected List<Reference> patientsFromLinkageResources(List<IBaseResource> linkageResources) {
		List<Reference> linkedPatients = new ArrayList<>();
		for (IBaseResource res : linkageResources) {
			if (res instanceof Linkage) {
				Linkage linkage = (Linkage) res;
				for (Linkage.LinkageItemComponent linkageItem : linkage.getItem()) {
					linkedPatients.add(linkageItem.getResource());
				}
			}
		}
		return linkedPatients;
	}

	private static Reference referenceFromLinkage( Reference ref ) {
		if ( !ref.hasExtension(EXTENSION_URL_SDS_PARTITION_NAME) )
			return ref ;
		if ( !ref.hasReferenceElement() )
			return ref ;
		Type extValue = ref.getExtensionByUrl(EXTENSION_URL_SDS_PARTITION_NAME).getValue() ;
		if ( !(extValue instanceof StringType) )
			return ref ;
		IIdType id = new IdType( ((StringType)extValue).getValue(), ref.getReferenceElement().getResourceType(), ref.getReferenceElement().getIdPart(), ref.getReferenceElement().getVersionIdPart() ) ;
		return new Reference( id ) ;
	}

	private static Reference referenceForLinkage( IIdType id ) {
		if ( id.hasBaseUrl() ) {
			Reference ref = new Reference( id.toUnqualifiedVersionless() ) ;
			ref.addExtension(EXTENSION_URL_SDS_PARTITION_NAME, new StringType( id.getBaseUrl() ) );
			return ref ;
		} else {
			return new Reference(id) ;
		}
	}

	@Override
	protected void createLinkage( IIdType sourcePatientId, IIdType alternatePatientId, RequestDetails theRequestDetails ) {
		Linkage linkage = new Linkage();
		linkage.addItem().setType(LinkageType.SOURCE).setResource(referenceForLinkage(sourcePatientId));
		linkage.addItem().setType(LinkageType.ALTERNATE).setResource(referenceForLinkage(alternatePatientId));
		DaoMethodOutcome outcome = daoLinkageDstu3.create(linkage, theRequestDetails);
		if ( Boolean.TRUE != outcome.getCreated() ) {
			throw new RuntimeException( "failed to create linkage between " + sourcePatientId + " and " + alternatePatientId ) ;
		}
	}

	@Override
	protected Set<? extends IBaseReference> alternatePatientsFromLinkageResources(List<? extends IBaseResource> linkageResources) {
		List<Reference> sourceRefs =
			linkageResources.stream()
				.filter(r -> (r instanceof Linkage))
				.map(r -> (Linkage) r)
				.flatMap(k -> k.getItem().stream())
				.filter(i -> i.getType() == LinkageType.ALTERNATE)
				.map(i -> i.getResource())
				.map( SupplementalDataStoreLinkageDstu3::referenceFromLinkage )
				.collect(java.util.stream.Collectors.toList());
		return FhirResourceComparison.references().createSet( sourceRefs ) ;
	}
	
	@Override
	protected Set<? extends IBaseReference> sourcePatientsFromLinkageResources(List<? extends IBaseResource> linkageResources) {
		List<Reference> sourceRefs =
			linkageResources.stream()
				.filter(r -> (r instanceof Linkage))
				.map(r -> (Linkage) r)
				.flatMap(k -> k.getItem().stream())
				.filter(i -> i.getType() == LinkageType.SOURCE)
				.map(i -> i.getResource())
				.map( SupplementalDataStoreLinkageDstu3::referenceFromLinkage )
				.collect(java.util.stream.Collectors.toList());
		return FhirResourceComparison.references().createSet( sourceRefs ) ;
	}

	@Override
	public IBaseResource createLocalPatient( RequestDetails theRequestDetails ) {
		Patient patient = new Patient();
		DaoMethodOutcome createOutcome = daoPatientDstu3.create(patient, theRequestDetails);
		return createOutcome.getResource();
	}

	@Override
	public IBaseResource createLocalPractitioner( RequestDetails theRequestDetails ) {
		Practitioner practitioner = new Practitioner();
		DaoMethodOutcome createOutcome = daoPractitionerDstu3.create(practitioner, theRequestDetails );
		return createOutcome.getResource();
	}

	@Override
	public IBaseResource createLocalRelatedPerson( RequestDetails theRequestDetails ) {
		RelatedPerson relatedPerson = new RelatedPerson();
		DaoMethodOutcome createOutcome = daoRelatedPersonDstu3.create(relatedPerson, theRequestDetails );
		return createOutcome.getResource();
	}

	@Override
	public IIdType fullyQualifiedIdForStubUser( IBaseResource userResource ) {
		DomainResource res = (DomainResource)userResource ;
		IIdType id = res.getIdElement() ;
		Extension ext = res.getExtensionByUrl(EXTENSION_URL_RESOURCE_SDS_LINKAGE_TARGET_STUB) ;
		if ( null == ext || !ext.hasValue() || false == ext.getValue().castToBoolean(ext.getValue()).booleanValue() )
			throw new IllegalArgumentException( String.format( "%1$s resource \"%2$s\" is not a stub user", userResource.fhirType(), id.getIdPart() ) ) ;
		Extension ext2 = res.getExtensionByUrl(EXTENSION_URL_SDS_PARTITION_NAME) ;
		if ( null == ext2 || !ext2.hasValue() )
			throw new IllegalArgumentException( String.format( "%1$s resource \"%2$s\" is not a stub user", userResource.fhirType(), id.getIdPart() ) ) ;
		String baseUrl = ext2.getValue().castToString(ext2.getValue()).getValue() ;
		return id.withServerBase(baseUrl, id.getResourceType() ) ;
	}

	@Override
	protected Optional<IBaseResource> searchPatient(IIdType patientId, RequestDetails theRequestDetails) {
		List<IBaseResource> resources =
			daoPatientDstu3
				.search( new SearchParameterMap( Patient.SP_RES_ID, new ReferenceParam( patientId ) ), theRequestDetails)
				.getResources(0, 1)
				;
		return resources.stream().findFirst();
	}

	@Override
	public IBaseResource createNonLocalStubPatient( IIdType nonLocalPatientId, RequestDetails theRequestDetails ) {
		Patient patient = new Patient();
		patient.setId( nonLocalPatientId ) ;
		patient.setId( nonLocalPatientId.toUnqualifiedVersionless() ) ;
		patient.addExtension( EXTENSION_URL_RESOURCE_SDS_LINKAGE_TARGET_STUB, new BooleanType(true) );
		patient.addExtension( EXTENSION_URL_SDS_PARTITION_NAME, new StringType( nonLocalPatientId.getBaseUrl() ) );
		DaoMethodOutcome createOutcome = daoPatientDstu3.update(patient, theRequestDetails);
		return createOutcome.getResource();
	}
	
	@Override
	public IBaseResource createNonLocalStubPractitioner( IIdType nonLocalPractitionerId, RequestDetails theRequestDetails ) {
		Practitioner practitioner = new Practitioner();
		practitioner.setId( nonLocalPractitionerId.toUnqualifiedVersionless() ) ;
		practitioner.addExtension( EXTENSION_URL_RESOURCE_SDS_LINKAGE_TARGET_STUB, new BooleanType(true) );
		practitioner.addExtension( EXTENSION_URL_SDS_PARTITION_NAME, new StringType( nonLocalPractitionerId.getBaseUrl() ) );
		DaoMethodOutcome createOutcome = daoPractitionerDstu3.update(practitioner, theRequestDetails );
		return createOutcome.getResource();
	}
	
	@Override
	public IBaseResource createNonLocalStubRelatedPerson( IIdType nonLocalRelatedPersonId, RequestDetails theRequestDetails ) {
		RelatedPerson relatedPerson = new RelatedPerson();
		relatedPerson.setId( nonLocalRelatedPersonId.toUnqualifiedVersionless() ) ;
		relatedPerson.addExtension( EXTENSION_URL_RESOURCE_SDS_LINKAGE_TARGET_STUB, new BooleanType(true) );
		relatedPerson.addExtension( EXTENSION_URL_SDS_PARTITION_NAME, new StringType( nonLocalRelatedPersonId.getBaseUrl() ) );
		DaoMethodOutcome createOutcome = daoRelatedPersonDstu3.update(relatedPerson, theRequestDetails );
		return createOutcome.getResource();
	}
}
