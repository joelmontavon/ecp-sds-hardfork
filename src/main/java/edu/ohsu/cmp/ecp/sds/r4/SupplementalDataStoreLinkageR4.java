package edu.ohsu.cmp.ecp.sds.r4;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import javax.inject.Inject;

import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Linkage;
import org.hl7.fhir.r4.model.Linkage.LinkageType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.RelatedPerson;
import org.hl7.fhir.r4.model.Type;
import org.hl7.fhir.r4.model.UrlType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.api.model.DaoMethodOutcome;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.jpa.starter.annotations.OnR4Condition;
import ca.uhn.fhir.model.api.IQueryParameterType;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.ReferenceParam;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreProperties;
import edu.ohsu.cmp.ecp.sds.base.FhirResourceComparison;
import edu.ohsu.cmp.ecp.sds.base.SupplementalDataStoreLinkageBase;

@Component
@Conditional(OnR4Condition.class)
public class SupplementalDataStoreLinkageR4 extends SupplementalDataStoreLinkageBase {

	@Inject
	SupplementalDataStoreProperties sdsProperties;

	@Inject
	IFhirResourceDao<org.hl7.fhir.r4.model.Linkage> daoLinkageR4;

	@Inject
	IFhirResourceDao<org.hl7.fhir.r4.model.Patient> daoPatientR4;

	@Inject
	IFhirResourceDao<org.hl7.fhir.r4.model.Practitioner> daoPractitionerR4;

	@Inject
	IFhirResourceDao<org.hl7.fhir.r4.model.RelatedPerson> daoRelatedPersonR4;

	private static Predicate<IIdType> sameId( IIdType id ) {
		return (i) -> {
			if ( id.hasVersionIdPart() && i.hasVersionIdPart() && !id.getVersionIdPart().equals(i.getVersionIdPart()))
				return false ;
			if ( id.hasBaseUrl() && i.hasBaseUrl() && !id.getBaseUrl().equals(i.getBaseUrl()))
				return false ;
			if ( id.hasResourceType() && i.hasResourceType() && !id.getResourceType().equals(i.getResourceType()))
				return false ;
			if ( !id.hasIdPart() || !i.hasIdPart() )
				return false ;
			return id.getIdPart().equals( i.getIdPart() ) ;
		};
	}

	private static Predicate<Linkage.LinkageItemComponent> refersTo( IQueryParameterType param ) {
		if ( param instanceof ReferenceParam ) {
			ReferenceParam refParam = (ReferenceParam)param ;
			return refersTo( new IdType( refParam.getResourceType(), refParam.getIdPart() ) ) ;
		} else {
			return (i) -> false ;
		}
	}

	private static Predicate<Linkage.LinkageItemComponent> refersTo( IIdType ref ) {
		Predicate<IIdType> p = sameId( ref ) ;
		return i -> i.hasResource() && i.getResource().hasReference() && p.test( referenceFromLinkage( i.getResource() ).getReferenceElement() );
	}

	private static Predicate<Linkage.LinkageItemComponent> sourceRefersTo( IQueryParameterType param ) {
		if ( param instanceof ReferenceParam ) {
			ReferenceParam refParam = (ReferenceParam)param ;
			return sourceRefersTo( new IdType( refParam.getResourceType(), refParam.getIdPart() ) ) ;
		} else {
			return (i) -> false ;
		}
	}

	private static Predicate<Linkage.LinkageItemComponent> sourceRefersTo( IIdType ref ) {
		Predicate<Linkage.LinkageItemComponent> p1 = refersTo( ref ) ;
		return i -> i.getType() == Linkage.LinkageType.SOURCE && p1.test(i) ;
	}

	private Predicate<IBaseResource> linkageItemFilter( List<List<IQueryParameterType>> parameterValue ) {
		if ( null == parameterValue )
			return r -> true ;
		return r -> {
			if ( !(r instanceof Linkage ) )
				return false ;
			Linkage linkage = (Linkage)r ;

			return parameterValue.stream().allMatch( v1 -> v1.stream().anyMatch( v -> linkage.getItem().stream().anyMatch( refersTo( v ) ) ) ) ;
		} ;
	}

	private Predicate<IBaseResource> linkageSourceFilter( List<List<IQueryParameterType>> parameterValue ) {
		if ( null == parameterValue )
			return r -> true ;
			return r -> {
				if ( !(r instanceof Linkage ) )
					return false ;
				Linkage linkage = (Linkage)r ;

				return parameterValue.stream().allMatch( v1 -> v1.stream().anyMatch( v -> linkage.getItem().stream().anyMatch( sourceRefersTo( v ) ) ) ) ;
			} ;
	}

	private IQueryParameterType convertQueryParameter( IQueryParameterType parameter ) {
		if ( parameter instanceof ReferenceParam ) {
			ReferenceParam refParam = (ReferenceParam)parameter ;
			IdType id = new IdType( refParam.getValue() ).toUnqualifiedVersionless() ;
			return new ReferenceParam( id ) ;
		} else {
			return parameter ;
		}
	}

	private List<IQueryParameterType> convertQueryParameters( List<IQueryParameterType> parameters ) {
		return parameters.stream().map( this::convertQueryParameter ).collect( toList() ) ;
	}

	private List<List<IQueryParameterType>> convertQueryParametersList( List<List<IQueryParameterType>> parameters ) {
		return parameters.stream().map( this::convertQueryParameters ).collect( toList() ) ;
	}

