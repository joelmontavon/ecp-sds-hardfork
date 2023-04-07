package edu.ohsu.cmp.ecp.sds;

import java.util.List;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRule;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRuleBuilder;
import ca.uhn.fhir.rest.server.interceptor.auth.RuleBuilder;

@Configuration
public class TestMockSupplementalDataStorePartitioningConfig  {

	private static final String MOCK_PARTITION_NAME = "MOCK-PARTITION" ;
	
	private static final SupplementalDataStoreAuthorizationInterceptor MOCK_AUTH_INTERCEPTOR =
		new TestMockSupplementalDataStoreAuthenticationInterceptor()
		;
	
	private static final SupplementalDataStorePartitionInterceptor MOCK_PARTITION_INTERCEPTOR =
		new TestMockSupplementalDataStorePartitionInterceptor( MOCK_PARTITION_NAME ) ;
		;
	
	/*
	 * permit read/write of any resources, regardless of authentication
	 */
	@Bean
	@Primary
	public SupplementalDataStoreAuthorizationInterceptor mockAuthInterceptor() {
		return MOCK_AUTH_INTERCEPTOR ;
	}

	/*
	 * for all read/write operations, use the mock partition
	 */
	@Bean
	@Primary
	public SupplementalDataStorePartitionInterceptor mockPartitionInterceptor() {
		return MOCK_PARTITION_INTERCEPTOR ;
	}
	
	private static class TestMockSupplementalDataStoreAuthenticationInterceptor extends SupplementalDataStoreAuthorizationInterceptor {
		@Override
		public List<IAuthRule> buildRuleList(RequestDetails theRequestDetails) {
			IAuthRuleBuilder ruleBuilder = new RuleBuilder();
			return ruleBuilder.allowAll("mock authentication interceptor").build() ;
		}
	}
	
	private static class TestMockSupplementalDataStorePartitionInterceptor extends SupplementalDataStorePartitionInterceptor {
		
		private final String mockPartitionName;

		public TestMockSupplementalDataStorePartitionInterceptor(String mockPartitionName) {
			this.mockPartitionName = mockPartitionName;
		}

		@Override
		protected String partitionNameFromRequest(RequestDetails theRequestDetails) {
			return mockPartitionName ;
		}

		@Override
		public void validateResourceBelongsInPartition(IBaseResource resource, String partitionName) throws InvalidRequestException {
			/* don't throw InvalidRequestException */
		}
	}
	
}
