package edu.ohsu.cmp.ecp.sds;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "sds")
public class SupplementalDataStoreProperties {

	private Partition partition = new Partition();

	public Partition getPartition() {
		return partition;
	}

	public void setPartition(Partition partition) {
		this.partition = partition;
	}

	public static class Partition {

		private String localName;

		public String getLocalName() {
			return localName;
		}

		public void setLocalName(String localPartitionId) {
			this.localName = localPartitionId;
		}
	}
}
