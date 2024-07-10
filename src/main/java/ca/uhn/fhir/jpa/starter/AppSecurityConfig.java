package ca.uhn.fhir.jpa.starter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class AppSecurityConfig {

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
        	.authorizeRequests()
        		.antMatchers(HttpMethod.GET, "/**/metadata")
        		.anonymous()
		   .and()
		   .authorizeRequests()
			  .antMatchers(HttpMethod.OPTIONS)
			  .anonymous()
        	.and()
        	.authorizeRequests()
            	.anyRequest()
            	.authenticated()
            .and()
        	.oauth2ResourceServer( oauth2 -> oauth2.opaqueToken() );

        return http.build();
    } 

}
