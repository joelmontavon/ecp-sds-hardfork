package edu.ohsu.cmp.ecp.util;

import ca.uhn.fhir.rest.api.server.RequestDetails;

public class RequestDetailsUtil {
	public static String toString(RequestDetails requestDetails) {
		if (requestDetails == null) return "null";
		return "RequestDetails{" +
			"completeUrl='" + requestDetails.getCompleteUrl() + '\'' +
			", tenantId='" + requestDetails.getTenantId() + '\'' +
			'}';
	}
}
