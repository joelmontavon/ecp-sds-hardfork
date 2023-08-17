package edu.ohsu.cmp.ecp.sds.r5;

import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r5.model.RelatedPerson;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.jpa.starter.annotations.OnR5Condition;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreRelatedPerson;

@Component
@Conditional(OnR5Condition.class)
public class SupplementalDataStoreRelatedPersonR5 implements SupplementalDataStoreRelatedPerson {

	@Override
	public IBaseReference patientFromRelatedPerson( IBaseResource relatedPerson ) {
		
		return ((RelatedPerson)relatedPerson).getPatient() ;
		
	}
	
}
