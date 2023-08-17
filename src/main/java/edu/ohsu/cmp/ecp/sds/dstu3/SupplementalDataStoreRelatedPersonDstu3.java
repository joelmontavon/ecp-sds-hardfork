package edu.ohsu.cmp.ecp.sds.dstu3;

import org.hl7.fhir.dstu3.model.RelatedPerson;
import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.jpa.starter.annotations.OnDSTU3Condition;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreRelatedPerson;

@Component
@Conditional(OnDSTU3Condition.class)
public class SupplementalDataStoreRelatedPersonDstu3 implements SupplementalDataStoreRelatedPerson {

	@Override
	public IBaseReference patientFromRelatedPerson( IBaseResource relatedPerson ) {
		
		return ((RelatedPerson)relatedPerson).getPatient() ;
		
	}
	
}
