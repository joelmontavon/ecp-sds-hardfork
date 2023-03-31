# Supplemental Data Store

## Implementation

### Partitioning

#### Partitioning - Configuration

Configure the name to use for the partition that holds local resources.

> ##### Example - Partitioning - Configuration
> In application.properties:
>
> `sds.partition.local-name: SDS-LOCAL`
>

#### Implementation

Implement a bean to read the configuration.

Implement an [interceptor](https://hapifhir.io/hapi-fhir/docs/interceptors/server_interceptors.html)
to [extract the partition name](https://hapifhir.io/hapi-fhir/docs/server_jpa_partitioning/partitioning.html)
from a custom header.
HAPI
provides [some examples](https://hapifhir.io/hapi-fhir/docs/server_jpa_partitioning/partition_interceptor_examples.html).

Implement a bean that configures partitioning and registers the interceptor.

> ##### Example - Partitioning - Implementation
>
> Add SupplementalDataStoreProperties.java to the project
> where it can be component-scanned.
>
> Add SupplementalDataStorePartitionInterceptor.java to the project.
>
> Add SupplementalDataStorePartitioningConfig.java to the project
> where it can be component-scanned.
>

### Authorization

#### Configuration

Configure the introspection endpoint for authenticating bearer tokens.

> ##### Example - Authorization - Configuration
> In application.properties:
>
> `spring.security.oauth2.resourceserver.opaque-token.introspection-uri: https://my-ehr.org/oauth2/introspect`
>

#### Dependencies

Include dependencies for Spring Boot support for OAuth2.

> ##### Example - Authorization - Dependencies
> In pom.xml:
>
> ```xml
> <dependency>
> 	<groupId>org.springframework.boot</groupId>
> 	<artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
> </dependency>
> <dependency>
>     <groupId>com.nimbusds</groupId>
>     <artifactId>oauth2-oidc-sdk</artifactId>
>     <version>9.25</version>
>     <scope>runtime</scope>
> </dependency>
> ```
>

#### Implementation - Authorizing the Introspection

Implement beans that cause the introspection process to use the token
that is being introspect-ed as the authorization to use
the introspect service.

> ##### Example - Authorization - Implementation - Introspect
>
> Add ApplicationOpaqueTokenIntrospector.java
> and IntrospectorReflexiveAuthenticationInterceptor.java to the project
> where they can be component-scanned.
>

#### Implementation - Supplemental Data Store Requirements

Implement an [interceptor](https://hapifhir.io/hapi-fhir/docs/interceptors/server_interceptors.html)
to setup rules based on
data [from a successful the authentication](https://hapifhir.io/hapi-fhir/docs/security/authorization_interceptor.html).

Implement a bean to extract the subject from the authentication.

Implement a bean to search Linkage resources in the local partition.

> ##### Example - Authorization - Implementation - SDS
>
> Add SupplementalDataStoreAuthorizationInterceptor.java to the project.
>
> Add SupplementalDataStoreAuth.java SupplementalDataStoreAuthR4.java to the project
> where it can be component-scanned.
>
> Add SupplementalDataStoreLinkage.java and SupplementalDataStoreLinkageR4.java to the project
> where it can be component-scanned.
> 
