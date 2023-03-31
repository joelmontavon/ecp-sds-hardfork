package edu.ohsu.cmp.ecp.sds;

import ca.uhn.fhir.rest.api.server.RequestDetails;
import org.hl7.fhir.instance.model.api.IIdType;

public interface SupplementalDataStoreAuth {

	IIdType authorizedPatientId(RequestDetails theRequestDetails);
}
