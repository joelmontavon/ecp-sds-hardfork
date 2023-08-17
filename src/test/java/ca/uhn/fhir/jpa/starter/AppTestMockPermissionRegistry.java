package ca.uhn.fhir.jpa.starter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class AppTestMockPermissionRegistry {
	
	private final Map<String,Map<String,Permission>> permissions = new HashMap<>() ;
	
	public enum Permission {
		NONE,
		READ,
		READWRITE
	}
	
	public interface PersonBuilder {
		PermissionBuilder permitsPerson( String personId ) ;
	}
	
	public interface PermissionBuilder {
		PermissionBuilder toRead() ;
		PermissionBuilder toReadAndWrite() ;
		PersonBuilder andPermitsPerson( String personId ) ;
	}
	
	public PersonBuilder person( String personId ) {
		Map<String,Permission> personPermissions =
			permissions.computeIfAbsent( personId , (k) -> new HashMap<>() )
			;
		return new PersonBuilder() {
			
			private PersonBuilder personBuilder = this ;
			
			public PermissionBuilder permitsPerson( String permittedPersonId ) {
				
				Permission initialPerm = personPermissions.computeIfAbsent( permittedPersonId, (k) -> Permission.NONE ) ;
				
				return new PermissionBuilder() {
					
					private boolean readPermitted = initialPerm == Permission.READ || initialPerm == Permission.READWRITE ;
					private boolean writePermitted = initialPerm == Permission.READWRITE ;

					@Override
					public PermissionBuilder toRead() {
						readPermitted = true ;
						return permit();
					}

					@Override
					public PermissionBuilder toReadAndWrite() {
						readPermitted = true ;
						writePermitted = true ;
						return permit();
					}

					private PermissionBuilder permit() {
						Permission perm ;
						if ( readPermitted && writePermitted )
							perm = Permission.READWRITE ;
						else if ( readPermitted )
							perm = Permission.READ ;
						else
							perm = Permission.NONE ;
						personPermissions.put( permittedPersonId, perm ) ;
						return this ;
					}
					
					@Override
					public PersonBuilder andPermitsPerson(String personId) {
						return personBuilder;
					}
					
				};
			
			}
			
		};
	}
	
	public List<String> permittedToReadAndWrite( String permittedPersonId ) {
		List<String> personIds = new ArrayList<>() ;
		permissions.forEach( (personId,permissions) -> {
			if ( Permission.READWRITE == permissions.get( permittedPersonId ) )
				personIds.add( personId ) ;
		});
		return personIds ;
	}
}
