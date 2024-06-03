package example;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.Ignore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.neo4j.driver.AccessMode;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.piterion.asplm.graph.Neo4jActions;
import com.piterion.asplm.model.Snapshot;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ToJsonTest {

	private Driver driver;
    private Neo4j embeddedDatabaseServer;

    @BeforeAll
    void initializeNeo4j() {
        this.embeddedDatabaseServer = Neo4jBuilders.newInProcessBuilder()
                .withDisabledServer()
                .withFunction(ToJson.class)
                .build();

        this.driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI());
    }

    @AfterAll
    void closeDriver(){
        this.embeddedDatabaseServer.close();
    }

    @AfterEach
    void cleanDb(){
        try(Session session = driver.session()) {
            session.run("Match (n)-[r]->(m) delete r delete n delete m");
        }
    }
    
    @Ignore
    @Test
    void serializeDummySnapshotRoot() throws IOException {
        try(Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI());
            Session session = driver.session()) {
        	// Given
        	session.run(String.format("""
        		MERGE (occ:Occurence {occId: "%s"})
        		    ON CREATE
        		      SET
        		        occ.createdAt = datetime(),
        		        occ.endItem = "C205"
        		    ON MATCH
        		      SET
        		        occ.endItem =(CASE WHEN "C205" in occ.endItem THEN occ.endItem ELSE occ.endItem + ["C205"] END)
        		    SET
        		      occ.updateddAt = datetime()
        		""", "dXNyX3dndWIwMDAwMDAwOUI1MDI3RkE3")
        			);
        	
            // When
            String result = session.run(String.format( """
            		Match(o:Occurence{occId:"%s"}) where "C205" in o.endItem 
            		RETURN example.toJson(o, "C205") AS result"""
            		, "dXNyX3dndWIwMDAwMDAwOUI1MDI3RkE3")
            		).single().get("result").asString();

            // Then
    		String text = """
    				{"occId":"dXNyX3dndWIwMDAwMDAwOUI1MDI3RkE3","object":{"INPUT_PARAMETERS":{}}}
    				""";
    		
            assertThat( result).isEqualTo(text);
        }
    }

    
	@Test
	void Given_SavedPartSnapshot_When_ExportedUsingoccId_Than_Match() throws IOException {
		String inputfileName= "snapshot_extract_1level.json";
		String endItem = "C205";
		String query = String.format("""
				Match(o:Occurence{occId:"%s"}) where "C205" in o.endItem
				RETURN example.toJson(o, "C205") AS result""", "dXNyX3dndWIwMDAwMDAwOUI1MDI3RkE3");
		
		Given_SavedGraph_When_Exported_Than_Match(inputfileName, inputfileName, endItem, query);
	}
	
    
	@Test
	void Given_SavedPartSnapshot_When_ExportedUsingLDisplayedName_Than_Match() throws IOException {
		String inputfileName= "snapshot_extract_1level.json";
		String expectedOutputfileName = "extract_1level.json";
		String endItem = "C205";
		String query = String.format("""
				Match(o:Occurence)-[ro:HAS_Object]->(obj:Object) where obj.LDisplayedName="%s" AND "C205" in obj.endItem AND "C205" in ro.endItem AND "C205" in o.endItem
				RETURN example.toJson(o, "C205") AS result""", "C205, 0001.011, C-KLASSE BR 205");
		
		Given_SavedGraph_When_Exported_Than_Match(inputfileName, expectedOutputfileName, endItem, query);
	}
	
    @Ignore
	@Test
	void Given_SavedSnapshotAS_C205_When_Exported_Than_Match() throws IOException {
		String inputfileName= "AS_C205.json";
		String endItem = "AS_C205";
		String query = String.format("""
				Match(o:Occurence{occId:"%s"}) where "AS_C205" in o.endItem
				RETURN example.toJson(o, "AS_C205") AS result""", "dXNyX3dndWIwMDAwMDAwOUI1MDI3RkE3");
		
		Given_SavedGraph_When_Exported_Than_Match(inputfileName, inputfileName, endItem, query);
	}

	private void Given_SavedGraph_When_Exported_Than_Match(String inputfileName, String expectedOutputfileName, String endItem, String query)
			throws IOException, JsonProcessingException, JsonMappingException {
		 DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
		 DateTimeFormatter d = DateTimeFormatter.ofPattern("MMdd");
		 
		// Given
		Path resourceDirectory = Paths.get("src", "test", "resources");
		String absolutePath = resourceDirectory.toFile().getAbsolutePath();
		String inputText = Files.readString(Paths.get(absolutePath + "\\" + inputfileName), StandardCharsets.ISO_8859_1);

		LocalDateTime startedtParsingJson = LocalDateTime.now();  
		StringBuilder reporter = new StringBuilder();
		reporter.append("\n***********************************************\n");
		reporter.append(String.format("Test with\n  - Input file: %s\n  - endItem: %s\n  - Query: %s\n  -Test file: %s\n", inputfileName, endItem, query, expectedOutputfileName));
		reporter.append(String.format("\nParsing Json startet at: %s", dtf.format(startedtParsingJson)));
		ObjectMapper objectMapper = new ObjectMapper();
		Snapshot snapshot = objectMapper.readValue(inputText, Snapshot.class);
		LocalDateTime endParsingJson = LocalDateTime.now();
		reporter.append(String.format("\nParsing Json endet at: %s", dtf.format(endParsingJson)));
		
		// When
		String result;
		try (Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI());
				Session session = driver.session()) {

			Neo4jActions.saveAllSnapshot(snapshot, endItem, driver, SessionConfig.builder().withDefaultAccessMode(AccessMode.WRITE).build());

			LocalDateTime endSaveStructure = LocalDateTime.now();
			reporter.append(String.format("\nSaving structure in DB endet at: %s", dtf.format(endSaveStructure)));

			result = session.run(query).single().get("result").asString();

			LocalDateTime endQuery = LocalDateTime.now();
			reporter.append(String.format("\nGet query result from DB endet at: %s", dtf.format(endQuery)));
		}

		// Then
		String expectedText = Files.readString(Paths.get(absolutePath + "\\" + expectedOutputfileName));
		
		JsonNode treeResult = objectMapper.readTree(result);
		JsonNode treeExpected = objectMapper.readTree(expectedText);

		Files.writeString(
				Paths.get("src", "test", "resources", "reports", String.format("reprot_%s.txt", d.format(LocalDateTime.now()))),
						reporter.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND
						);
		
		assertThat(treeResult).isEqualTo(treeExpected);
		//assertThat(result.replaceAll("\\s", "")).isEqualTo((text.replaceAll("\\s", "")));
	}
}