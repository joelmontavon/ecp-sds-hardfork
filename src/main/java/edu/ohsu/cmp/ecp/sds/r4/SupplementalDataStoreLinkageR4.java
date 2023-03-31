package edu.ohsu.cmp.ecp.sds.r4;

import ca.uhn.fhir.interceptor.model.RequestPartitionId;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.api.model.DaoMethodOutcome;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.jpa.starter.annotations.OnR4Condition;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreLinkage;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreProperties;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Linkage;
import org.hl7.fhir.r4.model.Linkage.LinkageType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@Conditional(OnR4Condition.class)
public class SupplementalDataStoreLinkageR4 implements SupplementalDataStoreLinkage {

	@Inject
	SupplementalDataStoreProperties sdsProperties;

	@Inject
	IFhirResourceDao<org.hl7.fhir.r4.model.Linkage> daoLinkageR4;

	@Inject
	IFhirResourceDao<org.hl7.fhir.r4.model.Patient> daoPatientR4;

	@Inject
	IFhirResourceDao<org.hl7.fhir.r4.model.Practitioner> daoPractitionerR4;

	private RequestDetails localPartitionRequest() {
		SystemRequestDetails internalRequestForLocalPartition = new SystemRequestDetails();
		internalRequestForLocalPartition.setRequestPartitionId(RequestPartitionId.fromPartitionName(sdsProperties.getPartition().getLocalName()));
		return internalRequestForLocalPartition;
	}

	private List<IBaseResource> linkageResourcesHavingAlternateItem(IIdType nonLocalPatientId) {
		SearchParameterMap linkageSearchParamMap = new SearchParameterMap();
		linkageSearchParamMap.add("item", new ReferenceParam(nonLocalPatientId));

		List<IBaseResource> allLinkageResources = daoLinkageR4.search(linkageSearchParamMap, localPartitionRequest()).getAllResources();

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

	private List<IBaseResource> linkageResourcesHavingSourceItem(IIdType localPatientId) {
		SearchParameterMap linkageSearchParamMap = new SearchParameterMap();
		linkageSearchParamMap.add("source", new ReferenceParam(localPatientId));

		List<IBaseResource> linkageResources = daoLinkageR4.search(linkageSearchParamMap, localPartitionRequest()).getAllResources();
		return linkageResources;
	}

	@Override
	public List<Reference> patientsLinkedTo(IIdType localPatientId) {

		List<IBaseResource> linkageResources = linkageResourcesHavingSourceItem(localPatientId);

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
	public IIdType establishLocalUserFor(IIdType nonLocalUserId) {
		if (null == nonLocalUserId)
			throw new InvalidRequestException("cannot establish local user resource without a non-local user id for initial linkage");

		List<IBaseResource> linkageResources = linkageResourcesHavingAlternateItem(nonLocalUserId);

		if (linkageResources.isEmpty()) {

			IBaseResource localUser = createLocalUser(nonLocalUserId.getResourceType());
			Linkage linkage = new Linkage();
			linkage.addItem().setType(LinkageType.SOURCE).setResource(new Reference(localUser.getIdElement()));
			linkage.addItem().setType(LinkageType.ALTERNATE).setResource(new Reference(nonLocalUserId));
			daoLinkageR4.create(linkage, localPartitionRequest());
			return localUser.getIdElement();

		} else {
			// return the local patient that is the source 

			List<Reference> linkedPatients = new ArrayList<>();
			for (IBaseResource res : linkageResources) {
				if (res instanceof Linkage) {
					Linkage linkage = (Linkage) res;
					for (Linkage.LinkageItemComponent linkageItem : linkage.getItem()) {
						linkedPatients.add(linkageItem.getResource());
					}
				}
			}
			Set<Reference> sourceRefs =
				linkageResources.stream()
					.filter(r -> (r instanceof Linkage))
					.map(r -> (Linkage) r)
					.flatMap(k -> k.getItem().stream())
					.filter(i -> i.getType() == LinkageType.SOURCE)
					.map(i -> i.getResource())
					.collect(java.util.stream.Collectors.toSet());
			if (sourceRefs.size() == 1) {
				return sourceRefs.iterator().next().getReferenceElement();
			} else if (sourceRefs.isEmpty()) {
				throw new InvalidRequestException("cannot establish local user resource; no local source resources found");
			} else {
				throw new InvalidRequestException("cannot establish local user resource; multiple local source resources found");
			}
		}
	}

	public IBaseResource createLocalUser(String resourceType) {
		if ("Patient".equalsIgnoreCase(resourceType))
			return createLocalPatient();
		else if ("Practitioner".equalsIgnoreCase(resourceType))
			return createLocalPractitioner();
		else
			throw new InvalidRequestException("expected a Patient or Practitioner user but encountered a " + resourceType);
	}

	public IBaseResource createLocalPatient() {
		Patient patient = new Patient();
		DaoMethodOutcome createOutcome = daoPatientR4.create(patient, localPartitionRequest());
		return createOutcome.getResource();
	}

	public IBaseResource createLocalPractitioner() {
		Practitioner practitioner = new Practitioner();
		DaoMethodOutcome createOutcome = daoPractitionerR4.create(practitioner, localPartitionRequest());
		return createOutcome.getResource();
	}
}
