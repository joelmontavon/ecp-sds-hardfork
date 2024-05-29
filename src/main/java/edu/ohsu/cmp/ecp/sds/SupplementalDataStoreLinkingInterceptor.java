package edu.ohsu.cmp.ecp.sds;

import static java.util.stream.Collectors.joining;

import java.util.Collection;
import java.util.Optional;
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
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreAuth.AuthorizationProfile;
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

	@Inject
	SupplementalDataStorePartition partition;
	
	public static final String REQUEST_ATTR_PERMISSIONS = "SDS-AUTH-PERMISSIONS" ;

	public static final class UserIdentity {
		private final String userResourceType;
		private final Optional<IIdType> basisUserId;
		private final IIdType localUserId;
		private final Set<IIdType> nonLocalUserIds = FhirResourceComparison.idTypes().createSet() ;

		private void requireMatchingIdType( IIdType id ) {
			if ( !this.userResourceType.equalsIgnoreCase( id.getResourceType() ) )
				throw new IllegalArgumentException( "cannot form a \"" + this.userResourceType + "\" user identity with a " + id.getResourceType() + " id" ) ;
		}

		public UserIdentity( Optional<IIdType> basisUserId, IIdType localUserId, Collection<? extends IIdType> nonLocalUserIds ) {
			this.basisUserId = basisUserId;
			this.localUserId = localUserId;
			this.nonLocalUserIds.addAll(nonLocalUserIds) ;
			this.userResourceType = localUserId.getResourceType();
			basisUserId.ifPresent( this::requireMatchingIdType );
			nonLocalUserIds.forEach( this::requireMatchingIdType );
		}
		
		public String userResourceType() {
			return userResourceType;
		}

		public Optional<IIdType> basisUserId() {
			return basisUserId;
		}

		public IIdType localUserId() {
			return localUserId;
		}

		public Set<IIdType> nonLocalUserIds() {
			return nonLocalUserIds;
		}

		public UserIdentity withAdditionalNonLocalUserId(IIdType additionalNonLocalUserId) {
			Set<IIdType> expandedNonLocalUserIds = FhirResourceComparison.idTypes().createSet( nonLocalUserIds ) ;
			expandedNonLocalUserIds.add( additionalNonLocalUserId ) ;
			return new UserIdentity( basisUserId, localUserId, expandedNonLocalUserIds );
		}

		private String userIdToString( IIdType userId ) {
			String format ;
			if ( userId.hasBaseUrl() ) {
				format = "%3$s (%1$s)" ;
			} else {
				format = "%3$s" ;
			}
			return String.format( format, userId.getBaseUrl(), userId.getResourceType(), userId.getIdPart() ) ;
		}

		@Override
		public String toString() {
			String format ;
			if ( basisUserId.isPresent() && !nonLocalUserIds.isEmpty() )
				format = "[%1$s] %2$s\nauthorized as %3$s\na.k.a. %4$s" ;
			else if ( basisUserId.isPresent() )
				format = "[%1$s] %2$s\nauthorized as %3$s" ;
			else if ( nonLocalUserIds.isEmpty() )
				format = "[%1$s] %2$s\na.k.a. %4$s" ;
			else
				format = "[%1$s] %2$s" ;
			return String.format(
					format,
					userResourceType,
					localUserId,
					basisUserId.map(this::userIdToString).orElse("-none-"),
					nonLocalUserIds.stream().map( this::userIdToString ).collect( joining(", ", "[ ", " ]" ) )
				);
		}

	}
	
	public static final class Permissions {
		private final IIdType authorizedUserId;
		private final Optional<ReadAllPatients> readAllPatients ;
		private final Optional<ReadAndWriteSpecificPatient> readAndWriteSpecificPatient ;

		public Permissions( ReadAllPatients readAllPatients ) {
			this.readAllPatients = Optional.of(readAllPatients);
			authorizedUserId = readAllPatients.authorizedUserId() ;
			this.readAndWriteSpecificPatient = Optional.empty();
		}

		public Permissions( ReadAndWriteSpecificPatient readAndWriteSpecificPatient) {
			this.readAllPatients = Optional.empty();
			this.readAndWriteSpecificPatient = Optional.of( readAndWriteSpecificPatient );
			authorizedUserId = readAndWriteSpecificPatient.authorizedUserId() ;
		}

		public IIdType authorizedNonLocalUserId() {
			return authorizedUserId ;
		}

		public Optional<ReadAllPatients> readAllPatients() {
			return readAllPatients ;
		}
		public Optional<ReadAndWriteSpecificPatient> readAndWriteSpecificPatient() {
			return readAndWriteSpecificPatient ;
		}

		public static final class ReadAllPatients {
			private final IIdType authorizedUserId;

			public ReadAllPatients(IIdType authorizedUserId) {
				this.authorizedUserId = authorizedUserId;
			}

			public IIdType authorizedUserId() {
				return authorizedUserId ;
			}
		}

		public static final class ReadAndWriteSpecificPatient {
			private final IIdType authorizedUserId;

			private final UserIdentity patientId;

			public ReadAndWriteSpecificPatient(IIdType authorizedUserId, UserIdentity patientId) {
				this.authorizedUserId = authorizedUserId;
				this.patientId = patientId;
			}

			public IIdType authorizedUserId() {
				return authorizedUserId ;
			}

			public UserIdentity patientId() {
				return patientId ;
			}

			public ReadAndWriteSpecificPatient withUpdatedPatientIdentity( UserIdentity replacementPatientId ) {
				return new ReadAndWriteSpecificPatient( authorizedUserId, replacementPatientId ) ;
			}
		}
	}
	
	public static Permissions getPermissions(RequestDetails theRequestDetails) {
		return (Permissions)theRequestDetails.getAttribute(REQUEST_ATTR_PERMISSIONS) ;
	}
	
	@Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_HANDLED)
	public void identifyPermissions(RequestDetails theRequestDetails) {
		
		/*
		 * identify the authorized user
		 * and store the ID in the RequestDetails
		 */
		
		AuthorizationProfile authProfile = auth.authorizationProfile(theRequestDetails);
		if ( null == authProfile )
			return ;

		IIdType authorizedUserId = authProfile.getAuthorizedUserId();

		/*
		 * identify or create the authorized local User
		 * and store the info in the RequestDetails
		 */

		if ( "Practitioner".equalsIgnoreCase( authorizedUserId.getResourceType() ) ) {
			theRequestDetails.setAttribute(
				REQUEST_ATTR_PERMISSIONS,
				permissionsForPractitioner( authorizedUserId )
			);
		} else {
			theRequestDetails.setAttribute(
				REQUEST_ATTR_PERMISSIONS,
				permissionsForPatient( authorizedUserId, authProfile.getTargetPatientId(), theRequestDetails )
			);
		}
	}
		
	private UserIdentity buildUserIdentity( IIdType basisNonLocalUserId ) {

		IIdType localUserId = linkage.establishLocalUserFor(basisNonLocalUserId);

		return buildUserIdentity( localUserId, Optional.of(basisNonLocalUserId) ) ;
	}

	private UserIdentity buildUserIdentity( IIdType localUserId, Optional<IIdType> basisUserId ) {

		Set<IIdType> nonLocalPatientIds = FhirResourceComparison.idTypes().createSet() ;
		basisUserId.filter(partition::userIsNonLocal).ifPresent( nonLocalPatientIds::add ) ;

		for ( IBaseReference nonLocalUser : linkage.patientsLinkedTo(localUserId) ) {
			IIdType nonLocalUserId = nonLocalUser.getReferenceElement();
			nonLocalPatientIds.add( nonLocalUserId );
		}

		return new UserIdentity(basisUserId, localUserId, nonLocalPatientIds) ;
	}

	private Permissions permissionsForPractitioner( IIdType authorizedUserId ) {
		return new Permissions( new Permissions.ReadAllPatients(authorizedUserId) ) ;
	}

	private Permissions permissionsForPatient( IIdType authorizedUserId, IIdType authorizedPatientId, RequestDetails theRequestDetails ) {


		UserIdentity targetPatientId = buildUserIdentity( authorizedPatientId );
		/*
		 * IF the request is a non-local Patient update
		 * AND the Patient id is not already linked to an existing local patient
		 * THEN flag the patient compartment for permission to create
		 */
		Optional<PatientUpdateDetails> patientUpdateDetails = isPatientUpdateWithId( theRequestDetails ) ;
		if ( patientUpdateDetails.isPresent() ) {
			IIdType claimingPatientId = patientUpdateDetails.get().patientId() ;
			Set<? extends IBaseReference> claimedByLocalPatients = linkage.patientsLinkedFrom(claimingPatientId) ;
			Set<? extends IBaseReference> claimedByNonLocalPatients = linkage.patientsLinkedTo(claimingPatientId) ;

			Set<IIdType> claimedByLocalPatientIds = FhirResourceComparison.idTypes().createSet( claimedByLocalPatients, IBaseReference::getReferenceElement ) ;
			claimedByLocalPatientIds.remove( targetPatientId.localUserId() ) ;
			Set<IIdType> claimedByNonLocalPatientIds = FhirResourceComparison.idTypes().createSet( claimedByNonLocalPatients, IBaseReference::getReferenceElement ) ;
			claimedByNonLocalPatientIds.removeAll( targetPatientId.nonLocalUserIds() ) ;
			
			if ( claimedByLocalPatientIds.isEmpty() && claimedByNonLocalPatients.isEmpty() ) {
				targetPatientId = targetPatientId.withAdditionalNonLocalUserId( claimingPatientId ) ;
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

		return new Permissions( new Permissions.ReadAndWriteSpecificPatient( authorizedUserId, targetPatientId ) ) ;
	}

	@Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_CREATED)
	public void linkNewNonLocalPatientToLocalPatient(RequestDetails theRequestDetails, RequestPartitionId requestPartitionId) {
		
		/*
		 * IF the request is a non-local Patient update
		 * AND the Patient id is not already linked to an existing local patient
		 * THEN add the Patient id to the local patient Linkage
		 */
		isNonLocalPatientUpdateWithId( theRequestDetails, requestPartitionId ).ifPresent( details -> {
			Permissions permissions = getPermissions( theRequestDetails );
			permissions.readAndWriteSpecificPatient.ifPresent( (readAndWriteSpecificPatient) -> {
				IIdType localPatientId = readAndWriteSpecificPatient.patientId().localUserId() ;
				linkage.linkNonLocalPatientToLocalPatient( localPatientId, details.patientId() ) ;
			});
		});
	}

	private static class PatientUpdateDetails {

		private final IIdType patientId;

		public PatientUpdateDetails(IIdType patientId) {
			this.patientId = patientId;
		}

		public IIdType patientId() {
			return patientId;
		}
	}

	private Optional<PatientUpdateDetails> isPatientUpdateWithId( RequestDetails theRequestDetails ) {
		if ( theRequestDetails.getRestOperationType() != RestOperationTypeEnum.UPDATE )
			return Optional.empty() ;
		if ( null == theRequestDetails.getResource() )
			return Optional.empty() ;
		IBaseResource resource = theRequestDetails.getResource() ;
		if ( !"Patient".equals( resource.fhirType() ) )
			return Optional.empty() ;
		if ( null == resource.getIdElement() )
			return Optional.empty() ;
		
		return Optional.of( new PatientUpdateDetails( resource.getIdElement() ) );
	}
	
	private Optional<PatientUpdateDetails> isNonLocalPatientUpdateWithId( RequestDetails theRequestDetails, RequestPartitionId requestPartitionId ) {
		String localPartitionName = sdsProperties.getPartition().getLocalName();
		if ( localPartitionName.equals( requestPartitionId.getFirstPartitionNameOrNull() ) )
			return Optional.empty() ;
		
		return isPatientUpdateWithId(theRequestDetails) ;
	}
	
}
