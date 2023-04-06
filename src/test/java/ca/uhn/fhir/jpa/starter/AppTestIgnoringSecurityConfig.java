package ca.uhn.fhir.jpa.starter;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;

@Configuration
@Profile("ignoring-security")
public class AppTestIgnoringSecurityConfig implements WebSecurityCustomizer {

	@Override
	public void customize(WebSecurity web) {
		web.ignoring().antMatchers("/**");
	}

}
