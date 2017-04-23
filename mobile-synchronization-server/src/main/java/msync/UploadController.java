package msync;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.RepositoryRestController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import generated.deserialize.ForeignKeySetter;
import generated.deserialize.SingleRequest;
import generated.msync.model.system.DeletedRecord;
import msync.model.AbstractEntity;

/**
 * @author Georg Causin
 */
@RepositoryRestController
public class UploadController {

    @Autowired
    private ForeignKeySetter foreignKeySetter;
    
    @SuppressWarnings("unchecked")
	@RequestMapping(method = RequestMethod.POST, value = "/users/singleRequest")
    public @ResponseBody ResponseEntity<?> saveSingleRequest(@RequestBody SingleRequest singleRequest) {

        if (singleRequest.getDeletedRecords() != null) {
        	SingleRequestGroup[] deletedRecords = singleRequest.getDeletedRecords();
        	
        	for (SingleRequestGroup singleRequestGroup : deletedRecords) {
        		for (AbstractEntity entity : singleRequestGroup.getΕntities()) {
        			DeletedRecord deletedRecord = (DeletedRecord)entity;
        			final EntityRequestHandler requestHandler = foreignKeySetter.getRequestHandler(deletedRecord.getEntityName());

                    ((Consumer<String>)requestHandler.getDelete()).accept(deletedRecord.getEntityPk());
                }
        	}
        	
        	singleRequest.setDeletedRecords(null);
        }
        
        for (SingleRequestGroup[] requestGroups : singleRequest.getRequestGroups()) {
            if (requestGroups == null || requestGroups.length == 0) {
                continue;
            }
            
            final EntityRequestHandler requestHandler = foreignKeySetter.getRequestHandler(requestGroups[0].getΕntities()[0].getClass().getSimpleName());

            for (final SingleRequestGroup requestGroup : requestGroups) {
                
                for (final AbstractEntity entity : requestGroup.getΕntities()) {
                    for (final Map.Entry<String, String> entry : requestGroup.getForeignKeys().entrySet()) {
                        ((BiConsumer<AbstractEntity, String>)requestHandler.getForeignKeySetter().get(entry.getKey())).accept(entity, entry.getValue());
                    }
                }
                
                for (AbstractEntity entity : requestGroup.getΕntities()) {
                    ((Consumer<AbstractEntity>)requestHandler.getSave()).accept(entity);
                }
            }
        }
        
        return ResponseEntity.ok("Ok");
    }
}

