package edu.ohsu.cmp.ecp.sds;

import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IIdType;

import java.util.List;

public interface SupplementalDataStoreLinkage {

	List<? extends IBaseReference> patientsLinkedTo(IIdType sourcePatientId);

	IIdType establishLocalUserFor(IIdType nonLocalUserId);
}
