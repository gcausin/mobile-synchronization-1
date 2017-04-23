package msync;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import msync.model.AbstractEntity;

public class DmlCheckService implements InitializingBean {

	interface AuthorizationCheck {
		public Boolean isAuthorized(String principalName, String entityName, String pk);
	}
	
	private AuthorizationCheck authorizationCheck;
	
    @Autowired
    private Environment env;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String schema;
    
    @Override
	public void afterPropertiesSet() throws Exception {
    	if (Boolean.parseBoolean(env.getProperty("log.debug"))) {
        	authorizationCheck = (principalName, entityName, pk) -> isPrincipalOwnerWithLogging(principalName, entityName, pk);
    	} else {
        	authorizationCheck = (principalName, entityName, pk) -> isPrincipalOwner(principalName, entityName, pk);
    	}
	}

	public boolean isDeleteOk(String principalName, String entityName, String pk) {
		Boolean result = authorizationCheck.isAuthorized(principalName, entityName, pk);
		
		return result == null || result;
    }

    public boolean isUpsertOk(String principalName, AbstractEntity entity) {
    	Boolean result = authorizationCheck.isAuthorized(principalName, entity.getClass().getSimpleName(), entity.getPk()); 
    	
        if (result == null) {
        	return principalName.equals(entity.getUser().getName());
        }
        
    	return result;
    }
    
    private Boolean isPrincipalOwner(String principalName, String entityName, String pk) {
        return jdbcTemplate.queryForObject(
						        		"select * from \"" + getSchema() + "\".\"IsOwner\"(?, ?, ?)",
						        		new Object[] { principalName, entityName, pk },
						        		Boolean.class);
    }
    
    private Boolean isPrincipalOwnerWithLogging(String principalName, String entityName, String pk) {
        long started = System.currentTimeMillis();
        Boolean result = isPrincipalOwner(principalName, entityName, pk);
        
        System.out.println("isPrincipalOwner(\"" + principalName + "\" ,\"" + entityName + "\" ,\"" +  pk + "\") is " +
        						(result == null ? "undefined" : result) + " and needed " + (System.currentTimeMillis() - started) + " ms.");
        
        return result;
    }
    
    private String getSchema() {
        if (schema == null) {
            schema = env.getProperty("schema");
        }
        
        return schema;
    }
}
