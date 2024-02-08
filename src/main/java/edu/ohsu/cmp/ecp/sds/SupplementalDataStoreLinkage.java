package edu.ohsu.cmp.ecp.sds;

import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IIdType;

import java.util.Set;

public interface SupplementalDataStoreLinkage {

	Set<? extends IBaseReference> patientsLinkedTo(IIdType sourcePatientId);
	Set<? extends IBaseReference> patientsLinkedFrom(IIdType alternatePatientId);

	IIdType establishLocalUserFor(IIdType nonLocalPatientId);

	void linkNonLocalPatientToLocalPatient(IIdType localPatientId, IIdType nonLocalPatientId);
}
