package edu.ohsu.cmp.ecp.sds.base;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import javax.inject.Inject;

import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.UrlType;

import ca.uhn.fhir.interceptor.model.RequestPartitionId;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreLinkage;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStorePartition;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreProperties;

public abstract class SupplementalDataStoreLinkageBase implements SupplementalDataStoreLinkage {

	protected static final String EXTENSION_URL_SDS_PARTITION_NAME = "urn:sds:partition-name";
	protected static final String EXTENSION_URL_RESOURCE_SDS_LINKAGE_TARGET_STUB = "urn:sds:linkage-target-stub";

	@Inject
	SupplementalDataStoreProperties sdsProperties;

	@Inject
	SupplementalDataStorePartition partition;

	private RequestDetails localPartitionRequest() {
		SystemRequestDetails internalRequestForLocalPartition = new SystemRequestDetails();
		internalRequestForLocalPartition.setRequestPartitionId(RequestPartitionId.fromPartitionName(sdsProperties.getPartition().getLocalName()));
		return internalRequestForLocalPartition;
	}

	private RequestDetails nonLocalPartitionRequest( IIdType nonLocalResourceId ) {
		if ( !nonLocalResourceId.hasBaseUrl() )
			throw new InvalidRequestException("cannot operate on a resource in non-local partition without a base url to identify the partition");
		String nonLocalPartitionName = nonLocalResourceId.getBaseUrl();
		partition.establishNonLocalPartition( nonLocalPartitionName );
		return nonLocalPartitionRequest( nonLocalPartitionName ) ;
	}
	
