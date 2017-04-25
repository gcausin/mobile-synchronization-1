package msync.codegeneration;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

//import org.junit.Assert;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MobileSynchronizationGenerator {

	enum ConnectionContext {
		server,
		generator
	}
	
    public static void main(String[] args) throws SQLException, IOException {
        new MobileSynchronizationGenerator().generate();
    }

    protected void generate() throws SQLException, IOException {

        setValues(new Properties());
        
        values.load(getClass().getClassLoader().getResourceAsStream("generator.properties"));
        values.load(getClass().getClassLoader().getResourceAsStream("your.gitignored.properties"));
        
        checkRequiredProperies();
        
        setConnection(values);
        
//        if (true) return;
        
        final RootEntity rootEntity = readAndPrepareRootEntity(values);
        
        Utils.deleteDir(new File(getProperty("csharpModelRootPath") + "/Generated"));
        Utils.deleteDir(new File(getProperty("javaModelRootPath") + "/generated"));

        generateJavaProperties(rootEntity);
        generateCsharpProperties(rootEntity);
        generateCsharpInterfaces();
        
        StringBuffer out = new StringBuffer();
        
        out.append(dropAndPrepareSchema(rootEntity));

        final Entity deletedRecord = fromJson("deletedRecordTable:");
        final Entity userDependencyTable = fromJson("userDependencyTable:");
        final Entity userDependencyQueryTable = fromJson("userDependencyQueryTable:");
        final Entity accountTable = fromJson("accountTable:");
        final Entity roleTable = fromJson("roleTable:");
        final Entity userRoleTable = fromJson("userRoleTable:");
        
        deletedRecord.setParent(rootEntity);
        
        generateJavaClasses(rootEntity, rootEntity);
        generateJavaClasses(rootEntity, deletedRecord);
        generateJavaDeserializeClasses(rootEntity, Stream.concat(Stream.of(deletedRecord), rootEntity.flattened()));
        
        Stream.concat(Stream.of(deletedRecord), rootEntity.flattened())
            .forEach(entity -> entity.setCsharpNamespace("Generated." + entity.getCsharpNamespace()));

        generateCsharpClasses(rootEntity, rootEntity);
        generateCsharpClasses(rootEntity, deletedRecord);
        generatePartialSynchronizationSerice(rootEntity, Stream.concat(Stream.of(deletedRecord), rootEntity.flattened()));
        new File(getProperty("csharpModelRootPath") + "/Models.csproj").setLastModified(System.currentTimeMillis());
        new File(getProperty("csharpModelRootPath") + "../../MSync/MSync/MSync.csproj").setLastModified(System.currentTimeMillis());

        out.append(generateCreateTableStatements(rootEntity, userDependencyTable));
        out.append(generateCreateTableStatements(rootEntity, userDependencyQueryTable));
        out.append(generateCreateTableStatements(rootEntity, rootEntity));
        out.append(generateCreateTableStatements(rootEntity, accountTable));
        out.append(generateCreateTableStatements(rootEntity, roleTable));
        out.append(generateCreateTableStatements(rootEntity, userRoleTable));
        out.append(generateCreateTableStatements(rootEntity, deletedRecord));
        
        out.append(createStoredProcedures(rootEntity));
        
        String copyFromSchema = getProperty("copyFromSchema");
        
        if (copyFromSchema != null) {
            out.append(copyFromSchema(rootEntity, copyFromSchema, rootEntity.flattened()));
            out.append(copyFromSchema(rootEntity, copyFromSchema, Stream.of(deletedRecord)));
            out.append(copyFromSchema(rootEntity, copyFromSchema, Stream.of(accountTable)));
            out.append(copyFromSchema(rootEntity, copyFromSchema, Stream.of(roleTable)));
            out.append(copyFromSchema(rootEntity, copyFromSchema, Stream.of(userRoleTable)));
        } else {
            try {
                out.append(
                        executeStatement(
                                Utils.readTextFileFromClasspath("initial-content.sql")
                                            .replace("~schema~", rootEntity.getSchema())));
            }
            catch (IOException ioException){
                // no file initial-content.sql exists on classpath
            }
        }
        
        String createTablePath = getProperty("createTablePath");
        
        if (createTablePath != null) {
            writeToFile(createTablePath, "SetupDatabase.sql", out.toString());
        }
    }

    private void generateCsharpInterfaces() throws IOException {
        final String interfaceDefinitions = Utils.readTextFileFromClasspath("base-interfaces.json");
        final Interface[] interfaces = interfaceDefinitions == null ?
        									new Interface[0] : 
        									new ObjectMapper().readValue(interfaceDefinitions, Interface[].class);
        String allInterfaces = "";
        									
		for (Interface baseInterface : interfaces) {
			String properties = "";
			
	        for (Property property : baseInterface.getProperties()) {
	            properties += readTemplate("csharpInterfaceProperty:")
	                    .replace("~csharpPropertyType~", toCsharpType(property.getType()))
	                    .replace("~csharpPropertyName~", Utils.firstLetterToUpperCase(property.getName()));
	        }

	        allInterfaces += 
		                readTemplate("csharpInterfaceTemplate:")
		                    .replace("~interface~", baseInterface.getInterfaceName())
		                    .replace("~extendedInterfaces~", baseInterface.getExtendedInterfaces())
		                    .replace("~csharpInterfaceProperties~", properties);
		}
		
        writeToFile(getProperty("csharpModelRootPath") + "/Generated/Model/Base", "ModelInterfaces.cs",
                readTemplate("csharpModelInterfaces:")
                	.replace("~additionalInterfaces~", allInterfaces));
	}
    
	private void generateCsharpProperties(RootEntity rootEntity) throws UnknownHostException, IOException {
    	String localServer = getProperty("localServer");
    	String webServer = getProperty("webServer");
    	
        writeToFile(getProperty("csharpModelRootPath") + "/Generated/MobileSynchronization", "GeneratedConstants.cs",
                readTemplate("csharpProperties:")
                    .replace("~serviceName~", coalesce(getProperty("serviceName"), rootEntity.getSchema()))
                    .replace("~modelVersion~", rootEntity.getModelVersion())
                    .replace("~localServer~", coalesce(localServer, InetAddress.getLocalHost().getHostAddress()))
                    .replace("~webServer~", webServer)
                    .replace("~server~", Utils.firstLetterToUpperCase(getProperty("buildFor") + "Server"))
                    .replace("~server.port~", coalesce(getProperty("server.port"), "8080"))
                    .replace("~download.pageSize~", coalesce(getProperty("download.pageSize"), "1000"))
                    .replace("~upload.pageSize~", coalesce(getProperty("upload.pageSize"), "1000"))
                    .replace("~log.debug~", coalesce(getProperty("log.debug"), "false"))
        			.replace("~log.json~", coalesce(getProperty("log.json"), "false")));
    }

    private void generateJavaProperties(RootEntity rootEntity) throws IOException {
        writeToFile(getProperty("javaModelRootPath").replace("java", "resources"), "generated.application.properties",
                readTemplate("javaProperties:")
                    .replace("~schemaName~", rootEntity.getSchemaName())
                    .replace("~modelVersion~", rootEntity.getModelVersion())
                    .replace("~serviceName~", coalesce(getProperty("serviceName"), rootEntity.getSchema()))
                    .replace("~url~", getDatabaseProperty(ConnectionContext.server, "url"))
                    .replace("~user~", getDatabaseProperty(ConnectionContext.server, "user"))
                    .replace("~password~", getDatabaseProperty(ConnectionContext.server, "password"))
                    .replace("~server.port~", coalesce(getProperty("server.port"), "8080"))
                    .replace("~log.debug~", coalesce(getProperty("log.debug"), "false")));
    }

	private RootEntity readAndPrepareRootEntity(final Properties values) throws JsonParseException, JsonMappingException, IOException {
        final RootEntity rootEntityBase =
                new ObjectMapper().readValue(readTemplate("rootEntityBase:"), RootEntity.class);
        final RootEntity rootEntity =
                new ObjectMapper().readValue(Utils.readTextFileFromClasspath("models.json"), RootEntity.class);

        rootEntity.setProperties(rootEntityBase.getProperties());
        rootEntity.setPackageName(rootEntityBase.getPackageName());
        rootEntity.setCsharpNamespace(rootEntityBase.getCsharpNamespace());
        rootEntity.setCsharpInterfaces(rootEntityBase.getCsharpInterfaces());
        rootEntity.setTableName(rootEntityBase.getTableName());
        rootEntity.setUniqueConstraint(rootEntityBase.getUniqueConstraint());
        
        if (getProperty("csharpModelRootPath") == null) {
        	values.setProperty("csharpModelRootPath", "Models/Models");
        }
        
        if (getProperty("javaModelRootPath") == null) {
        	values.setProperty("javaModelRootPath", "mobile-synchronization-server/src/main/java");
        }
        
        return rootEntity;
    }

    private StringBuffer createStoredProcedures(final RootEntity rootEntity) throws SQLException {
        final StringBuffer out = new StringBuffer();
        
        out.append(executeStatement(readDatabaseTemplate("createFunctionDependentUserFk:")
                .replace("~schema~", rootEntity.getSchema())));
        out.append(executeStatement(readDatabaseTemplate("createFunctionIsOwner:")
                .replace("~schema~", rootEntity.getSchema())));
        out.append(executeStatement(readDatabaseTemplate("createFunctionNotifyDeletedRecord:")
                .replace("~schema~", rootEntity.getSchema())));
        
        rootEntity.flattened().forEach(e -> {
            try {
                out.append(executeStatement(readDatabaseTemplate("createTriggerNotifyDeletedRecord:")
                        .replace("~entity~", e.getTableName())
                        .replace("~schema~", rootEntity.getSchema())));
            }
            catch (Exception e1) {
                throw new RuntimeException(e1);
            }
        });
        
        return out;
    }

    private void generateJavaDeserializeClasses(RootEntity rootEntity, Stream<Entity> clientRelevantEntities) throws IOException {
        String classSingleRequest = readTemplate("classSingleRequest:");
        String imports = "";
        String classSingleRequestGroups = "";
        String members = "";
        String importsOfRepositories = "";
        String foreignKeySetterOfClasses = "";
        String requestGroupGetters = "";
        int classCounter = 0;
        
        for (Entity entity : clientRelevantEntities.collect(Collectors.toList())) {
            
            String entityName = entity.getTableName();
            String foreignKeySetters = "";

            importsOfRepositories += readTemplate("import:")
                    .replace("~fullClassName~", entity.getPackageName() + "." + entity.getTableName() + "Repository");
            
            for (ReferenceConstraint referenceConstraint : entity.getAllReferences()) {
                
                foreignKeySetters += readTemplate("foreignKeySetter:")
                        .replace("~foreignKey~", Utils.firstLetterToLowerCase(referenceConstraint.getReferencedEntity()))
                        .replace("~ForeignKey~", referenceConstraint.getReferencedEntity())
                        .replace("~SimpleClassName~", entityName);
            }
            
            foreignKeySetterOfClasses += readTemplate("foreignKeySetterOfClass:")
                    .replace("~ifOrElseIf~", classCounter++ == 0 ? "if" : "else if")
                    .replace("~SimpleClassName~", entityName)
                    .replace("~foreignKeySetters~", foreignKeySetters);

            imports += readTemplate("import:").replace("~fullClassName~", entity.getPackageName() + "." + entity.getTableName());
            
            String EntityNames = Utils.pluralOf(entityName);
            String entityNames = Utils.firstLetterToLowerCase(EntityNames);
            
            classSingleRequestGroups += readTemplate("classSingleRequestGroup:")
                    .replace("~SimpleClassName~", entityName)
                    .replace("~simpleClassNames~", entityNames)
                    .replace("~SimpleClassNames~", EntityNames);
            
            members += readTemplate("member:")
                    .replace("~SimpleClassName~", entityName)
                    .replace("~simpleClassNames~", entityNames)
                    .replace("~SimpleClassNames~", EntityNames);
            
            requestGroupGetters  += readTemplate("requestGroupGetter:")
                    .replace("~SimpleClassNames~", EntityNames);
        }
        
        
        classSingleRequest = classSingleRequest
                                    .replace("~imports~", imports)
                                    .replace("~classDefinitions~", classSingleRequestGroups)
                                    .replace("~requestGroupGetters~", requestGroupGetters)
                                    .replace("~members~", members);
        
        writeToFile(getProperty("javaModelRootPath") + "/generated/deserialize", "SingleRequest.java", classSingleRequest);
        writeToFile(getProperty("javaModelRootPath") +"/generated/deserialize", "ForeignKeySetter.java",
                            readTemplate("classForeignKeySetter:")
                                    .replace("~foreignKeySetterOfClasses~", foreignKeySetterOfClasses)
                                    .replace("~imports~", imports)
                                    .replace("~importsOfRepositories~", importsOfRepositories));
    }

    private StringBuffer copyFromSchema(RootEntity rootEntity, String copyFromSchema, Stream<Entity> stream) throws SQLException {
        
        StringBuffer out = new StringBuffer();
        
        if (getConnection() == null) {
            return out;
        }
        
        Statement stmt = getConnection().createStatement();

        try {
            stream.forEach(e -> {
                try {
                    ResultSet rs = stmt.executeQuery(readDatabaseTemplate("copyInsertStatement:")
                            .replace("~entity~", e.getTableName())
                            .replace("~fromSchema~", copyFromSchema)
                            .replace("~schema~", rootEntity.getSchema()));
                    
                    if (rs.next()) {
                        out.append(executeStatement(rs.getString(1)));
                    }
                }
                catch (Exception e1) {
                    throw new RuntimeException(e1);
                }
            });
        }
        finally {
            if (stmt != null) {
                stmt.close();
            }
        }
        
        return out;
    }

    private void generatePartialSynchronizationSerice(RootEntity rootEntity, Stream<Entity> clientRelevantEntities) throws IOException {
        HashSet<String> usings = new HashSet<>();
        List<String> entries = new ArrayList<>();
        
        clientRelevantEntities.forEach(e -> {
            usings.add(readTemplate("csharpUsing:").replace("~csharpNamespace~", e.getCsharpNamespace()));
            entries.add(readTemplate("partialSynchronizationServiceEntry:").replace("~entity~", e.getTableName()));
        });
        
        writeToFile(getProperty("csharpModelRootPath") + "/../../MSync/MSync/Generated/Services", "SynchronizationService.cs",
                readTemplate("partialSynchronizationService:")
                    .replace("~csharpUsings~", usings.stream().reduce("", (a, b) -> a + b))
                    .replace("~partialSynchronizationServiceEntries~", entries.stream().reduce((a, b) -> a + b).get()));
    }

    private Entity fromJson(String section) throws JsonParseException, JsonMappingException, IOException {
        return new ObjectMapper().readValue(readTemplate(section), Entity.class);
    }

    private StringBuffer dropAndPrepareSchema(RootEntity rootEntity) throws SQLException {
        StringBuffer out = new StringBuffer();
        
        String[] prepareStatements = new String[] {
                "drop schema \"" + rootEntity.getSchema() + "\" cascade",
                "create schema \"" + rootEntity.getSchema() + "\"",
                "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"",
                "CREATE EXTENSION IF NOT EXISTS citext",
        };
        
        for (String prepareStatement : prepareStatements) {
            out.append(executeStatement(prepareStatement));
        }
        
        return out;
    }

    private void generateJavaClasses(RootEntity rootEntity, Entity entity) throws IOException {
        
        entity.setPackageName("generated." + entity.getPackageName());
        System.out.println("Generate Java Class " + entity.getPackageName() + "." + entity.getTableName());

        Entity user = rootEntity.findEntity("User");
        String entityImports = readTemplate("import:").replace("~fullClassName~", user.getPackageName() + "." + user.getTableName());
        String references = "";
        String properties = "";
        List<ReferenceConstraint> referencedEntities = entity.getAllReferences();
        
        for(ReferenceConstraint referenceConstraint : referencedEntities) {
            references += readTemplate("reference:")
                    .replace("~ReferencedEntity~", referenceConstraint.getReferencedEntity())
                    .replace("~referenceProperty~", Utils.firstLetterToLowerCase(referenceConstraint.getReferenceProperty()))
                    .replace("~ReferenceProperty~", Utils.firstLetterToUpperCase(referenceConstraint.getReferenceProperty()))
                    .replace("~referencedEntity~", Utils.firstLetterToLowerCase(referenceConstraint.getReferencedEntity()))
                    .replace("~nullable~", new Boolean(referenceConstraint.isNullable()).toString());
            
            String packageOfEntity = rootEntity.findEntity(referenceConstraint.getReferencedEntity()).getPackageName();
            
            if (!packageOfEntity.equals(entity.getPackageName()) &&
                !referenceConstraint.getReferencedEntity().equals("User")) {
                
                entityImports += readTemplate("import:")
                                    .replace("~fullClassName~", packageOfEntity + "." + referenceConstraint.getReferencedEntity());
            }
        }
        
        for (Property property : entity.getProperties()) {
            properties += readTemplate("property:")
                    .replace("~propertyType~", toJavaType(property.getType()))
                    .replace("~propertyName~", property.getName())
                    .replace("~PropertyName~", Utils.firstLetterToUpperCase(property.getName()))
                    .replace("~nullable~", new Boolean(property.isNullable()).toString());
        }
        
        writeToFile(getProperty("javaModelRootPath") + "/" + entity.getPackageName().replace(".",  "/"), entity.getTableName() + ".java",
                readTemplate("entityTemplate:")
                    .replace("~packageName~", entity.getPackageName())
                    .replace("~entityImports~", entityImports)
                    .replace("~references~", references)
                    .replace("~properties~", properties)
                    .replace("~EntityName~", entity.getTableName())
                    .replace("~schema~", rootEntity.getSchema())
                    .replace("~getUser~", getUser(entity, referencedEntities))
                        );
        
        writeToFile(getProperty("javaModelRootPath") + "/" + entity.getPackageName().replace(".",  "/"), entity.getTableName() + "Repository.java",
                readTemplate("repositoryTemplate:")
                    .replace("~packageName~", entity.getPackageName())
                    .replace("~EntityName~", entity.getTableName())
                    .replace("~selectStatement~", entity.getSelectStatement()));

        for (Entity child : entity.getDependentEntities()) {
            
            child.setParent(entity);
            generateJavaClasses(rootEntity, child);
        }
    }
    
    private String toJavaType(String type) {
        if (type.equals("text")) {
            return "String";
        } else if (type.equals("citext")) {
            return "String";
        } else if (type.equals("varchar(36)")) {
            return "String";
        } else if (type.equals("integer")) {
            return "int";
        } else if (type.equals("numeric")) {
            return "double";
        } else if (type.equals("time")) {
            return "java.time.LocalTime";
        } else if (type.equals("timestamp")) {
            return "java.time.LocalDateTime";
        } else if (type.equals("boolean")) {
            return "boolean";
        } else {
            return "no Java type found for " + type ;
        }
    }
    
    private void generateCsharpClasses(RootEntity rootEntity, Entity entity) throws IOException {
        System.out.println("Generate C# Class " + entity.getCsharpNamespace() + "." + entity.getTableName());
        
        HashSet<String> usings = new HashSet<>();
        String references = "";
        String properties = "";
        String csharpOneToManys = "";
        List<ReferenceConstraint> referencedEntities = entity.getAllReferences();
        
        for (ReferenceConstraint referenceConstraint : referencedEntities) {
            references += readTemplate("csharpReference:")
                    .replace("~csharpReferencedEntity~", referenceConstraint.getReferencedEntity())
                    .replace("~csharpReferenceProperty~", Utils.firstLetterToUpperCase(referenceConstraint.getReferenceProperty()))
                    .replace("~isNullable~", referenceConstraint.isNullable() ? "" : ", NotNull")
                    .replace("~csharpUniqueIndexAttribute~", entity.findUniqueIndex(referenceConstraint));
            
            String namespaceOfEntity = rootEntity.findEntity(referenceConstraint.getReferencedEntity()).getCsharpNamespace();
            
            if (!namespaceOfEntity.equals(entity.getCsharpNamespace())) {
                usings.add(readTemplate("csharpUsing:").replace("~csharpNamespace~", namespaceOfEntity));
            }
        }
        
        for (Property property : entity.getProperties()) {
            properties += readTemplate("csharpProperty:")
                    .replace("~csharpPropertyType~", toCsharpType(property.getType()))
                    .replace("~csharpPropertyName~", Utils.firstLetterToUpperCase(property.getName()))
                    .replace("~isNullable~", property.isNullable() ? "" : ", NotNull")
                    .replace("~csharpUniqueIndexAttribute~", entity.findUniqueIndex(property.getName()));
        }
        
        for (Entity referencingEntity : entity.getDependentEntities()) {
            if (!referencingEntity.isContainedInParentAsOneToMany()) {
                continue;
            }
            
            csharpOneToManys += readTemplate("csharpOneToMany:")
                    .replace("~referencingEntity~", referencingEntity.getCsharpNamespace() + "." + referencingEntity.getTableName())
                    .replace("~referencingEntities~", Utils.pluralOf(referencingEntity.getTableName()));
        }
        
        for (Entity referencingEntity : rootEntity
                                            .flattened()
                                            .collect(Collectors.toList())) {
            
            for (ReferenceConstraint referenceConstraint : referencingEntity.getReferenceConstraints()) {
                if (!referenceConstraint.isContainedInParentAsOneToMany()) {
                    continue;
                }
                
                Entity referencedEntity = rootEntity.findEntity(referenceConstraint.getReferencedEntity()); 
                
                if (!referencedEntity.getTableName().equals(entity.getTableName())) {
                    continue;
                }

                csharpOneToManys += readTemplate("csharpOneToMany:")
                        .replace("~referencingEntity~", referencingEntity.getCsharpNamespace() + "." + referencingEntity.getTableName())
                        .replace("~referencingEntities~", Utils.pluralOf(referencingEntity.getTableName()));
            }
            
        }
        
        writeToFile(getProperty("csharpModelRootPath") + "/" + entity.getCsharpNamespace().replace(".",  "/"),
                    entity.getTableName() + ".cs",
		                readTemplate("csharpEntityTemplate:")
		                    .replace("~csharpNamespace~", entity.getCsharpNamespace())
		                    .replace("~csharpUsings~", usings.stream().reduce("", (a, b) -> a + b))
		                    .replace("~csharpReferences~", references)
		                    .replace("~csharpProperties~", properties)
		                    .replace("~csharpEntity~", entity.getTableName())
		                    .replace("~csharpOneToManys~", csharpOneToManys)
        					.replace("~csharpAdditionalInterfaces~", entity.getCsharpInterfaces()));

        for (Entity child : entity.getDependentEntities()) {
            generateCsharpClasses(rootEntity, child);
        }
    }

    private String toCsharpType(String type) {
        if (type.equals("text")) {
            return "string";
        } else if (type.equals("citext")) {
            return "string";
        } else if (type.equals("varchar(36)")) {
            return "string";
        } else if (type.equals("integer")) {
            return "int";
        } else if (type.equals("numeric")) {
            return "double";
        } else if (type.equals("time")) {
            return "TimeSpan";
        } else if (type.equals("timestamp")) {
            return "DateTime";
        } else if (type.equals("boolean")) {
            return "bool";
        } else {
            return "no C# type found for " + type ;
        }
    }
    
    private StringBuffer generateCreateTableStatements(RootEntity rootEntity, Entity entity) throws IOException, SQLException {
        String references = "";
        String properties = "";
        List<ReferenceConstraint> referencedEntities = entity.getAllReferences();

        System.out.println("Generate " + (getConnection() == null ? "" : "and execute ") + "SQL \"" + rootEntity.getSchema() + "\".\"" + entity.getTableName() + "\"");

        for(ReferenceConstraint referenceConstraint : referencedEntities) {
            references += readDatabaseTemplate("referencingAttribute:")
                    .replace("~referencedEntity~", referenceConstraint.getReferencedEntity())
                    .replace("~referenceProperty~", Utils.firstLetterToLowerCase(referenceConstraint.getReferenceProperty()))
                    .replace("~ReferenceProperty~", Utils.firstLetterToUpperCase(referenceConstraint.getReferenceProperty()))
                    .replace("~attributeType~", Utils.firstLetterToLowerCase(referenceConstraint.getReferencedEntity()))
                    .replace("~schema~", rootEntity.getSchema())
                    .replace("~isNullable~", referenceConstraint.isNullable() ? "" : System.lineSeparator() + "        not null")
                    .replace("~entity~", entity.getTableName());
        }
        
        for (Property property : entity.getProperties()) {
            properties += readDatabaseTemplate("tableAttribute:")
                    .replace("~attributeType~", property.getType())
                    .replace("~isNullable~", property.isNullable() ? "" : System.lineSeparator() + "        not null")
                    .replace("~attributeName~", property.getName());
        }
        
        String createTable = readDatabaseTemplate("createTableTemplate:")
                .replace("~schema~", rootEntity.getSchema())
                .replace("~entity~", entity.getTableName())
                .replace("~modifiedDate~", entity.isModifiedDateOmitted() ? "" :
                                                readDatabaseTemplate("modifiedDate:"))
                .replace("~referencingAttributes~", references)
                .replace("~tableAttributes~", properties)
                .replace("~uniqueTableConstraint~",
                            readDatabaseTemplate("uniqueTableConstraint:")
                                    .replace("~entity~", entity.getTableName())
                                    .replace("~uniqueTableConstraint~",
                                                entity
                                                    .getUniqueConstraint()
                                                    .getProperties()
                                                    .stream()
                                                    .reduce((a, b) -> a + ", " + b)
                                                    .get())); 
        
        StringBuffer out = new StringBuffer();
        
        out.append(executeStatement(createTable));

        if (!entity.isExludedFromUserDependency()) {
            out.append(executeStatement((entity.getParent() == null ?
                    readDatabaseTemplate("userDependencyInsertStatementNoParent:") :
                    readDatabaseTemplate("userDependencyInsertStatementWithParent:")
                                    .replace("~parentEntity~", entity.getParent().getTableName()))
                            .replace("~schema~", rootEntity.getSchema())
                            .replace("~entity~", entity.getTableName())));

            out.append(executeStatement(readDatabaseTemplate("createQueryOfUserDependency:")
                            .replace("~schema~", rootEntity.getSchema())
                            .replace("~entity~", entity.getTableName())));
        }

        for (Entity child : entity.getDependentEntities()) {
            out.append(generateCreateTableStatements(rootEntity, child));
        }
        
        return out;
    }

    private StringBuffer executeStatement(String statement) throws SQLException {
        Statement stmt = getConnection() == null ? null : getConnection().createStatement();
        
        if (stmt != null) {
            try {
                stmt.execute(statement);
            }
            catch(Exception e) {
                System.err.println(e.getMessage());
                System.err.println("executeStatement(\"" + statement + "\") failed.");
            }
            finally {
                if (stmt != null) {
                    stmt.close();
                }
            }
        }

        StringBuffer out = new StringBuffer(statement);
        
        out.append(';');
        
        for (int i = 0; i < 2; i++) {
            out.append(System.getProperty("line.separator"));
        }

        return out;
    }

    private String getUser(Entity entity, List<ReferenceConstraint> referencedEntities) throws IOException {
        if (referencedEntities.stream().filter(r -> r.getReferencedEntity().equals("User")).findFirst().isPresent()) {
            return "";
        }
        
        return readTemplate("getUser:").replace("~getUser~", entity.getUserChain());
    }

    private String getProperty(String key) {
        return coalesce(System.getProperty(key), getValues().getProperty(key));
    }
    
    private String getDatabaseProperty(ConnectionContext context, String key) {
    	String property = null;
    	
    	if (context == ConnectionContext.generator) {
    		property = getProperty(getProperty("buildFor") + "GeneratorDatabase" + Utils.firstLetterToUpperCase(key));
    	}
    	
		return coalesce(
				property,
				getProperty(getProperty("buildFor") + "Database" + Utils.firstLetterToUpperCase(key)),
				getProperty(key));
    }
    
    public <T> T coalesce(@SuppressWarnings("unchecked") T...allArgs) {
        return Utils.coalesce(allArgs);
    }

    private String checkRequiredProperies() {
    	String[] required = new String[] {
        		"buildFor",	
        		"database",	
    	};
    	
        for (int i = 0; i < required.length; i++) {
            if (getProperty(required[i]) == null) {
        		throw new IllegalStateException("missing property \"" + required[i] +"\"");
            }
        }
        
        if (Integer.parseInt(coalesce(getProperty("download.pageSize"), "1000")) > 1000){
        	throw new IllegalArgumentException("Parameter for download.pageSize must not exceed 1000.");
        }
        
        return null;
    }

    private void writeToFile(String pathName, String fileName, String content) throws IOException {
        new File(pathName).mkdirs();
        
        BufferedWriter writer = new BufferedWriter(new FileWriter(pathName + "/" + fileName));
        
        writer.write(content);
        writer.close();
    }

    private String readTemplate(String key) {
        return Utils.readTemplate(key);
    }
    
    private String readDatabaseTemplate(String key) {
        return Utils.readDatabaseTemplate(getProperty("database"), key);
    }

    private void setConnection(Properties values) throws SQLException {
        String createSchema = getProperty("createSchema");
        
        if (createSchema == null || !Boolean.parseBoolean(createSchema)) {
            return;
        }
        
        String url = getDatabaseProperty(ConnectionContext.generator, "url");
        String user = getDatabaseProperty(ConnectionContext.generator, "user");
        String password = getDatabaseProperty(ConnectionContext.generator, "password");
        
        if (url != null && user != null && password != null) {
            setConnection(DriverManager.getConnection(url, user, password));
        }
    }

    private Connection connection;
    private Properties values;
    
    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public Properties getValues() {
        return values;
    }

    public void setValues(Properties values) {
        this.values = values;
    }
}
