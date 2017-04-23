package msync.model;

import java.time.LocalDateTime;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;

import generated.msync.model.system.User;

@MappedSuperclass
public abstract class AbstractEntity {

	@Id
	private String pk;
	
	@Column(nullable = false)
	private LocalDateTime modifiedDate;

    public void setModifiedDate(LocalDateTime modifiedDate) {
        this.modifiedDate = modifiedDate;
    }
    
    public LocalDateTime getModifiedDate() {
        return modifiedDate;
    }

    public String getPk() {
		if (pk == null) {
			pk = UUID.randomUUID().toString();
		}
		return pk;
	}

	public void setPk(String pk) {
		this.pk = pk;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " " + pk + " / " + getModifiedDate();
	}

	public abstract User getUser();
}
