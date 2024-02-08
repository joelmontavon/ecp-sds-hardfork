package edu.ohsu.cmp.ecp.sds.dstu3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.dstu3.model.RelatedPerson;
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
import ca.uhn.fhir.rest.api.server.RequestDetails;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreProperties;
import edu.ohsu.cmp.ecp.sds.base.FhirResourceComparison;
import edu.ohsu.cmp.ecp.sds.base.SupplementalDataStoreLinkageBase;

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
					if (linkageItem.getType() == LinkageType.ALTERNATE && linkageItem.hasResource() && linkageItem.getResource().hasReference() && nonLocalPatientId.equals(linkageItem.getResource().getReferenceElement())) {
						linkageResources.add(res);
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
		daoLinkageDstu3.create(linkage, theRequestDetails);
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
}
