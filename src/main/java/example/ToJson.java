package example;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.ResourceIterable;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.UserFunction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.piterion.asplm.model.AbstractOccurence;
import com.piterion.asplm.model.DbObject;
import com.piterion.asplm.model.Occurence;
import com.piterion.asplm.model.Snapshot;
import com.piterion.asplm.model.StructureAttributes;


enum MyRelationshipTypes implements RelationshipType
{
	HAS_DETAILS,
	HAS_Details,
	HAS_Input,
	HAS_Object,
	HAS_Relation,
	HAS_Parent
}


/**
 * This is an example how you can create a simple user-defined function for Neo4j.
 */
public class ToJson {
	// ObjectMapper is threadsafe, but should check if the perfoemance is better when it is instanciated inside the function
	private final static ObjectMapper objectMapper = new ObjectMapper();
	
    @UserFunction
    @Description("Match(o:Occurence{id:x1}) where $endItem in o:endItem return example.toJson(o, $endItem) - merge o and the graph anderhlab corresponding to the endItem to JSON string.")
    public String toJson(
    		@Name("root") Node rootNode,
            @Name(value = "endItem", defaultValue = "Min_Eng") String endItem) throws Exception {
        
    	Objects.requireNonNull(rootNode);
    	Objects.requireNonNull(endItem);
    	
    	AbstractOccurence occurence = process(rootNode, endItem, null);
    	
    	String json =  objectMapper.writeValueAsString(occurence);
        return json;
    }

	private AbstractOccurence process(Node node, String endItem, AbstractOccurence parent) throws Exception {
		String occId = (String) node.getProperty(StructureAttributes.OCC_ID);

		Boolean isSnapshot = false;
		if (parent == null && node.hasProperty("isMeta")) {
			isSnapshot = (Boolean) node.getProperty("isMeta");
        }
		
		AbstractOccurence ret;
		if (isSnapshot) {
			 Map<String, String> detailsAttributes;
			final Map<String, String> inputParameters;
			try {
				detailsAttributes = extractOppositerNodeAttributes(node, Direction.OUTGOING, MyRelationshipTypes.HAS_Details, endItem);
				inputParameters = extractOppositerNodeAttributes(node, Direction.OUTGOING, MyRelationshipTypes.HAS_Input, endItem);
			}catch(Exception e) {
				throw new Exception(String.format("Missing related object to snapshot with occId = %s", occId), e);
			}
			ret = new Snapshot(occId, detailsAttributes, inputParameters);
			
		}else {
			final Map<String, String> objectAttributes;
			final Map<String, String> relationAttributes;
			try {
				objectAttributes = extractOppositerNodeAttributes(node, Direction.OUTGOING, MyRelationshipTypes.HAS_Object, endItem);
				relationAttributes = extractOppositerNodeAttributes(node, Direction.OUTGOING, MyRelationshipTypes.HAS_Relation, endItem);
			}catch(Exception e) {
				throw new Exception(String.format("Missing related object to occurence with occId = %s", occId), e);
			}
			ret = new Occurence(occId, new DbObject(objectAttributes), new DbObject(relationAttributes), parent);
		}
		
		
		// process children
		ResourceIterable<Relationship> relationshipsDetails = node.getRelationships(Direction.INCOMING, MyRelationshipTypes.HAS_Parent);
		try (ResourceIterator<Relationship> it = relationshipsDetails.iterator()) {
			while (it.hasNext()) {
				Relationship relation = it.next();
				if (relation.hasProperty("endItem")) {
					String[] endItems = (String[]) relation.getProperty("endItem");
					if (contains(endItems, endItem)) {
						Node childNode = relation.getOtherNode(node);
						Occurence childOcc = (Occurence)process(childNode, endItem, ret);
						ret.getChildren().add(childOcc);
					}
				}
			}
		}
		
		return ret;
	}
	
	private Map<String, String> extractOppositerNodeAttributes(Node node, Direction direction, MyRelationshipTypes relationSchip, String endItem) { //throws Exception
		
		ResourceIterable<Relationship> relationshipsDetails = node.getRelationships(direction, relationSchip);
		
		try (ResourceIterator<Relationship> it = relationshipsDetails.iterator() )
		 {
		     while ( it.hasNext() )
		     {
		    	 Relationship relation = it.next();
		         if (relation.hasProperty("endItem"))
		         {
		        	 String[] endItems = (String[]) relation.getProperty("endItem");
		        	 if (contains(endItems, endItem)) {
		        		Node othernode = relation.getOtherNode(node);
		        		Map<String, Object> attributes = othernode.getAllProperties();
		        		attributes.remove("occID"); //in details and Input it is set because they need a unique id
		        		attributes.remove("createdAt");
		        		attributes.remove("endItem");
		        		attributes.remove("isMeta");
		        		attributes.remove("updateddAt");
		        		attributes.remove("OBID", "no_OBID");
		        		
		        		Map<String, String> detailsAttributes = transform(attributes);
		        		return detailsAttributes;
		        	 }
		         }
		     }
		 }
		 
		return null;
		//throw new Exception(String.format("missing %s relationship with type: %s and endItem %s", direction.toString(), relationSchip.toString(), endItem));
	}
	
	private Map<String, String> transform(Map<String, Object> inputMap) {
		Map<String, String> result = inputMap.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));
		return result;
	}
	
	boolean contains(String[] strings, String searchString) {
	    for (String string : strings) {
	        if (string.equals(searchString))
	        return true;
	    }
	    
	    return false;
	}
}