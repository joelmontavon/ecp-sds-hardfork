package edu.ohsu.cmp.ecp.sds;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.instance.model.api.IIdType;

public class SupplementalDataStoreMatchers {

	private SupplementalDataStoreMatchers() {}

	public static BaseMatcher<IIdType> identifiesResource( IAnyResource resource ) {
		return identifiesSameResourceAs( null == resource ? null : resource.getIdElement() ) ;
	}

	public static BaseMatcher<IIdType> identifiesSameResourceAs( IIdType idType ) {
		return new IdMatcher( idType ) ;
	}

	public static class IdMatcher extends BaseMatcher<IIdType> {

		private final IIdType expectedId ;

		public IdMatcher(IIdType idType) {
			this.expectedId  = idType ;
		}

		@Override
		public boolean matches(Object item) {
			if ( null == expectedId )
				return null == item ;
			if ( !(item instanceof IIdType) )
				return false ;
			IIdType id = (IIdType)item ;

			if ( id.isEmpty() )
				return expectedId.isEmpty() ;

			if ( id.hasBaseUrl() && expectedId.hasBaseUrl() )
				if ( !id.getBaseUrl().equals( expectedId.getBaseUrl() ))
					return false ;

			if ( id.hasResourceType() && expectedId.hasResourceType() )
				if ( !id.getResourceType().equals( expectedId.getResourceType() ))
					return false ;

			if ( id.hasIdPart() && expectedId.hasIdPart() )
				if ( !id.getIdPart().equals( expectedId.getIdPart() ))
					return false ;

			if ( id.hasVersionIdPart() && expectedId.hasVersionIdPart() )
				if ( !id.getVersionIdPart().equals( expectedId.getVersionIdPart() ))
					return false ;

			return true ;
		}

		@Override
		public void describeTo(Description description) {
			description.appendText( "FHIR id identifies same resource as ") ;
			description.appendValue( expectedId ) ;
		}

	}
}
