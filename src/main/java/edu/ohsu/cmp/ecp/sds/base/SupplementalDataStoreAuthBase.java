package edu.ohsu.cmp.ecp.sds.base;

import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreAuth;
import org.hl7.fhir.instance.model.api.IIdType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;

public abstract class SupplementalDataStoreAuthBase implements SupplementalDataStoreAuth {

	public IIdType authorizedPatientId(RequestDetails theRequestDetails) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (null == authentication)
			throw new AuthenticationException(Msg.code(644) + "Missing or Invalid Authorization");

		Object principal = authentication.getPrincipal() ;
		if ( null == principal )
			throw new AuthenticationException(Msg.code(644) + "Missing or Invalid Principal");
		
		if ( !(principal instanceof OAuth2AuthenticatedPrincipal) )
			return null;

		OAuth2AuthenticatedPrincipal oauth2Principal = (OAuth2AuthenticatedPrincipal) authentication.getPrincipal();
		Object subject = oauth2Principal.getAttribute("sub");
		if (null == subject)
			throw new AuthenticationException(Msg.code(644) + "Missing or Invalid Subject");

		return idFromSubject(subject.toString());
	}
	
	protected abstract IIdType idFromSubject( String subject ) ;
}
