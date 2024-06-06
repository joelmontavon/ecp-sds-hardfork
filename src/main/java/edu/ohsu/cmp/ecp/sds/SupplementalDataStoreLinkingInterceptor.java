package edu.ohsu.cmp.ecp.sds;

import static edu.ohsu.cmp.ecp.sds.SupplementalDataStorePermissionsInterceptor.getPermissions;

import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IIdType;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.interceptor.model.RequestPartitionId;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import edu.ohsu.cmp.ecp.sds.base.FhirResourceComparison;

@Interceptor
@Component
public class SupplementalDataStoreLinkingInterceptor {
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(SupplementalDataStoreLinkingInterceptor.class);

	@Inject
	SupplementalDataStoreLinkage linkage;

	@Inject
	SupplementalDataStorePartition partition;

	@Inject
	SupplementalDataStoreResourceCreation resourceCreation;

	@Inject
	SupplementalDataStoreProperties sdsProperties;
	
	@Hook(Pointcut.STORAGE_PRECOMMIT_RESOURCE_CREATED)
	public void linkNewResourceToAuthorizedUser(RequestDetails theRequestDetails, RequestPartitionId requestPartitionId) {
		Permissions permissions = getPermissions( theRequestDetails );
		permissions.readAndWriteSpecificPatient()
			.ifPresent( readAndWriteSpecificPatient -> {

					resourceCreation.resourceCreationInfo( theRequestDetails )
						.ifPresent( details -> {
							establishCompartmentOwnerForNewResource( readAndWriteSpecificPatient, details ) ;
							linkNewResourceToAuthorizedUser( readAndWriteSpecificPatient, details ) ;
						});
				});

	}

	private void establishCompartmentOwnerForNewResource(Permissions.ReadAndWriteSpecificPatient readAndWriteSpecificPatient, SupplementalDataStoreResourceCreation.Details details ) {

		/* IF a resource is created in a Patient compartment
		 * AND that compartment does not have a Patient resource to own it
		 * THEN create a stub patient resource in the compartment
		 */

		for ( IIdType patientCompartment : details.compartments() ) {
			if ( details.inherentlyClaimsCompartment( patientCompartment ) )
				continue ;

			if ( !linkage.patientCompartmentIsClaimed(patientCompartment) ) {
				if ( partition.userIsLocal( patientCompartment) )
					linkage.establishLocalUserFor( patientCompartment ) ;
				else
					linkage.establishNonLocalUser( patientCompartment ) ;
			}
		}
	}

	private void linkNewResourceToAuthorizedUser( Permissions.ReadAndWriteSpecificPatient readAndWriteSpecificPatient, SupplementalDataStoreResourceCreation.Details details ) {

		/* IF a resource is created in a Patient compartment
		 * AND that compartment is not linked to the currently targeted patient
		 * THEN create linkages between the targeted patient and the resource
		 */

		PatientCompartmentLinkingPlan plan = new PatientCompartmentLinkingPlan( readAndWriteSpecificPatient.patientId().basisUserId() ) ;
		
		/*
		 * if the local user already exists, identify it in the plan
		 */
		linkage.lookupLocalUserFor( readAndWriteSpecificPatient.patientId().basisUserId() ).ifPresent( id -> {
			plan.localCompartmentAlreadyExists( id ) ;
		});

		/* 
		 * for each compartment that owns the new resource (local and non-local), identify it in the plan
		 */
		for ( IIdType compartmentOwner : details.compartments() ) {
			plan.compartmentAlreadyExists( compartmentOwner ) ;
		}

		/* 
		 * for each compartment is linked to the local patient (if any), identify it in the plan
		 */
		plan.localPatientId().ifPresent( localUserId -> {
			for ( IBaseReference nonLocalPatientRef : linkage.patientsLinkedTo(localUserId) ) {
				IIdType nonLocalPatientId = nonLocalPatientRef.getReferenceElement() ;
				plan.nonLocalCompartmentIsAlreadyLinked( nonLocalPatientId ) ;
			}
		});

		/* 
		 * compartments to be linked are all the compartments identified in
		 * the plan EXCEPT the compartments identified as already linked
		 */
		Set<IIdType> unlinkedCompartments = FhirResourceComparison.idTypes().createSet( plan.nonLocalCompartments() ) ;
		unlinkedCompartments.removeAll( plan.linkedNonLocalCompartments() ) ;

		/* 
		 * IF the basis compartment is unlinked
		 * BUT it is the only unlinked compartment AND the local user does not already exist
		 * THEN the basis compartment does not need to be linked
		 */
		if ( unlinkedCompartments.contains( plan.basisCompartment() )
				&& unlinkedCompartments.size() == 1 
				&& plan.localPatientId().isEmpty()
				) {
			unlinkedCompartments.remove( plan.basisCompartment() ) ;
		}

		/* 
		 * IF no non-local compartments need to be linked
		 * THEN nothing more to do
		 */
		if ( unlinkedCompartments.isEmpty() )
			return ;

		/* 
		 * IF any non-local compartments need to be linked
		 * THEN ensure the local patient exists
		 */
		if ( plan.localPatientId().isEmpty() ) {

			IIdType newLocalUserId = linkage.establishLocalUser( "Patient" ) ;
			plan.localCompartmentAlreadyExists( newLocalUserId ) ;
			unlinkedCompartments.add( plan.basisCompartment() ) ;

			if ( !linkage.patientCompartmentIsClaimed( plan.basisCompartment() ) ) {
				linkage.establishNonLocalUser( plan.basisCompartment() ) ;
			}
		}
		
		/* 
		 * link each compartment that needs to be linked
		 */
		for ( IIdType patientCompartment : unlinkedCompartments ) {
			linkage.linkNonLocalPatientToLocalPatient( plan.localPatientId.get(), patientCompartment ) ;
		}
	}


