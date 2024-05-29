package edu.ohsu.cmp.ecp.sds;

import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IIdType;

import java.util.Optional;
import java.util.Set;

public interface SupplementalDataStoreLinkage {

	Set<? extends IBaseReference> patientsLinkedTo(IIdType sourcePatientId);
	Set<? extends IBaseReference> patientsLinkedFrom(IIdType alternatePatientId);

	Optional<IIdType> lookupLocalUserFor(IIdType userId);
	IIdType establishLocalUserFor(IIdType userId);

	void linkNonLocalPatientToLocalPatient(IIdType localPatientId, IIdType nonLocalPatientId);
}
