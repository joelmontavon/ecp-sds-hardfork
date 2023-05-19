package edu.ohsu.cmp.ecp.sds.r4;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Linkage;
import org.hl7.fhir.r4.model.Linkage.LinkageType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.api.model.DaoMethodOutcome;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.jpa.starter.annotations.OnR4Condition;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreProperties;
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

	@Override
	protected List<IBaseResource> searchLinkageResources( SearchParameterMap linkageSearchParamMap, RequestDetails theRequestDetails ) {
		return daoLinkageR4.search(linkageSearchParamMap, theRequestDetails).getAllResources();	
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
		daoLinkageR4.create(linkage, theRequestDetails);
	}

	@Override
	protected List<Reference> sourcePatientsFromLinkageResources(List<IBaseResource> linkageResources) {
		List<Reference> sourceRefs =
			linkageResources.stream()
				.filter(r -> (r instanceof Linkage))
				.map(r -> (Linkage) r)
				.flatMap(k -> k.getItem().stream())
				.filter(i -> i.getType() == LinkageType.SOURCE)
				.map(i -> i.getResource())
				.collect(java.util.stream.Collectors.toList());
		return sourceRefs ;
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
}
