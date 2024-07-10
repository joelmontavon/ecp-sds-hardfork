package edu.ohsu.cmp.ecp.sds.dstu2;

import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.jpa.starter.annotations.OnDSTU2Condition;
import ca.uhn.fhir.model.dstu2.resource.RelatedPerson;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreRelatedPerson;

@Component
@Conditional(OnDSTU2Condition.class)
public class SupplementalDataStoreRelatedPersonDstu2 implements SupplementalDataStoreRelatedPerson {

	@Override
	public IBaseReference patientFromRelatedPerson( IBaseResource relatedPerson ) {
		
		return ((RelatedPerson)relatedPerson).getPatient() ;
		
	}
	
}
