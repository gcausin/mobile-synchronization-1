package msync.codegeneration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * @author Georg Causin
 * 
 * An entity instantiated by model.json
 * 
 * In a tree structure (see dependentEntities) the "owns/owned by" relation is configured.
 * This relation is used for the rule:
 * 		Users may read all "own" entities and entities owned by user 'Public'
 * 		Users may insert/update/delete all "own" entities
 *
 */
class Entity {
	// Java package name - required
	private String packageName;
	// C# namespace - required
	private String csharpNamespace;
	// table name equals entity name - required
	// Must be unique, reserved: User, Role, UserRole, Account, DeletedRecord,
	// LastEntitySyncTime, Synchronization
	private String tableName;
	// interface(s) for C# classes, full qualified (if more than one comma
	// separated) - optional
	private String csharpInterfaces;
	// read only those records from repository that are owned by the user (not
	// by public) - optional, default false
	// see generated repositories:
	// 		u.name in (?#{principal.username}, 'Public')
	// or
	//		u.name = ?#{principal.username}
	private boolean onlyPrivate;
	// Unique constraint (see UniqueConstraint) - required
	private UniqueConstraint uniqueConstraint;
	// Reference constraints (see ReferenceConstraint) - optional
	private List<ReferenceConstraint> referenceConstraints;
	// Properties (see Property) - useless if empty
	private List<Property> properties;
	// If true a OneToMany collection of this entity is created in the
	// referenced entity like:
	//
	// [JsonIgnore, OneToMany]
	// public List<Generated.Model.Foods.Recipe.Ingredient> Ingredients { get; set; }
	private boolean containedInParentAsOneToMany;
	// The entities in relation "owned by user of this entity"
	private List<Entity> dependentEntities;
	// For internal use only. Don't set the following properties, neither in Java code nor in model.json
	private Entity parent;
	private boolean exludedFromUserDependency;
	private boolean modifiedDateOmitted;
	// Enable configuring a name for reference constraint 
	private String referenceConstraintName;

	public Entity findEntity(String referencedEntity) {

		if (getTableName().equals(referencedEntity)) {
			return this;
		}

		if (getDependentEntities() == null) {
			return null;
		}

		for (Entity entity : getDependentEntities()) {
			Entity foundEntity = entity.findEntity(referencedEntity);

			if (foundEntity != null) {
				return foundEntity;
			}
		}

		return null;
	}

	public Stream<Entity> flattened() {
		return Stream.concat(Stream.of(this), getDependentEntities().stream().flatMap(Entity::flattened));
	}

	public String getUserChain() {
		String getUser;

		if (getParent() == null) {
			return "this";
		} else {
			getUser = "get" + getParent().getTableName() + "()";
		}

		Entity entity = getParent();

		while (entity.getParent() != null) {
			getUser += ".get" + entity.getParent().getTableName() + "()";
			entity = entity.getParent();
		}

		return getUser;
	}

	public String getSelectStatement() throws IOException {
		String joinConditions = "";

		if (getParent() != null) {
			Entity parent = getParent();
			Entity thisEntity = this;

			while (thisEntity.getParent() != null) {
				joinConditions += Utils.readTemplate("joinCondition:")
						.replace("~entity~", Utils.firstLetterToLowerCase(thisEntity.getTableNameExceptUser()))
						.replace("~parent~", Utils.firstLetterToLowerCase(parent.getTableName()))
						.replace("~parentAlias~", Utils.firstLetterToLowerCase(parent.getTableNameExceptUser()));
				thisEntity = parent;
				parent = thisEntity.getParent();
			}

		}

		String select = Utils.readTemplate("selectStatement:")
				.replace("~entity~", Utils.firstLetterToLowerCase(getTableNameExceptUser()))
				.replace("~Entity~", getTableName())
				.replace("~checkUserCondition~",
						isOnlyPrivate() ? Utils.readTemplate("checkUserConditionPrivate:")
								: Utils.readTemplate("checkUserConditionWithPublic:"))
				.replace("~joinConditions~", joinConditions);

		return select;
	}

	public String getPackageName() {
		return packageName;
	}

	public void setPackageName(String packageName) {
		this.packageName = packageName;
	}

	public String getCsharpNamespace() {
		return csharpNamespace;
	}