	private RequestDetails nonLocalPartitionRequest( String nonLocalPartitionName ) {
		SystemRequestDetails internalRequestForNonLocalPartition = new SystemRequestDetails();
		internalRequestForNonLocalPartition.setRequestPartitionId(RequestPartitionId.fromPartitionName( nonLocalPartitionName ));
		return internalRequestForNonLocalPartition;
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
	
	protected abstract Set<? extends IBaseReference> alternatePatientsFromLinkageResources(List<? extends IBaseResource> linkageResources) ;

	protected abstract Set<? extends IBaseReference> sourcePatientsFromLinkageResources(List<? extends IBaseResource> linkageResources) ;
	
	@Override
	public Set<? extends IBaseReference> patientsLinkedTo(IIdType localPatientId) {

		List<IBaseResource> linkageResources = linkageResourcesHavingSourceItem(localPatientId);

		Set<? extends IBaseReference> linkedPatients = alternatePatientsFromLinkageResources( linkageResources );
		return linkedPatients;
	}

	@Override
	public Set<? extends IBaseReference> patientsLinkedFrom(IIdType nonLocalPatientId) {
		
		List<IBaseResource> linkageResources = linkageResourcesHavingAlternateItem(nonLocalPatientId);
		
		Set<? extends IBaseReference> linkedPatients = sourcePatientsFromLinkageResources( linkageResources );
		return linkedPatients;
	}
	
	protected abstract void createLinkage( IIdType sourcePatientId, IIdType alternatePatientId, RequestDetails theRequestDetails ) ;

	@Override
	public Optional<IIdType> lookupLocalUserFor(IIdType userId) {
		if (null == userId)
			throw new InvalidRequestException("cannot lookup local user resource without another user id to search for a linkage");
		if ( partition.userIsLocal(userId))
			return Optional.of(userId) ;
		
		IIdType nonLocalUserId = userId ;

		List<IBaseResource> linkageResources = linkageResourcesHavingAlternateItem(nonLocalUserId);

		if (linkageResources.isEmpty()) {

			return Optional.empty() ;

		} else {
			// return the local patient that is the source 

			Set<? extends IBaseReference> sourceRefs = sourcePatientsFromLinkageResources(linkageResources) ;
			
			if (sourceRefs.size() == 1) {
				return Optional.of( sourceRefs.iterator().next().getReferenceElement() );
			} else if (sourceRefs.isEmpty()) {
				throw new InvalidRequestException("cannot lookup local user resource; no local source resources found");
			} else {
				throw new InvalidRequestException("cannot lookup local user resource; multiple local source resources found");
			}
		}
	}

	@Override
	public IIdType establishLocalUserFor(IIdType userId) {
		if ( partition.userIsLocal(userId))
			return userId ;
		IIdType nonLocalUserId = userId ;

		return lookupLocalUserFor(nonLocalUserId).orElseGet( () -> {
		
			IBaseResource localUser = createLocalUser(nonLocalUserId.getResourceType());
			/*
			 * TODO: this should be skipped IF the non-local user is being created by this request
			 */
			/*
			 * TODO: this should be skipped IF the non-local user already exists
			 */
			IBaseResource nonLocalStubUser = createNonLocalStubUser(nonLocalUserId);
			IIdType localUserId = localUser.getIdElement().toUnqualifiedVersionless() ;
			IIdType newlyCreatedNonLocalUserId = fullyQualifiedIdForStubUser(nonLocalStubUser);
			createLinkage( localUserId, newlyCreatedNonLocalUserId, localPartitionRequest() ) ;
			return localUserId;

		} ) ;
	}

	@Override
	public void linkNonLocalPatientToLocalPatient(IIdType localPatientId, IIdType nonLocalPatientId) {
		if (null == localPatientId)
			throw new InvalidRequestException("cannot link patient resources without a local patient id for initial linkage");
		if (null == nonLocalPatientId)
			throw new InvalidRequestException("cannot link patient resources without a non-local patient id for initial linkage");

		createLinkage( localPatientId, nonLocalPatientId, localPartitionRequest() ) ;
	}

	public IBaseResource createLocalUser(String resourceType) {
		if ("Patient".equalsIgnoreCase(resourceType))
			return createLocalPatient( localPartitionRequest() );
		else if ("Practitioner".equalsIgnoreCase(resourceType))
			return createLocalPractitioner( localPartitionRequest() );
		else if ("RelatedPerson".equalsIgnoreCase(resourceType))
			return createLocalRelatedPerson( localPartitionRequest() );
		else
			throw new InvalidRequestException("cannot create local user resource: expected a Patient or Practitioner user but encountered a " + resourceType);
	}

	public IBaseResource createNonLocalStubUser(IIdType nonLocalUserId) {
		String resourceType = nonLocalUserId.getResourceType() ;
		if ("Patient".equalsIgnoreCase(resourceType))
			return createNonLocalStubPatient( nonLocalUserId, nonLocalPartitionRequest(nonLocalUserId) );
		else if ("Practitioner".equalsIgnoreCase(resourceType))
			return createNonLocalStubPractitioner( nonLocalUserId, nonLocalPartitionRequest(nonLocalUserId) );
		else if ("RelatedPerson".equalsIgnoreCase(resourceType))
			return createNonLocalStubRelatedPerson( nonLocalUserId, nonLocalPartitionRequest(nonLocalUserId) );
		else
			throw new InvalidRequestException("cannot create local user resource: expected a Patient or Practitioner user but encountered a " + resourceType);
	}
	
	abstract public IBaseResource createLocalPatient( RequestDetails theRequestDetails ) ;
	
	abstract public IBaseResource createLocalPractitioner( RequestDetails theRequestDetails ) ;
	
	abstract public IBaseResource createLocalRelatedPerson( RequestDetails theRequestDetails ) ;

	abstract public IIdType fullyQualifiedIdForStubUser( IBaseResource userResource ) ;
	
	abstract public IBaseResource createNonLocalStubPatient( IIdType nonLocalPatientId, RequestDetails theRequestDetails ) ;
	
	abstract public IBaseResource createNonLocalStubPractitioner( IIdType nonLocalPractitionerId, RequestDetails theRequestDetails ) ;
	
	abstract public IBaseResource createNonLocalStubRelatedPerson( IIdType nonLocalRelatedPersonId, RequestDetails theRequestDetails ) ;
	
}
