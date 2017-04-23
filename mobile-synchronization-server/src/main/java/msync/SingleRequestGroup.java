package msync;

import java.util.Map;

import msync.model.AbstractEntity;

public abstract class SingleRequestGroup {

    private Map<String, String> foreignKeys;
    
    abstract public AbstractEntity[] getΕntities();

    public Map<String, String> getForeignKeys() {
        return foreignKeys;
    }

    public void setForeignKeys(Map<String, String> foreignKeys) {
        this.foreignKeys = foreignKeys;
    }
}
