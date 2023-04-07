package edu.ohsu.cmp.ecp.sds.base;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;

import ca.uhn.fhir.interceptor.model.RequestPartitionId;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreLinkage;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreProperties;

public abstract class SupplementalDataStoreLinkageBase implements SupplementalDataStoreLinkage {

	@Inject
	SupplementalDataStoreProperties sdsProperties;

	private RequestDetails localPartitionRequest() {
		SystemRequestDetails internalRequestForLocalPartition = new SystemRequestDetails();
		internalRequestForLocalPartition.setRequestPartitionId(RequestPartitionId.fromPartitionName(sdsProperties.getPartition().getLocalName()));
		return internalRequestForLocalPartition;
	}

	protected abstract List<IBaseResource> searchLinkageResources( SearchParameterMap linkageSearchParamMap, RequestDetails theRequestDetails );

	protected abstract List<IBaseResource> filterLinkageResourcesHavingAlternateItem( List<IBaseResource> linkageResources, IIdType nonLocalPatientId );
	
	protected List<IBaseResource> linkageResourcesHavingAlternateItem(IIdType nonLocalPatientId) {
		SearchParameterMap linkageSearchParamMap = new SearchParameterMap();
		linkageSearchParamMap.add("item", new ReferenceParam(nonLocalPatientId));

		List<IBaseResource> allLinkageResources = searchLinkageResources(linkageSearchParamMap, localPartitionRequest());

		List<IBaseResource> linkageResources = filterLinkageResourcesHavingAlternateItem( allLinkageResources, nonLocalPatientId );
		
		return linkageResources;
	}

	protected List<IBaseResource> linkageResourcesHavingSourceItem(IIdType localPatientId) {
		SearchParameterMap linkageSearchParamMap = new SearchParameterMap();
		linkageSearchParamMap.add("source", new ReferenceParam(localPatientId));

		List<IBaseResource> linkageResources = searchLinkageResources(linkageSearchParamMap, localPartitionRequest());
		return linkageResources;
	}

	protected abstract List<? extends IBaseReference> patientsFromLinkageResources(List<IBaseResource> linkageResources) ;
	
	protected abstract List<? extends IBaseReference> sourcePatientsFromLinkageResources(List<IBaseResource> linkageResources) ;

	@Override
	public List<? extends IBaseReference> patientsLinkedTo(IIdType localPatientId) {

		List<IBaseResource> linkageResources = linkageResourcesHavingSourceItem(localPatientId);

		List<? extends IBaseReference> linkedPatients = patientsFromLinkageResources( linkageResources );
		return linkedPatients;
	}

	protected abstract void createLinkage( IIdType sourcePatientId, IIdType alternatePatientId, RequestDetails theRequestDetails ) ;

	@Override
	public IIdType establishLocalUserFor(IIdType nonLocalUserId) {
		if (null == nonLocalUserId)
			throw new InvalidRequestException("cannot establish local user resource without a non-local user id for initial linkage");

		List<IBaseResource> linkageResources = linkageResourcesHavingAlternateItem(nonLocalUserId);

		if (linkageResources.isEmpty()) {

			IBaseResource localUser = createLocalUser(nonLocalUserId.getResourceType());
			createLinkage( localUser.getIdElement(), nonLocalUserId, localPartitionRequest() ) ;
			return localUser.getIdElement();

		} else {
			// return the local patient that is the source 

			Set<? extends IBaseReference> sourceRefs = new HashSet<IBaseReference>( sourcePatientsFromLinkageResources(linkageResources) ) ;
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
			return createLocalPatient( localPartitionRequest() );
		else if ("Practitioner".equalsIgnoreCase(resourceType))
			return createLocalPractitioner( localPartitionRequest() );
		else
			throw new InvalidRequestException("expected a Patient or Practitioner user but encountered a " + resourceType);
	}

	abstract public IBaseResource createLocalPatient( RequestDetails theRequestDetails ) ;
	
	abstract public IBaseResource createLocalPractitioner( RequestDetails theRequestDetails ) ;
	
}
