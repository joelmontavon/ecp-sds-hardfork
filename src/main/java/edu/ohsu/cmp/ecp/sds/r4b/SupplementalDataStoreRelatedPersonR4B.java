package edu.ohsu.cmp.ecp.sds.r4b;

import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4b.model.RelatedPerson;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.jpa.starter.annotations.OnR4BCondition;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreRelatedPerson;

@Component
@Conditional(OnR4BCondition.class)
public class SupplementalDataStoreRelatedPersonR4B implements SupplementalDataStoreRelatedPerson {

	@Override
	public IBaseReference patientFromRelatedPerson( IBaseResource relatedPerson ) {
		
		return ((RelatedPerson)relatedPerson).getPatient() ;
		
	}
	
}
