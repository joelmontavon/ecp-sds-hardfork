package edu.ohsu.cmp.ecp.sds;

import org.hl7.fhir.instance.model.api.IIdType;
import org.springframework.security.core.Authentication;

public interface SupplementalDataStorePermissions {
	
	IIdType resolveWritablePatientIdFor(IIdType authorizedUserId, Authentication authentication) ;
	
}
