package edu.ohsu.cmp.ecp.sds;

import javax.inject.Inject;
import javax.inject.Named;

import org.hl7.fhir.instance.model.api.IIdType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import ca.uhn.fhir.jpa.dao.data.IPartitionDao;
import ca.uhn.fhir.jpa.entity.PartitionEntity;
import ca.uhn.fhir.jpa.model.entity.ModelConfig;

@Component
public class SupplementalDataStorePartition {

	@Inject
	SupplementalDataStoreProperties sdsProperties;

	@Inject
	IPartitionDao daoPartition;

	@Inject
	ModelConfig modelConfig ;
	
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
    
	public boolean userIsLocal( IIdType userId ) {
		if ( !userId.hasBaseUrl() )
			return true ;
		if ( modelConfig.getTreatBaseUrlsAsLocal().contains( userId.getBaseUrl() ) )
			return true ;
		return false ;
	}
	
	public boolean userIsNonLocal( IIdType userId ) {
		return !userIsLocal( userId ) ;
	}
	
	public void establishLocalPartition() {
		String localPartitionName = sdsProperties.getPartition().getLocalName();

		doInTransaction( () -> {
			if (!daoPartition.findForName(localPartitionName).isPresent()) {
				daoPartition.save(newLocalPartitionEntity());
			}
		});
	}

	public void establishNonLocalPartition( String partitionName ) {
		doInTransaction( () -> {
			if (!daoPartition.findForName(partitionName).isPresent()) {
				daoPartition.save(newNonLocalPartitionEntity(partitionName));
			}
		});
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
