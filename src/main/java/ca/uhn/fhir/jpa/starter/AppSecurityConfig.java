package ca.uhn.fhir.jpa.starter;

import static java.util.Arrays.asList;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import ca.uhn.fhir.jpa.starter.annotations.OnCorsPresent;

@Configuration
@EnableWebSecurity
public class AppSecurityConfig {

	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(AppSecurityConfig.class);

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		ourLog.info("requiring OAuth2 on security filter chain");
        http
        	.authorizeRequests()
        		.antMatchers(HttpMethod.GET, "/**/metadata")
        		.anonymous()
        	.and()
        	.authorizeRequests()
            	.anyRequest()
            	.authenticated()
            .and()
        	.oauth2ResourceServer( oauth2 -> oauth2.opaqueToken() );
            ;
        return http.build();
    } 

	@Bean
	@Conditional(OnCorsPresent.class)
	public SecurityFilterChain filterChainWithCors(HttpSecurity http) throws Exception {
		ourLog.info("enabling CORS on security filter chain");
		return
			http
				.cors()
				.and()
				.build()
				;
	}
	
	@Bean
	@Conditional(OnCorsPresent.class)
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(asList("*"));
		configuration.setAllowedMethods(asList("*"));
		configuration.setAllowedHeaders(asList("*"));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
