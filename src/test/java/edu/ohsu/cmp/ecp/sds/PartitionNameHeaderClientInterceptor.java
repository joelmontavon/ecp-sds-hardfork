package edu.ohsu.cmp.ecp.sds;

import java.io.IOException;

import ca.uhn.fhir.rest.client.api.IClientInterceptor;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.IHttpRequest;
import ca.uhn.fhir.rest.client.api.IHttpResponse;

class PartitionNameHeaderClientInterceptor implements IClientInterceptor {

	private final String partitionName;

	public PartitionNameHeaderClientInterceptor(String partitionName) {
		this.partitionName = partitionName;
	}

	@Override
	public void interceptRequest(IHttpRequest theRequest) {
		theRequest.addHeader( SupplementalDataStorePartitionInterceptor.HEADER_PARTITION_NAME, partitionName );
	}

	@Override
	public void interceptResponse(IHttpResponse theResponse) throws IOException {
		/* no modifications to the response */
	}
	
}