	public void setCsharpNamespace(String csharpNamespace) {
		this.csharpNamespace = csharpNamespace;
	}

	public String getTableName() {
		return tableName;
	}

	public String getTableNameExceptUser() {
		return "User".equals(tableName) ? "U" : tableName;
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	public String getCsharpInterfaces() {
		if (csharpInterfaces == null || "".equals(csharpInterfaces)) {
			return "";
		}
		return ", " + csharpInterfaces;
	}

	public void setCsharpInterfaces(String csharpInterfaces) {
		this.csharpInterfaces = csharpInterfaces;
	}

	public List<Entity> getDependentEntities() {
		if (dependentEntities == null) {
			return Collections.emptyList();
		}
		return dependentEntities;
	}

	public void setDependentEntities(List<Entity> dependentEntities) {
		this.dependentEntities = dependentEntities;
	}

	public UniqueConstraint getUniqueConstraint() {
		return uniqueConstraint;
	}

	public void setUniqueConstraint(UniqueConstraint uniqueConstraint) {
		this.uniqueConstraint = uniqueConstraint;
	}

	public List<ReferenceConstraint> getReferenceConstraints() {
		if (referenceConstraints == null) {
			return Collections.emptyList();
		}
		return referenceConstraints;
	}

	public void setReferenceConstraints(List<ReferenceConstraint> referenceConstraints) {
		this.referenceConstraints = referenceConstraints;
	}

	public List<Property> getProperties() {
		if (properties == null) {
			return Collections.emptyList();
		}
		return properties;
	}

	public void setProperties(List<Property> properties) {
		this.properties = properties;
	}

	public boolean isOnlyPrivate() {
		return onlyPrivate;
	}

	public void setOnlyPrivate(boolean onlyPrivate) {
		this.onlyPrivate = onlyPrivate;
	}

	public boolean isContainedInParentAsOneToMany() {
		return containedInParentAsOneToMany;
	}

	public void setContainedInParentAsOneToMany(boolean containedInParentAsOneToMany) {
		this.containedInParentAsOneToMany = containedInParentAsOneToMany;
	}

	public List<ReferenceConstraint> getAllReferences() {
		List<ReferenceConstraint> referencedEntities = new ArrayList<>();

		if (getParent() != null && !getReferenceConstraints().stream().filter(r -> r.getReferencedEntity().equals(getParent().getTableName())).findFirst().isPresent()) {
			String referenceConstraintName = getReferenceConstraintName();

			referencedEntities.add(new ReferenceConstraint() {{
				setReferencedEntity(getParent().getTableName());
				setReferenceConstraintName(referenceConstraintName);
			}});
		}

		referencedEntities.addAll(getReferenceConstraints());
		
		return referencedEntities;
	}

	public String findUniqueIndex(ReferenceConstraint referenceConstraint) throws IOException {
		return findUniqueIndex(referenceConstraint.getReferenceProperty() + "Fk");
	}

	public String findUniqueIndex(String propertyName) throws IOException {
		final String finalPropertyName = Utils.firstLetterToLowerCase(propertyName);

		try {
			int index = IntStream.range(0, getUniqueConstraint().getProperties().size())
					.filter(userInd -> getUniqueConstraint().getProperties().get(userInd).equals(finalPropertyName))
					.findFirst().getAsInt();

			return Utils.readTemplate("csharpUniqueIndexAttribute:").replace("~csharpEntity~", getTableName())
					.replace("~csharpIndexOrder~", "" + (index + 1));

		} catch (NoSuchElementException n) {
			return "";
		}
	}

	Entity getParent() {
		return parent;
	}
	
	// For internal use only. Don't set the following properties, neither in Java code nor in model.json
	void setParent(Entity parent) {
		this.parent = parent;
	}

	boolean isExludedFromUserDependency() {
		return exludedFromUserDependency;
	}

	void setExludedFromUserDependency(boolean exludedFromUserDependency) {
		this.exludedFromUserDependency = exludedFromUserDependency;
	}

	boolean isModifiedDateOmitted() {
		return modifiedDateOmitted;
	}

	void setModifiedDateOmitted(boolean modifiedDateOmitted) {
		this.modifiedDateOmitted = modifiedDateOmitted;
	}

	public String getReferenceConstraintName() {
		return referenceConstraintName;
	}

	public void setReferenceConstraintName(String referenceConstraintName) {
		this.referenceConstraintName = referenceConstraintName;
	}
}

/**
 * @author Georg Causin
 *
 */
class Property {
	// Property name
	private String name;
	// Type, ["text"|"citext"|"varchar(36)|"integer"|"numeric"|"time"|"timestamp"|"boolean"]
	private String type;
	// nullable, default false
	private boolean nullable;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public boolean isNullable() {
		return nullable;
	}

