package edu.ohsu.cmp.ecp.sds;

import org.hl7.fhir.instance.model.api.IIdType;

import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreAuth.AuthorizationProfile;

public class SupplementalDataStoreAuthProfile implements AuthorizationProfile {

	private final IIdType authorizedUserId;
	private final IIdType targetPatientId;

	public SupplementalDataStoreAuthProfile(IIdType authorizedUserId, IIdType targetPatientId ) {
		this.authorizedUserId = authorizedUserId;
		this.targetPatientId = targetPatientId;
	}

	public static AuthorizationProfile forPatient( IIdType userAndPatientId ) {
		return new SupplementalDataStoreAuthProfile( userAndPatientId, userAndPatientId ) ;
	}

	public static AuthorizationProfile forOtherPatient( IIdType userId, IIdType patientId ) {
		return new SupplementalDataStoreAuthProfile( userId, patientId ) ;
	}

	public static AuthorizationProfile forPractitioner( IIdType practitionerId ) {
		return new SupplementalDataStoreAuthProfile( practitionerId, null ) ;
	}

	@Override
	public IIdType getAuthorizedUserId() {
		return authorizedUserId;
	}

	@Override
	public IIdType getTargetPatientId() {
		return targetPatientId;
	}

}