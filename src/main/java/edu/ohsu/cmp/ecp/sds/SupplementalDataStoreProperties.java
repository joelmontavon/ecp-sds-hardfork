package edu.ohsu.cmp.ecp.sds;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "sds")
public class SupplementalDataStoreProperties {

	private boolean requireBaseUrl = true ;
	private Partition partition = new Partition();

	public boolean getRequireBaseUrl() {
		return requireBaseUrl;
	}

	public void setRequireBaseUrl(boolean requireBaseUrl) {
		this.requireBaseUrl = requireBaseUrl;
	}

	public Partition getPartition() {
		return partition;
	}

	public void setPartition(Partition partition) {
		this.partition = partition;
	}

	public static class Partition {

		private String localName;
		private String httpHeaderName = "X-Partition-Name";

		public String getLocalName() {
			return localName;
		}

		public void setLocalName(String localPartitionId) {
			this.localName = localPartitionId;
		}

		public String getHttpHeader() {
			return httpHeaderName;
		}

		public void setHttpHeader(String httpHeaderName) {
			this.httpHeaderName = httpHeaderName;
		}
		
	}
}
