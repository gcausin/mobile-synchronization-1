package msync;

import javax.servlet.Filter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.query.spi.EvaluationContextExtension;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.data.repository.query.SecurityEvaluationContextExtension;

import com.github.ziplet.filter.compression.CompressingFilter;

import generated.deserialize.ForeignKeySetter;

@SpringBootApplication
@EntityScan(basePackages = "generated")
@EnableJpaRepositories(basePackages = "generated")
@PropertySources({
    @PropertySource("classpath:application.properties"),
    @PropertySource("classpath:generated.application.properties")
})
public class Server extends SpringBootServletInitializer {
    
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(Server.class);
    }

	public static void main(String[] args) {
		SpringApplication.run(Server.class);
	}

    @Bean(name = "dmlCheckService")
    public DmlCheckService dmlCheckService() {
        return new DmlCheckService();
    }
    
    @Bean(name = "foreignKeySetter")
    public ForeignKeySetter foreignKeySetter() {
        return new ForeignKeySetter();
    }
    
	@Configuration
	@EnableGlobalMethodSecurity(prePostEnabled = true)
	@EnableWebSecurity
	static class SecurityConfiguration extends WebSecurityConfigurerAdapter {

		@Autowired	
		UserDetailsService userDetailsService;
		
		@Override
		public void configure(AuthenticationManagerBuilder auth) throws Exception {			 
			auth.userDetailsService(userDetailsService);
		}	

		@Override
		protected void configure(HttpSecurity http) throws Exception {
			super.configure(http);

	        http.csrf().disable();
		}
	}

	@Bean
	EvaluationContextExtension securityExtension() {
		return new SecurityEvaluationContextExtension();
	}
	
	@Bean
	public Filter compressingFilter() {
		CompressingFilter compressingFilter = new CompressingFilter();
		
	    return compressingFilter;
	}
}
