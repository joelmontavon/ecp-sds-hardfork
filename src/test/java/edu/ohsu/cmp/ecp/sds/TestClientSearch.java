package edu.ohsu.cmp.ecp.sds;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Linkage;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import edu.ohsu.cmp.ecp.sds.base.FhirResourceComparison;

public class TestClientSearch {
	
	private final IGenericClient client;
	
	public TestClientSearch( IGenericClient client ) {
		this.client = client ;
	}

	public List<Linkage> searchLinkages() {
		List<Linkage> allLinkages =
			resourcesFromBundle( client.search().forResource(Linkage.class).returnBundle( Bundle.class ).execute(), Linkage.class )
			;
		return allLinkages ;
	}

	public List<Linkage> searchLinkagesWhereItemRefersTo( IIdType id ) {
		/*
		 * server is failing to find existing LINKAGE resources while searching on ITEM
		 *
		List<Linkage> linkages =
			resourcesFromBundle( clientLocal.search().forResource(Linkage.class).where( Linkage.ITEM.hasId(patId) ).returnBundle( Bundle.class ).execute(), Linkage.class )
				;
		*/
		
		/* workaround: search ALL linkages and filter on item */
		List<Linkage> allLinkages =
			resourcesFromBundle( client.search().forResource(Linkage.class).returnBundle( Bundle.class ).execute(), Linkage.class )
			;
		List<Linkage> linkages =
			allLinkages.stream()
				.filter( hasItemWhere( refersTo(id) ) )
				.collect( toList() )
				;
		return linkages ;
	}

	private static Predicate<Linkage.LinkageItemComponent> refersTo( IIdType id ) {
		Predicate<IIdType> p = (id2) ->  FhirResourceComparison.idTypes().comparator().compare(id2,id) == 0;
		return i -> i.hasResource() && i.getResource().hasReference() && p.test( i.getResource().getReferenceElement() ) ;
	}
	
	private static Predicate<Linkage> hasItemWhere( Predicate<Linkage.LinkageItemComponent> where ) {
		return lk -> lk.getItem().stream().anyMatch( where ) ;
	}

	private <T extends IBaseResource> List<T> resourcesFromBundle( Bundle bundle, Class<T> resourceType ) {
		List<T> resources = new ArrayList<>() ;

		while ( null != bundle ) {
			bundle.getEntry().stream()
				.filter( Bundle.BundleEntryComponent::hasResource )
				.map( Bundle.BundleEntryComponent::getResource )
				.filter( resourceType::isInstance )
				.map( resourceType::cast )
				.forEach( resources::add )
				;
			if ( bundle.getLink(IBaseBundle.LINK_NEXT) != null ) {
				bundle = client.loadPage().next(bundle).execute();
			} else {
				bundle = null ;
			}
		}

		return resources ;

	}

}
