package edu.ohsu.cmp.ecp.sds;

import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;

public interface SupplementalDataStoreRelatedPerson {

	IBaseReference patientFromRelatedPerson(IBaseResource relatedPerson);
}
