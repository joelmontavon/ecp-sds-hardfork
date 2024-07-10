package edu.ohsu.cmp.ecp.sds.r4;

import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.RelatedPerson;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.jpa.starter.annotations.OnR4Condition;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreRelatedPerson;

@Component
@Conditional(OnR4Condition.class)
public class SupplementalDataStoreRelatedPersonR4 implements SupplementalDataStoreRelatedPerson {

	@Override
	public IBaseReference patientFromRelatedPerson( IBaseResource relatedPerson ) {
		
		return ((RelatedPerson)relatedPerson).getPatient() ;
		
	}
	
}
