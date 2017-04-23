package msync;

import java.util.HashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import msync.model.AbstractEntity;

public class EntityRequestHandler {

    private HashMap<String, BiConsumer<? extends AbstractEntity, String>> foreignKeySetter;
    private Consumer<? extends AbstractEntity> save;
    private Consumer<String> delete;
    
    public HashMap<String, BiConsumer<? extends AbstractEntity, String>> getForeignKeySetter() {
        return foreignKeySetter;
    }
    
    public void setForeignKeySetter(HashMap<String, BiConsumer<? extends AbstractEntity, String>> foreignKeySetter) {
        this.foreignKeySetter = foreignKeySetter;
    }
    
    public Consumer<? extends AbstractEntity> getSave() {
        return save;
    }
    
    public void setSave(Consumer<? extends AbstractEntity> save) {
        this.save = save;
    }

	public Consumer<String> getDelete() {
		return delete;
	}

	public void setDelete(Consumer<String> delete) {
		this.delete = delete;
	}
}    

