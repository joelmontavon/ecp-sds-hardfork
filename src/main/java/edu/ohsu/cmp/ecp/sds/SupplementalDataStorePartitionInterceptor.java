package edu.ohsu.cmp.ecp.sds;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.interceptor.model.RequestPartitionId;
import ca.uhn.fhir.jpa.dao.data.IPartitionDao;
import ca.uhn.fhir.jpa.entity.PartitionEntity;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

@Interceptor
@Component
public class SupplementalDataStorePartitionInterceptor {

	public static final String HEADER_PARTITION_NAME = "X-Partition-Name";

	@Inject
	SupplementalDataStoreProperties sdsProperties;

	@Inject
	IPartitionDao daoPartition;

	@PostConstruct
	public void estabshLocalPartition() {
		String localPartitionName = sdsProperties.getPartition().getLocalName();

		if (!daoPartition.findForName(localPartitionName).isPresent()) {
			daoPartition.save(newLocalPartitionEntity());
		}

	}

	@Hook(Pointcut.STORAGE_PARTITION_IDENTIFY_CREATE)
	public RequestPartitionId partitionIdentifyCreate(RequestDetails theRequestDetails) {
		final String partitionName = partitionNameFromRequest(theRequestDetails);
		validateResourceBelongsInPartition(theRequestDetails.getResource(), partitionName);

		if (!sdsProperties.getPartition().getLocalName().equals(partitionName)) {
			if (!daoPartition.findForName(partitionName).isPresent()) {
				daoPartition.save(newNonLocalPartitionEntity(partitionName));
			}
		}

		return RequestPartitionId.fromPartitionName(partitionName);
	}

	@Hook(Pointcut.STORAGE_PARTITION_IDENTIFY_READ)
	public RequestPartitionId partitionIdentifyRead(RequestDetails theRequestDetails) {
		final String partitionName = partitionNameFromRequest(theRequestDetails);
		return RequestPartitionId.fromPartitionName(partitionName);
	}


	public RequestPartitionId partitionIdFromRequest(RequestDetails theRequestDetails) {
		final String partitionName = partitionNameFromRequest(theRequestDetails);
		return RequestPartitionId.fromPartitionName(partitionName);
	}

	private String partitionNameFromRequest(RequestDetails theRequestDetails) {
		final String partitionNameHeaderValue = theRequestDetails.getHeader(HEADER_PARTITION_NAME);
		if (null != partitionNameHeaderValue)
			return partitionNameHeaderValue;
		else
			return sdsProperties.getPartition().getLocalName();
	}

	public void validateResourceBelongsInPartition(IBaseResource resource, String partitionName) throws InvalidRequestException {
		if (null == resource)
			return;

		IIdType id = resource.getIdElement();
		if (null != id) {
			if (!id.hasBaseUrl())
				throw new InvalidRequestException("id element present without base url; cannot match SDS partition");
			if (!id.getBaseUrl().equals(partitionName))
				throw new InvalidRequestException(String.format("cannot store resource identified as belonging in SDS partition \"%1$s\" into SDS partition \"%2$s\"", id.getBaseUrl(), partitionName));
		} else {
			if (!sdsProperties.getPartition().getLocalName().equals(partitionName))
				throw new InvalidRequestException(String.format("cannot store resource identified as belonging in local SDS partition into SDS partition \"%1$s\"", partitionName));
		}
	}

	private int generatePartitionId(String partitionName) {
		return partitionName.hashCode();
	}

	private PartitionEntity newLocalPartitionEntity() {
		String localPartitionName = sdsProperties.getPartition().getLocalName();

		final PartitionEntity partitionEntity = new PartitionEntity();
		partitionEntity.setId(generatePartitionId(localPartitionName));
		partitionEntity.setName(localPartitionName);
		partitionEntity.setDescription("groups new resources not originating elsewhere");
		return partitionEntity;
	}

	private PartitionEntity newNonLocalPartitionEntity(String nonLocalPartitionName) {
		final PartitionEntity partitionEntity = new PartitionEntity();
		partitionEntity.setId(generatePartitionId(nonLocalPartitionName));
		partitionEntity.setName(nonLocalPartitionName);
		partitionEntity.setDescription(String.format("groups resources originating from \"%1$s\"", nonLocalPartitionName));
		return partitionEntity;
	}
}
