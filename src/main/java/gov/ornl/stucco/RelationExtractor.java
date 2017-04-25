package gov.ornl.stucco;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import gov.ornl.stucco.entity.CyberEntityAnnotator.CyberAnnotation;
import gov.ornl.stucco.entity.EntityLabeler;
import gov.ornl.stucco.graph.Edge;
import gov.ornl.stucco.graph.Vertex;
import gov.ornl.stucco.model.CyberRelation;
import gov.ornl.stucco.relationprediction.FindAndOrderAllInstances;
import gov.ornl.stucco.relationprediction.PredictUsingExistingSVMModel;
import gov.ornl.stucco.relationprediction.PrintPreprocessedDocuments;
import gov.ornl.stucco.relationprediction.WriteRelationInstanceFiles;

/**
 * Main class responsible for creating the vertices from labeled entities,
 * and edges from interpreted relationships between the entities.
 *
 */
public class RelationExtractor {
		
	public static final String SOURCE_PROPERTY = "source";
	public static final String PREPROCESS_TYPE = "entityreplaced";
	public static final String FEATURE_CODES = "a,b,c,d";
	
	private List<CyberRelation> relationships;
	

	public RelationExtractor() {
		this.relationships = new ArrayList<CyberRelation>();
	}
	
	private void findRelations(Annotation doc, String source, String title) {
		try {
			PrintPreprocessedDocuments.preprocessDocs(doc, source, title);
			FindAndOrderAllInstances.orderAllInstances(PREPROCESS_TYPE);
			for (String code : FEATURE_CODES.split(",")) {
				WriteRelationInstanceFiles relationFileWriter = new WriteRelationInstanceFiles(PREPROCESS_TYPE, code);
				relationFileWriter.writeRelationInstances(doc);
			}
			String featureCodeList = FEATURE_CODES.replaceAll(",", "");
			relationships.addAll(PredictUsingExistingSVMModel.predictRelationship(PREPROCESS_TYPE, featureCodeList));
			//TODO Clean up preprocessed text, relation instances, etc after each doc is done
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * @param doc annotated version of the unstructured text
	 * @param source name of the data source
	 * @return graph in GraphSON format
	 */
	public String createSubgraph(Annotation doc, String source, String title) {
		this.findRelations(doc, source, title);
		
		List<Vertex> vertices = new ArrayList<Vertex>();
		List<Edge> edges = new ArrayList<Edge>();
		StringBuilder graphBuilder = new StringBuilder();
		for (CyberRelation relation : relationships) {
			List<Vertex> relationVertices = relation.getRelationVertices(source);
			if ((relationVertices.size() == 2) && (relation.isEdge())) {
				List<Edge> relationEdges = relation.getRelationEdges(relationVertices.get(0), relationVertices.get(1), source);
				edges.addAll(relationEdges);
			}
			vertices.addAll(relationVertices);
		}
		
		graphBuilder.append("{\"vertices\": {");
		for (int i=0; i<vertices.size(); i++) {
			Vertex vertex1 = vertices.get(i);
			graphBuilder.append(vertex1.toJSON());
			if (i < vertices.size() - 1) {
				graphBuilder.append(", ");
			}
			
		}
		
		graphBuilder.append("},");
		graphBuilder.append("\"edges\": [");
		
		for (int k=0; k<edges.size(); k++) {
			Edge e = edges.get(k);
			graphBuilder.append(e.toGraphSON());
			if (k < edges.size() - 1) {
				graphBuilder.append(", ");
			}
		}

		graphBuilder.append("] }");
		
		return graphBuilder.toString();
	}
	
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		//TODO Need to test product-version relationship
		String testSentence = "Microsoft Windows 2000 allows attackers to execute arbitrary code in the Telephony Application Programming Interface (refer to CVE-2005-0058)."; // Microsoft Windows XP SP1 Home edition has buffer overflow vulnerability in file.php (refer to CVE-2005-0057).";
		EntityLabeler labeler = new EntityLabeler();
		Annotation doc = labeler.getAnnotatedDoc("My Doc", testSentence);
							
		List<CoreMap> sentences = doc.get(SentencesAnnotation.class);
		for ( CoreMap sentence : sentences) {
			// Label cyber entities appropriately
			for ( CoreLabel token : sentence.get(TokensAnnotation.class)) {
				System.out.println(token.get(TextAnnotation.class) + "\t\t" + token.get(CyberAnnotation.class));
			}
			System.out.println();
		}

		RelationExtractor rx = new RelationExtractor();
		System.out.println(rx.createSubgraph(doc, "CNN", "My Doc"));
		
	}

}
