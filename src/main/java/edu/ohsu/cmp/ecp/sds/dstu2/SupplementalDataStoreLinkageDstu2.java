package edu.ohsu.cmp.ecp.sds.dstu2;

import java.util.List;

import javax.inject.Inject;

import org.hl7.fhir.dstu2.model.Reference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.jpa.starter.annotations.OnDSTU2Condition;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreProperties;
import edu.ohsu.cmp.ecp.sds.base.SupplementalDataStoreLinkageBase;

@Component
@Conditional(OnDSTU2Condition.class)
public class SupplementalDataStoreLinkageDstu2 extends SupplementalDataStoreLinkageBase {

	@Inject
	SupplementalDataStoreProperties sdsProperties;

	@Inject
	IFhirResourceDao<ca.uhn.fhir.model.dstu2.resource.Patient> daoPatientDstu2;
	
	@Inject
	IFhirResourceDao<ca.uhn.fhir.model.dstu2.resource.Practitioner> daoPractitionerDstu2;

	private RuntimeException linkageResourceNotDefinedInDstu2() {
		return new UnsupportedOperationException( "SDS does not support FHIR version DSTU2 because it does not include a definition for the Linkage resource" );
	}
	
	@Override
	protected List<IBaseResource> searchLinkageResources( SearchParameterMap linkageSearchParamMap, RequestDetails theRequestDetails ) {
		throw linkageResourceNotDefinedInDstu2() ;
	}

	@Override
	protected List<IBaseResource> filterLinkageResourcesHavingAlternateItem( List<IBaseResource> allLinkageResources, IIdType nonLocalPatientId ) {
		throw linkageResourceNotDefinedInDstu2() ;
	}

	@Override
	protected List<Reference> patientsFromLinkageResources(List<IBaseResource> linkageResources) {
		throw linkageResourceNotDefinedInDstu2() ;
	}

	@Override
	protected void createLinkage( IIdType sourcePatientId, IIdType alternatePatientId, RequestDetails theRequestDetails ) {
		throw linkageResourceNotDefinedInDstu2() ;
	}

	@Override
	protected List<Reference> sourcePatientsFromLinkageResources(List<IBaseResource> linkageResources) {
		throw linkageResourceNotDefinedInDstu2() ;
	}

	@Override
	public IBaseResource createLocalPatient( RequestDetails theRequestDetails ) {
		throw linkageResourceNotDefinedInDstu2() ;
	}

	@Override
	public IBaseResource createLocalPractitioner( RequestDetails theRequestDetails ) {
		throw linkageResourceNotDefinedInDstu2() ;
	}
}
