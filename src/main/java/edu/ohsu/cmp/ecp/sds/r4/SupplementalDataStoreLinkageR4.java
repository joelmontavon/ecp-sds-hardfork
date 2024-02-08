package edu.ohsu.cmp.ecp.sds.r4;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import javax.inject.Inject;

import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Linkage;
import org.hl7.fhir.r4.model.Linkage.LinkageType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.RelatedPerson;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.api.model.DaoMethodOutcome;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.jpa.starter.annotations.OnR4Condition;
import ca.uhn.fhir.model.api.IQueryParameterType;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
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
		return i -> i.hasResource() && i.getResource().hasReference() && p.test( i.getResource().getReferenceElement() ) ;
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
	
	@Override
	protected List<IBaseResource> searchLinkageResources( SearchParameterMap linkageSearchParamMap, RequestDetails theRequestDetails ) {
		/*
		 * server is failing to find existing LINKAGE resources while searching on ITEM
		 * 
		return daoLinkageR4.search(linkageSearchParamMap, theRequestDetails).getAllResources();
		 */
		SearchParameterMap replacementSearchParameterMap = linkageSearchParamMap.clone() ;
		Predicate<IBaseResource> itemFilter = linkageItemFilter( replacementSearchParameterMap.remove("item") ) ;
		Predicate<IBaseResource> sourceFilter = linkageSourceFilter( replacementSearchParameterMap.remove("source") ) ;
		return daoLinkageR4.search(replacementSearchParameterMap, theRequestDetails).getAllResources()
				.stream()
				.filter( itemFilter )
				.filter( sourceFilter )
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
					if (linkageItem.getType() == LinkageType.ALTERNATE && linkageItem.hasResource() && linkageItem.getResource().hasReference() && nonLocalPatientId.equals(linkageItem.getResource().getReferenceElement())) {
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

	@Override
	protected void createLinkage( IIdType sourcePatientId, IIdType alternatePatientId, RequestDetails theRequestDetails ) {
		Linkage linkage = new Linkage();
		linkage.addItem().setType(LinkageType.SOURCE).setResource(new Reference(sourcePatientId));
		linkage.addItem().setType(LinkageType.ALTERNATE).setResource(new Reference(alternatePatientId));
		IBundleProvider searchBefore = daoLinkageR4.search( new SearchParameterMap(), theRequestDetails ) ;
		System.out.println( "*** createLinkage: before: " + searchBefore.size() + " linkage resources" ) ;
		DaoMethodOutcome outcome = daoLinkageR4.create(linkage, theRequestDetails);
		IBundleProvider searchAfter = daoLinkageR4.search( new SearchParameterMap(), theRequestDetails ) ;
		System.out.println( "*** createLinkage: after: " + searchAfter.size() + " linkage resources") ;
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
}
