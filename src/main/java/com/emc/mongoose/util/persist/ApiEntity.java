package com.emc.mongoose.util.persist;
//
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;
//
import static javax.persistence.GenerationType.IDENTITY;
/**
 * Created by olga on 17.10.14.
 */
@Entity(name="API")
@Table(name = "api")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public final class ApiEntity
implements Serializable {
	@Id
	@GeneratedValue(strategy = IDENTITY)
	@Column(name = "id")
	private long id;
	@Column(name = "name", unique = true)
	private String name;

	public ApiEntity(){
	}
	public ApiEntity(final String name){
		this.name = name;
	}

	public final long getId() {
		return id;
	}
	public final void setId(final long id) {
		this.id = id;
	}
	public final String getName() {
		return name;
	}
	public final void setName(final String name) {
		this.name = name;
	}
}
