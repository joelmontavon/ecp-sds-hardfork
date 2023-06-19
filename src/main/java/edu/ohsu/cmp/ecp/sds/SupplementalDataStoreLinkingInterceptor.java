package edu.ohsu.cmp.ecp.sds;

import static edu.ohsu.cmp.ecp.sds.SupplementalDataStoreLinkingInterceptor.getAuthorizedLocalPatientId;
import static java.util.stream.Collectors.joining;

import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.interceptor.model.RequestPartitionId;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import edu.ohsu.cmp.ecp.sds.base.FhirResourceComparison;

@Interceptor
@Component
public class SupplementalDataStoreLinkingInterceptor {
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(SupplementalDataStoreLinkingInterceptor.class);

	@Inject
	SupplementalDataStoreProperties sdsProperties;

	@Inject
	SupplementalDataStoreAuth auth;

	@Inject
	SupplementalDataStoreLinkage linkage;

	public static final String REQUEST_ATTR_NONLOCAL_PATIENT_ID = "SDS-AUTH-NONLOCAL-PATIENT-ID" ;
	public static final String REQUEST_ATTR_LOCAL_PATIENT_ID = "SDS-AUTH-LOCAL-PATIENT-ID" ;
	public static final String REQUEST_ATTR_CLAIMING_NONLOCAL_PATIENT_ID = "SDS-CLAIMING-NONLOCAL-PATIENT-ID" ;
	
	public static IIdType getAuthorizedNonLocalPatientId(RequestDetails theRequestDetails) {
		return (IIdType)theRequestDetails.getAttribute(REQUEST_ATTR_NONLOCAL_PATIENT_ID) ;
	}
	
	public static IIdType getAuthorizedLocalPatientId(RequestDetails theRequestDetails) {
		return (IIdType)theRequestDetails.getAttribute(REQUEST_ATTR_LOCAL_PATIENT_ID) ;
	}
	
	public static IIdType getClaimingNonLocalPatientId(RequestDetails theRequestDetails) {
		return (IIdType)theRequestDetails.getAttribute(REQUEST_ATTR_CLAIMING_NONLOCAL_PATIENT_ID) ;
	}
	
	@Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_HANDLED)
	public void identifyLocalPatient(RequestDetails theRequestDetails) {
		
		/*
		 * identify the authorized non-local Patient
		 * and store the ID in the RequestDetails
		 */
		
		IIdType authorizedNonLocalUserId = auth.authorizedPatientId(theRequestDetails);
		theRequestDetails.setAttribute(REQUEST_ATTR_NONLOCAL_PATIENT_ID, authorizedNonLocalUserId);

		/*
		 * identify or create the authorized local Patient
		 * and store the ID in the RequestDetails
		 */
		
		IIdType authorizedLocalUserId = linkage.establishLocalUserFor(authorizedNonLocalUserId);
		theRequestDetails.setAttribute(REQUEST_ATTR_LOCAL_PATIENT_ID, authorizedLocalUserId);

		/*
		 * IF the request is a non-local Patient update
		 * AND the Patient id is not already linked to an existing local patient
		 * THEN flag the patient compartment for permission to create
		 */
		if ( isPatientUpdateWithId( theRequestDetails ) ) {
			IIdType claimingPatientId = theRequestDetails.getResource().getIdElement() ;
			Set<? extends IBaseReference> claimedByLocalPatients = linkage.patientsLinkedFrom(claimingPatientId) ;
			Set<? extends IBaseReference> claimedByNonLocalPatients = linkage.patientsLinkedTo(claimingPatientId) ;

			Set<IIdType> claimedByLocalPatientIds = FhirResourceComparison.idTypes().createSet( claimedByLocalPatients, IBaseReference::getReferenceElement ) ;
			claimedByLocalPatientIds.remove( authorizedLocalUserId ) ;
			Set<IIdType> claimedByNonLocalPatientIds = FhirResourceComparison.idTypes().createSet( claimedByNonLocalPatients, IBaseReference::getReferenceElement ) ;
			claimedByNonLocalPatientIds.remove( authorizedNonLocalUserId ) ;
			
			if ( claimedByLocalPatientIds.isEmpty() && claimedByNonLocalPatients.isEmpty() ) {
				theRequestDetails.setAttribute(REQUEST_ATTR_CLAIMING_NONLOCAL_PATIENT_ID, claimingPatientId);
			}
			if ( !claimedByLocalPatientIds.isEmpty() ) {
				ourLog.warn(
					String.format(
						"attempt to claim \"%1$s\" prohibited because it is already claimed by %2$s",
						claimingPatientId,
						claimedByLocalPatientIds.stream().map( IIdType::getIdPart ).collect( joining(", ", "[", "]") )
					)
				);
			}
			if ( !claimedByNonLocalPatients.isEmpty() ) {
				ourLog.warn(
					String.format(
						"attempt to claim \"%1$s\" prohibited because it is already claimed by %2$s",
						claimingPatientId,
						claimedByNonLocalPatientIds.stream().map( IIdType::getIdPart ).collect( joining(", ", "[", "]") )
					) 
				) ;
			}
		}
}

	@Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_CREATED)
	public void linkNewNonLocalPatientToLocalPatient(RequestDetails theRequestDetails, RequestPartitionId requestPartitionId) {
		
		/*
		 * IF the request is a non-local Patient update
		 * AND the Patient id is not already linked to an existing local patient
		 * THEN add the Patient id to the local patient Linkage
		 */
		if ( isNonLocalPatientUpdateWithId( theRequestDetails, requestPartitionId ) ) {
			IIdType nonLocalPatientId = theRequestDetails.getResource().getIdElement() ;
		
			IIdType authorizedLocalUserId = getAuthorizedLocalPatientId( theRequestDetails );
			linkage.linkNonLocalPatientToLocalPatient( authorizedLocalUserId, nonLocalPatientId ) ;
		}
	}

	private boolean isPatientUpdateWithId( RequestDetails theRequestDetails ) {
		if ( theRequestDetails.getRestOperationType() != RestOperationTypeEnum.UPDATE )
			return false ;
		if ( null == theRequestDetails.getResource() )
			return false ;
		IBaseResource resource = theRequestDetails.getResource() ;
		if ( !"Patient".equals( resource.fhirType() ) )
			return false ;
		if ( null == resource.getIdElement() )
			return false ;
		
		return true ;
	}
	
	private boolean isNonLocalPatientUpdateWithId( RequestDetails theRequestDetails, RequestPartitionId requestPartitionId ) {
		String localPartitionName = sdsProperties.getPartition().getLocalName();
		if ( localPartitionName.equals( requestPartitionId.getFirstPartitionNameOrNull() ) )
			return false ;
		
		return isPatientUpdateWithId(theRequestDetails) ;
	}
	
}
