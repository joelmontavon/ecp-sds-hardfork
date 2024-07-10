package edu.ohsu.cmp.ecp.sds;

import java.util.List;

import javax.inject.Inject;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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

	private static final SupplementalDataStoreAuthorizationInterceptor MOCK_AUTH_INTERCEPTOR =
		new TestMockSupplementalDataStoreAuthenticationInterceptor()
		;
	
	@Inject
	SupplementalDataStoreProperties sdsProperties;
	
	/*
	 * permit read/write of any resources, regardless of authentication
	 */
	@Bean
	@Primary
	@ConditionalOnMissingBean(AuthAwareTestConfig.class)
	public SupplementalDataStoreAuthorizationInterceptor mockAuthInterceptor() {
		return MOCK_AUTH_INTERCEPTOR ;
	}

	/*
	 * for all read/write operations, use the default partition
	 */
	@Bean
	@Primary
	@ConditionalOnMissingBean(PartitionAwareTestConfig.class)
	public SupplementalDataStorePartition mockPartition() {
		final String localPartitionName = sdsProperties.getPartition().getLocalName();
		return new TestMockSupplementalDataStorePartition( localPartitionName ) ;
	}
	
	@Bean
	@Primary
	@ConditionalOnMissingBean(PartitionAwareTestConfig.class)
	public SupplementalDataStorePartitionInterceptor mockPartitionInterceptor() {
		return new TestMockSupplementalDataStorePartitionInterceptor() ;
	}
	
	private static class TestMockSupplementalDataStoreAuthenticationInterceptor extends SupplementalDataStoreAuthorizationInterceptor {
		@Override
		public List<IAuthRule> buildRuleList(RequestDetails theRequestDetails) {
			IAuthRuleBuilder ruleBuilder = new RuleBuilder();
			return ruleBuilder.allowAll("mock authentication interceptor").build() ;
		}
	}
	
	private static class TestMockSupplementalDataStorePartition extends SupplementalDataStorePartition {
		
		private final String mockPartitionName;

		public TestMockSupplementalDataStorePartition(String mockPartitionName) {
			this.mockPartitionName = mockPartitionName;
		}

		@Override
		protected String partitionNameFromRequest(RequestDetails theRequestDetails) {
			return mockPartitionName ;
		}

	}
	
	private static class TestMockSupplementalDataStorePartitionInterceptor extends SupplementalDataStorePartitionInterceptor {
		
		@Override
		public void validateResourceBelongsInPartition(IBaseResource resource, String partitionName) throws InvalidRequestException {
			/* don't throw InvalidRequestException */
		}
	}
	
}
