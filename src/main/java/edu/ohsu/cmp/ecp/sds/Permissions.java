package edu.ohsu.cmp.ecp.sds;

import java.util.Optional;

import org.hl7.fhir.instance.model.api.IIdType;

public final class Permissions {
	private final IIdType authorizedUserId;
	private final Optional<Permissions.ReadAllPatients> readAllPatients ;
	private final Optional<Permissions.ReadAndWriteSpecificPatient> readAndWriteSpecificPatient ;

	public Permissions( Permissions.ReadAllPatients readAllPatients ) {
		this.readAllPatients = Optional.of(readAllPatients);
		authorizedUserId = readAllPatients.authorizedUserId() ;
		this.readAndWriteSpecificPatient = Optional.empty();
	}

	public Permissions( Permissions.ReadAndWriteSpecificPatient readAndWriteSpecificPatient) {
		this.readAllPatients = Optional.empty();
		this.readAndWriteSpecificPatient = Optional.of( readAndWriteSpecificPatient );
		authorizedUserId = readAndWriteSpecificPatient.authorizedUserId() ;
	}

	public IIdType authorizedUserId() {
		return authorizedUserId ;
	}

	public Optional<Permissions.ReadAllPatients> readAllPatients() {
		return readAllPatients ;
	}
	public Optional<Permissions.ReadAndWriteSpecificPatient> readAndWriteSpecificPatient() {
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

		public Permissions.ReadAndWriteSpecificPatient withUpdatedPatientIdentity( UserIdentity replacementPatientId ) {
			return new ReadAndWriteSpecificPatient( authorizedUserId, replacementPatientId ) ;
		}
	}
}