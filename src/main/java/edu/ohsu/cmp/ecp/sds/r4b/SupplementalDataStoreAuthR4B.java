package edu.ohsu.cmp.ecp.sds.r4b;

import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4b.model.IdType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.jpa.starter.annotations.OnR4BCondition;
import edu.ohsu.cmp.ecp.sds.base.SupplementalDataStoreAuthBase;

@Component
@Conditional(OnR4BCondition.class)
public class SupplementalDataStoreAuthR4B extends SupplementalDataStoreAuthBase {

	@Override
	protected IIdType idFromSubject(String subject) {
		return new IdType(subject.toString());
	}
}