	@Override
	protected List<IBaseResource> searchLinkageResources( SearchParameterMap linkageSearchParamMap, RequestDetails theRequestDetails ) {
		/*
		 * server is returning LINKAGE resources while searching on SOURCE that match the id but are not SOURCE
		 */
		/*
		 * item and source parameters must be relative (i.e. no baseUrl) in order to find these LINKAGE resources
		 */

		SearchParameterMap replacementSearchParameterMap = linkageSearchParamMap.clone() ;
		List<List<IQueryParameterType>> itemQueryParameter = replacementSearchParameterMap.remove("item");
		List<List<IQueryParameterType>> sourceQueryParameter = replacementSearchParameterMap.remove("source");
		if ( null != itemQueryParameter ) {
			replacementSearchParameterMap.put( "item", convertQueryParametersList( itemQueryParameter ) );
		}
		if ( null != sourceQueryParameter ) {
			replacementSearchParameterMap.put( "source", convertQueryParametersList( sourceQueryParameter ) );
		}
		return daoLinkageR4.search(replacementSearchParameterMap, theRequestDetails).getAllResources()
				.stream()
				.filter( linkageItemFilter( itemQueryParameter ) )
				.filter( linkageSourceFilter( sourceQueryParameter ) )
				.collect( java.util.stream.Collectors.toList() )
				;
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
		if ( !(extValue instanceof UrlType) )
			return ref ;
		IIdType id = new IdType( ((UrlType)extValue).getValue(), ref.getReferenceElement().getResourceType(), ref.getReferenceElement().getIdPart(), ref.getReferenceElement().getVersionIdPart() ) ;
		return new Reference( id ) ;
	}

	private static Reference referenceForLinkage( IIdType id ) {
		if ( id.hasBaseUrl() ) {
			Reference ref = new Reference( id.toUnqualifiedVersionless() ) ;
			ref.addExtension(EXTENSION_URL_SDS_PARTITION_NAME, new UrlType( id.getBaseUrl() ) );
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
		DaoMethodOutcome outcome = daoLinkageR4.create(linkage, theRequestDetails);
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
				.map( SupplementalDataStoreLinkageR4::referenceFromLinkage )
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
				.map( SupplementalDataStoreLinkageR4::referenceFromLinkage )
				.collect(java.util.stream.Collectors.toList());
		return FhirResourceComparison.references().createSet( sourceRefs ) ;
	}
	
	@Override
	public IBaseResource createLocalPatient( RequestDetails theRequestDetails ) {
		Patient patient = new Patient();
		DaoMethodOutcome createOutcome = daoPatientR4.create(patient, theRequestDetails);
		return createOutcome.getResource();
	}

	@Override
	public IBaseResource createLocalPractitioner( RequestDetails theRequestDetails ) {
		Practitioner practitioner = new Practitioner();
		DaoMethodOutcome createOutcome = daoPractitionerR4.create(practitioner, theRequestDetails );
		return createOutcome.getResource();
	}

	@Override
	public IBaseResource createLocalRelatedPerson( RequestDetails theRequestDetails ) {
		RelatedPerson relatedPerson = new RelatedPerson();
		DaoMethodOutcome createOutcome = daoRelatedPersonR4.create(relatedPerson, theRequestDetails );
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
		String baseUrl = ext2.getValue().castToUrl(ext2.getValue()).getValue() ;
		return id.withServerBase(baseUrl, id.getResourceType() ) ;
	}
	
	@Override
	protected Optional<IBaseResource> searchPatient(IIdType patientId, RequestDetails theRequestDetails) {
		List<IBaseResource> resources =
				daoPatientR4
				.search( new SearchParameterMap( Patient.SP_RES_ID, new ReferenceParam( patientId ) ), theRequestDetails)
				.getResources(0, 1)
				;
		return resources.stream().findFirst();
	}


	@Override
	public IBaseResource createNonLocalStubPatient( IIdType nonLocalPatientId, RequestDetails theRequestDetails ) {
		Patient patient = new Patient();
		patient.setId( nonLocalPatientId.toUnqualifiedVersionless() ) ;
		patient.addExtension( EXTENSION_URL_RESOURCE_SDS_LINKAGE_TARGET_STUB, new BooleanType(true) );
		patient.addExtension( EXTENSION_URL_SDS_PARTITION_NAME, new UrlType( nonLocalPatientId.getBaseUrl() ) );
		DaoMethodOutcome createOutcome = daoPatientR4.update(patient, theRequestDetails);
		return createOutcome.getResource();
	}
	
	@Override
	public IBaseResource createNonLocalStubPractitioner( IIdType nonLocalPractitionerId, RequestDetails theRequestDetails ) {
		Practitioner practitioner = new Practitioner();
		practitioner.setId( nonLocalPractitionerId.toUnqualifiedVersionless() ) ;
		practitioner.addExtension( EXTENSION_URL_RESOURCE_SDS_LINKAGE_TARGET_STUB, new BooleanType(true) );
		practitioner.addExtension( EXTENSION_URL_SDS_PARTITION_NAME, new UrlType( nonLocalPractitionerId.getBaseUrl() ) );
		DaoMethodOutcome createOutcome = daoPractitionerR4.update(practitioner, theRequestDetails );
		return createOutcome.getResource();
	}
	
	@Override
	public IBaseResource createNonLocalStubRelatedPerson( IIdType nonLocalRelatedPersonId, RequestDetails theRequestDetails ) {
		RelatedPerson relatedPerson = new RelatedPerson();
		relatedPerson.setId( nonLocalRelatedPersonId.toUnqualifiedVersionless() ) ;
		relatedPerson.addExtension( EXTENSION_URL_RESOURCE_SDS_LINKAGE_TARGET_STUB, new BooleanType(true) );
		relatedPerson.addExtension( EXTENSION_URL_SDS_PARTITION_NAME, new UrlType( nonLocalRelatedPersonId.getBaseUrl() ) );
		DaoMethodOutcome createOutcome = daoRelatedPersonR4.update(relatedPerson, theRequestDetails );
		return createOutcome.getResource();
	}

}