	private class PatientCompartmentLinkingPlan {
		
		private final IIdType basisCompartment ;
		private Optional<IIdType> localPatientId = Optional.empty();
		private Set<IIdType> nonLocalCompartments = FhirResourceComparison.idTypes().createSet();
		private Set<IIdType> linkedNonLocalCompartments = FhirResourceComparison.idTypes().createSet();
	
		public Optional<IIdType> localPatientId() {
			return localPatientId ;
		}
		
		public IIdType basisCompartment() {
			return basisCompartment ;
		}
		
		public Set<IIdType> nonLocalCompartments() {
			return nonLocalCompartments ;
		}
		
		public Set<IIdType> linkedNonLocalCompartments() {
			return linkedNonLocalCompartments ;
		}
		
		public PatientCompartmentLinkingPlan( IIdType basisCompartment ) {
			if ( !partition.userIsNonLocal(basisCompartment))
				throw new UnsupportedOperationException( "cannot identify basis compartment as \"" + basisCompartment + "\" because it is local; basis compartments that are local is not yet supported" ) ;
			this.basisCompartment = basisCompartment ;
			this.nonLocalCompartments.add( basisCompartment ) ;
		}

		PatientCompartmentLinkingPlan basisCompartmentIsAlreadyLinked() {
			this.linkedNonLocalCompartments.add( basisCompartment ) ;
			return this ;
		}
		
		PatientCompartmentLinkingPlan localCompartmentAlreadyExists( IIdType localPatientCompartment ) {
			if ( !partition.userIsLocal(localPatientCompartment))
				throw new IllegalArgumentException( "cannot identify local patient as \"" + localPatientCompartment + "\" because it is non-local" ) ;
			this.localPatientId.ifPresent( id -> {
				if ( 0 != FhirResourceComparison.idTypes().comparator().compare(id, localPatientCompartment) ) {
					switch ( sdsProperties.getPartition().getMultipleLinkedLocalPatients() ) {
					case FAIL:
						throw new IllegalArgumentException( "local patient already identified as \"" + id + "\"; cannot re-identify as \"" + localPatientCompartment + "\"" ) ;
					case WARN:
						ourLog.warn( "local patient already identified as \"" + id + "\"; re-identifing as \"" + localPatientCompartment + "\" will cause problems with access permissions later" ) ;
					case IGNORE:
						ourLog.debug( "local patient already identified as \"" + id + "\"; re-identifing as \"" + localPatientCompartment + "\" will cause problems with access permissions later" ) ;
					default:
					}
				}
			});
			this.localPatientId = Optional.of( localPatientCompartment ) ;
			return this ;
		}
		
		PatientCompartmentLinkingPlan nonLocalCompartmentAlreadyExists( IIdType nonLocalPatientCompartment ) {
			if ( !partition.userIsNonLocal(nonLocalPatientCompartment))
				throw new IllegalArgumentException( "cannot identify non-local compartment as \"" + nonLocalPatientCompartment + "\" because it is local" ) ;
			nonLocalCompartments.add( nonLocalPatientCompartment ) ;
			return this ;
		}

		PatientCompartmentLinkingPlan compartmentAlreadyExists( IIdType patientCompartment ) {
			if ( partition.userIsLocal(patientCompartment) )
				return this.localCompartmentAlreadyExists(patientCompartment) ;
			else
				return this.nonLocalCompartmentAlreadyExists(patientCompartment) ;
		}

		PatientCompartmentLinkingPlan nonLocalCompartmentIsAlreadyLinked( IIdType nonLocalPatientCompartment ) {
			if ( !nonLocalCompartments.contains( nonLocalPatientCompartment ) )
				new IllegalArgumentException( "cannot identify non-local compartment \"" + nonLocalPatientCompartment + "\" as already linked because the local compartment has not been identified yet" ) ;
			this.linkedNonLocalCompartments.add( basisCompartment ) ;
			return this ;
		}
	}
	
}
