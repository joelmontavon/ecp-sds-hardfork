package edu.ohsu.cmp.ecp.sds.base;

import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IIdType;

public class FhirResourceComparison {

	private static final Comparison<IBaseReference> REFERENCES = new ComparisonImpl<>( new ReferenceComparator() );
	private static final Comparison<IIdType> IDTYPES = new ComparisonImpl<>( new IdTypeComparator() );

	public static <T extends IBaseReference> Comparison<T> references() { return (Comparison<T>)REFERENCES ; }
	public static <T extends IIdType> Comparison<T> idTypes() { return (Comparison<T>)IDTYPES ; }

	public interface Comparison<T> {
		Comparator<T> comparator() ;
		Set<T> createSet() ;
		Set<T> createSet( Collection<? extends T> c ) ;
		<U> Set<T> createSet( Collection<? extends U> c, Function<U,T> f ) ;
	}
	
	private static class ComparisonImpl<T> implements Comparison<T> {
		private final Comparator<T> comparator ;
		public ComparisonImpl( Comparator<T> comparator ) { this.comparator = comparator ; }
		public Comparator<T> comparator() { return comparator ; }
		public Set<T> createSet() { return new TreeSet<>( comparator ) ; }
		public Set<T> createSet( Collection<? extends T> c ) {
			Set<T> set = createSet() ;
			set.addAll( c );
			return set ;
		}
		public <U> Set<T> createSet( Collection<? extends U> c, Function<U,T> f ) {
			Set<T> set = createSet() ;
			c.stream().map( f ).forEach( set::add );
			return set ;
		}
		
	}
	
	private static class ReferenceComparator implements Comparator<IBaseReference> { 
		@Override
		public int compare(IBaseReference ref1, IBaseReference ref2) {
			IIdType id1 = ref1.getReferenceElement() ;
			IIdType id2 = ref2.getReferenceElement() ;
			
			if ( null == id1 )
				return -1 ;
			if ( null == id2 )
				return +1 ;

			return idTypes().comparator().compare(id1, id2) ;
		}

	}

	private static class IdTypeComparator implements Comparator<IIdType> { 
		@Override
		public int compare(IIdType id1, IIdType id2) {
			
			if ( id1.hasBaseUrl() && id2.hasBaseUrl() ) {
				int cmp = id1.getBaseUrl().compareTo(id2.getBaseUrl()) ;
				if ( cmp != 0 )
					return cmp ;
			}
			/* if either object has no baseUrl, ignore baseUrl in comparison */
			
			
			if ( id1.hasResourceType() && id2.hasResourceType() ) {
				int cmp = id1.getResourceType().compareTo(id2.getResourceType()) ;
				if ( cmp != 0 )
					return cmp ;
			}
			/* if either object has no resourceType, ignore resourceType in comparison */
			
			if ( id1.hasIdPart() && id2.hasIdPart() ) {
				int cmp = id1.getIdPart().compareTo(id2.getIdPart()) ;
				if ( cmp != 0 )
					return cmp ;
			}
			/* if either object has no idPart, stop the comparison because idPart is required */
			if ( !id1.hasIdPart() )
				return -1 ;
			if ( !id2.hasIdPart() )
				return +1 ;
					
			if ( id1.hasVersionIdPart() && id2.hasVersionIdPart() ) {
				int cmp = id1.getVersionIdPart().compareTo(id2.getVersionIdPart()) ;
				if ( cmp != 0 )
					return cmp ;
			}
			/* if either object has no versionIdPart, ignore versionIdPart in comparison */
			
			/* all components equal or ignored */
			return 0 ;
		}
		
	}

}
