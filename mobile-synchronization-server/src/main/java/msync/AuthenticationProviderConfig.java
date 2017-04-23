package msync;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.jdbc.JdbcDaoImpl;

@Configuration
public class AuthenticationProviderConfig {

	@Autowired
	private Environment env;
	
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Bean(name="userDetailsService")
    public UserDetailsService userDetailsService() {
    	
    	JdbcDaoImpl jdbcImpl = new JdbcDaoImpl();
    	
    	jdbcImpl.setJdbcTemplate(jdbcTemplate);
    	jdbcImpl.setUsersByUsernameQuery(getUserQuery());
    	jdbcImpl.setAuthoritiesByUsernameQuery(getAuthoritiesQuery());
    	
    	return jdbcImpl;
    }
    
    private String sqlSchema;

    public String getSqlSchema() {
        if (sqlSchema == null) {
            sqlSchema = env.getProperty("schema");
        }
        
        return sqlSchema;
    }

    private String getUserQuery() {
    	
        return "SELECT "
        		+ "u.name as username,"
        		+ "a.password,"
        		+ "a.enabled "
                + "FROM \"" + getSqlSchema() + "\".\"User\" u "
        		+ "join \"" + getSqlSchema() + "\".\"Account\" a on a.userFk = u.pk "
                + "WHERE u.name ilike ?";
    }

    private String getAuthoritiesQuery() {
    	
        return "SELECT "
        		+ "u.name as username,"
        		+ "r.name as authority "
                + "FROM \"" + getSqlSchema() + "\".\"User\" u "
        		+ "join \"" + getSqlSchema() + "\".\"UserRole\" ur on ur.userFk = u.pk "
        		+ "join \"" + getSqlSchema() + "\".\"Role\" r on r.pk = ur.roleFk "
                + "WHERE u.name ilike ?";
    }
}

