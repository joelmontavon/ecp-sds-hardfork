package edu.ohsu.cmp.ecp.sds.r5;

import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r5.model.IdType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.jpa.starter.annotations.OnR5Condition;
import edu.ohsu.cmp.ecp.sds.base.SupplementalDataStoreAuthBase;

@Component
@Conditional(OnR5Condition.class)
public class SupplementalDataStoreAuthR5 extends SupplementalDataStoreAuthBase {

	@Override
	protected IIdType idFromSubject(String subject) {
		return new IdType(subject.toString());
	}
}
