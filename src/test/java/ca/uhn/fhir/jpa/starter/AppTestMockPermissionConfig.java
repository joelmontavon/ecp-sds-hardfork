package ca.uhn.fhir.jpa.starter;

import java.util.List;

import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.IdType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;

import edu.ohsu.cmp.ecp.sds.SupplementalDataStorePermissions;
import junit.framework.AssertionFailedError;

@Configuration
public class AppTestMockPermissionConfig {

	@Autowired
	AppTestMockPermissionRegistry permissionRegistry ;

	@Bean @Primary
	public SupplementalDataStorePermissions mockSupplementalDataStorePermissions() {
		return new MockSupplementalDataStorePermissions() ;
	}
	
	public class MockSupplementalDataStorePermissions implements SupplementalDataStorePermissions {
		
		@Override
		public IIdType resolveWritablePatientIdFor(IIdType authorizedUserId, Authentication authentication) {
			List<String> personIds = permissionRegistry.permittedToReadAndWrite( authorizedUserId.toString() ) ;
			if ( personIds.size() > 1 )
				throw new AssertionFailedError("expected at most 1 patient per non-patient authorized user id but found (" + personIds.size() + ")") ;
			if ( personIds.isEmpty() )
				return null ;
			return new IdType( personIds.get(0) );
		}
	}

}
