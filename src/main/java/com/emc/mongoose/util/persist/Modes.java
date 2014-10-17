package com.emc.mongoose.util.persist;
//
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.Column;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;
import static javax.persistence.GenerationType.IDENTITY;
/**
 * Created by olga on 16.10.14.
 */
@Entity(name="Modes")
@Table(name = "modes", uniqueConstraints = {
		@UniqueConstraint(columnNames = "name")})
public final class Modes
implements java.io.Serializable{
	@Id
	@GeneratedValue(strategy = IDENTITY)
	@Column(name = "id")
	private BigInteger id;
	@Column(name = "name")
	private String name;
	@OneToMany(targetEntity=Runs.class, fetch = FetchType.LAZY, mappedBy = "mode")
	private Set<Runs> runsSet = new HashSet<Runs>();
	//
	public Modes(){
	}
	public Modes(String name){
		this.name = name;
	}
	//
	public final BigInteger getId() {
		return id;
	}
	public final void setId(BigInteger id) {
		this.id = id;
	}
	public final String getName() {
		return name;
	}
	public final void setName(String name) {
		this.name = name;
	}
	public Set<Runs> getRunsSet() {
		return runsSet;
	}
	public void setRunsSet(Set<Runs> runsSet) {
		this.runsSet = runsSet;
	}
}
