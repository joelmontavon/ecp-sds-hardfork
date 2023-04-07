package edu.ohsu.cmp.ecp.sds.dstu2;

import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.dstu2.model.IdType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.jpa.starter.annotations.OnDSTU2Condition;
import edu.ohsu.cmp.ecp.sds.base.SupplementalDataStoreAuthBase;

@Component
@Conditional(OnDSTU2Condition.class)
public class SupplementalDataStoreAuthDstu2 extends SupplementalDataStoreAuthBase {

	@Override
	protected IIdType idFromSubject(String subject) {
		return new IdType(subject.toString());
	}
}