	public void setNullable(boolean nullable) {
		this.nullable = nullable;
	}
}

class ReferenceConstraint {
	// The referenced entity
	private String referencedEntity;
	// optional nullable, default false
	private boolean nullable;
	// If true a OneToMany collection of this entity is created in the
	// referenced entity like:
	//
	// [JsonIgnore, OneToMany]
	// public List<Generated.Model.Foods.Recipe.Ingredient> Ingredients { get; set; }
	private boolean containedInParentAsOneToMany;
	// optional name of foreign key property (if same entity is referenced more than once)
	private String name;
	private String referencePropertyName;
	// Enable configuring a name for reference constraint 
	private String referenceConstraintName;

	public String getReferenceProperty() {
		return Utils.coalesce(getName(), getReferencedEntity());
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getReferencedEntity() {
		return referencedEntity;
	}

	public void setReferencedEntity(String referencedEntity) {
		this.referencedEntity = referencedEntity;
	}

	public boolean isNullable() {
		return nullable;
	}

	public void setNullable(boolean nullable) {
		this.nullable = nullable;
	}

	public boolean isContainedInParentAsOneToMany() {
		return containedInParentAsOneToMany;
	}

	public void setContainedInParentAsOneToMany(boolean containedInParentAsOneToMany) {
		this.containedInParentAsOneToMany = containedInParentAsOneToMany;
	}

	public String getReferencePropertyName() {
		return referencePropertyName;
	}

	public void setReferencePropertyName(String referencePropertyName) {
		this.referencePropertyName = referencePropertyName;
	}

	public String getReferenceConstraintName() {
		return referenceConstraintName;
	}

	public void setReferenceConstraintName(String referenceConstraintName) {
		this.referenceConstraintName = referenceConstraintName;
	}
}

/**
 * @author Georg Causin
 *
 * The unique constraint of an entity (for now only one constraint)
 * Holds the property name in the order of the unique indexes
 * 
 */
class UniqueConstraint {
	// The properties for unique constraint
	// (If ReferenceConstraint.name is set the same name must be used here)
	List<String> properties;

	public List<String> getProperties() {
		return properties;
	}

	public void setProperties(List<String> properties) {
		this.properties = properties;
	}
}

/**
 * @author Georg Causin
 *
 * This entity is the root of the "owned by" tree
 * Will be completed with entities User and DeletedRecord by the generator (Don't care)
 * 
 */
class RootEntity extends Entity {
	// schemaName and modelVersion build the
	// 	database schema and war file name of the web hosted server
	private String schemaName;
	private String modelVersion;

	public String getSchemaName() {
		return schemaName;
	}

	public void setSchemaName(String schemaName) {
		this.schemaName = schemaName;
	}

	public String getSchema() {
		return getSchemaName() + "-" + getModelVersion();
	}

	public String getModelVersion() {
		return modelVersion;
	}

	public void setModelVersion(String modelVersion) {
		this.modelVersion = modelVersion;
	}
}

class Interface {
	private String interfaceName;
	private String extendedInterfaces;
	private List<Property> properties;
	
	public String getInterfaceName() {
		return interfaceName;
	}
	public void setInterfaceName(String interfaceName) {
		this.interfaceName = interfaceName;
	}
	public String getExtendedInterfaces() {
		if (extendedInterfaces == null) {
			return "";
		}
		
		return ": " + extendedInterfaces;
	}
	public void setExtendedInterfaces(String extendedInterfaces) {
		this.extendedInterfaces = extendedInterfaces;
	}
	public List<Property> getProperties() {
		return properties;
	}
	public void setProperties(List<Property> properties) {
		this.properties = properties;
	}
}