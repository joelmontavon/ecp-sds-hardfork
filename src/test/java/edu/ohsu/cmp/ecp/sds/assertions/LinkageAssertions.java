package edu.ohsu.cmp.ecp.sds.assertions;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

import java.util.List;
import java.util.function.Predicate;

import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Linkage;

import edu.ohsu.cmp.ecp.sds.base.FhirResourceComparison;
import edu.ohsu.cmp.ecp.sds.util.SdsLinkageOperations;

public class LinkageAssertions {
	private final SdsLinkageOperations sdsLinkageOps ;

	public LinkageAssertions( SdsLinkageOperations sdsLinkageOperations ) {
		this.sdsLinkageOps = sdsLinkageOperations ;
	}

	public SdsLinkageOperations operations() {
		return sdsLinkageOps;
	}

	public void assertAbsent( IIdType patientId ) {
		List<Linkage> linkages = sdsLinkageOps.searchByItem( patientId ) ;
		assertThat( linkages, empty() ) ;
	}

	public List<Linkage> assertPresent( IIdType patientId ) {
		List<Linkage> linkages = sdsLinkageOps.searchByItem( patientId ) ;
		assertThat( linkages, not( empty() ) ) ;
		return linkages ;
	}

	public List<Linkage> assertLinked( IIdType patientId, IIdType expectedLinkedPatientId ) {
		List<Linkage> linkages = assertPresent( patientId ) ;

		Predicate<Linkage.LinkageItemComponent> itemMatches = item -> {
				IIdType idReferencedByItem = item.getResource().getReferenceElement();
				return 0 == FhirResourceComparison.idTypes().comparator().compare( idReferencedByItem, expectedLinkedPatientId ) ;
			};
		List<Linkage> linkagesMatchingExpected =
			linkages.stream()
				.filter( k -> k.getItem().stream().anyMatch( itemMatches ) )
				.collect( toList() )
				;
		assertThat( linkagesMatchingExpected, not( empty() )) ;

		return linkagesMatchingExpected ;
	}

}