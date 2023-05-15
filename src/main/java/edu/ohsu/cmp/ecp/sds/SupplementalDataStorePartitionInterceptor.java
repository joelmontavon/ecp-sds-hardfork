package edu.ohsu.cmp.ecp.sds;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.interceptor.model.RequestPartitionId;
import ca.uhn.fhir.jpa.dao.data.IPartitionDao;
import ca.uhn.fhir.jpa.entity.PartitionEntity;
import ca.uhn.fhir.jpa.partition.IRequestPartitionHelperSvc;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Iterator;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;

@Interceptor
@Component
public class SupplementalDataStorePartitionInterceptor {

	public static final String HEADER_PARTITION_NAME = "X-Partition-Name";

	@Inject
	SupplementalDataStoreProperties sdsProperties;

	@Inject
	IRequestPartitionHelperSvc requestPartitionHelperSvc;
	
	@Inject
	IPartitionDao daoPartition;

    @Inject
    @Named("transactionManager")
    protected PlatformTransactionManager txManager;
    
    private void doInTransaction( Runnable task ) {
    	TransactionTemplate tmpl = new TransactionTemplate(txManager);
    	
        tmpl.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
            	task.run() ;
            }
        });
    }
    
	@PostConstruct
	public void establishLocalPartition() {
		String localPartitionName = sdsProperties.getPartition().getLocalName();

		/* during @PostConstruct, the dao transaction must be established */
		doInTransaction( () -> {
			
			if (!daoPartition.findForName(localPartitionName).isPresent()) {
				daoPartition.save(newLocalPartitionEntity());
			}
			
		}) ;

	}

	public void establishNonLocalPartition( String partitionName ) {
		if (!daoPartition.findForName(partitionName).isPresent()) {
			daoPartition.save(newNonLocalPartitionEntity(partitionName));
		}
	}
	
	@Hook(Pointcut.STORAGE_PARTITION_IDENTIFY_CREATE)
	public RequestPartitionId partitionIdentifyCreate(RequestDetails theRequestDetails) {
		if ( !requestPartitionHelperSvc.isResourcePartitionable(theRequestDetails.getResourceName()) )
			return RequestPartitionId.defaultPartition() ;

		RequestPartitionId partitionId = partitionIdFromRequest( theRequestDetails ) ;

		validateAndEstablishNamedPartition( partitionId, theRequestDetails ) ;

		return partitionId;
	}

	protected void validateAndEstablishNamedPartition( RequestPartitionId partitionId, RequestDetails theRequestDetails ) {

		if ( partitionId.hasPartitionNames() ) {
			for ( String partitionName : partitionId.getPartitionNames() ) {

				validateResourceBelongsInPartition(theRequestDetails.getResource(), partitionName);

				if (!sdsProperties.getPartition().getLocalName().equals(partitionName)) {
					establishNonLocalPartition( partitionName ) ;
				}
			}
		}
	}

	@Hook(Pointcut.STORAGE_PARTITION_IDENTIFY_READ)
	public RequestPartitionId partitionIdentifyRead(RequestDetails theRequestDetails) {
		if ( !requestPartitionHelperSvc.isResourcePartitionable(theRequestDetails.getResourceName()) )
			return RequestPartitionId.defaultPartition() ;

		return partitionIdFromRequest( theRequestDetails ) ;
	}


	public RequestPartitionId partitionIdFromRequest(RequestDetails theRequestDetails) {
		final String partitionName = partitionNameFromRequest(theRequestDetails);
		return RequestPartitionId.fromPartitionName(partitionName);
	}

	protected String partitionNameFromRequest(RequestDetails theRequestDetails) {
		final String partitionNameHeaderValue = theRequestDetails.getHeader(HEADER_PARTITION_NAME);
		if (StringUtils.isNotBlank(partitionNameHeaderValue))
			return partitionNameHeaderValue;
		else
			return sdsProperties.getPartition().getLocalName();
	}

	public void validateResourceBelongsInPartition(IBaseResource resource, String partitionName) throws InvalidRequestException {
		if (null == resource)
			return;

		IIdType id = resource.getIdElement();
		if (id == null || StringUtils.isBlank(id.getIdPart())) {
			// create (PROs, etc.)

			if ( ! StringUtils.equals(sdsProperties.getPartition().getLocalName(), partitionName) ) {
				throw new InvalidRequestException(String.format("cannot store resource identified as belonging in local SDS partition into SDS partition \"%1$s\"", partitionName));
			}

		} else {
			// clone from foreign, or update existing

			if (sdsProperties.getRequireBaseUrl() && ! id.hasBaseUrl()) {
				throw new InvalidRequestException("id element present without base url; cannot match SDS partition");

			} else if (id.hasBaseUrl() && ! StringUtils.equals(id.getBaseUrl(), partitionName)) {
				throw new InvalidRequestException(String.format("cannot store resource identified as belonging in SDS partition \"%1$s\" into SDS partition \"%2$s\"", id.getBaseUrl(), partitionName));
			}
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
