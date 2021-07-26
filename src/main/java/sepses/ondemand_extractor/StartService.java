package sepses.ondemand_extractor;

import sepses.parser.GrokHelper;

import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.io.IOUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import sepses.parser.Util;


import java.net.*;
import java.io.*;


/**
 * @author KKurniawan
 * @version 2.0
 * 
 * 
 */
public class StartService
{
	
	private static final Logger log = LoggerFactory.getLogger(StartService.class);
	private ServerSocket serverSocket;
	private Socket clientSocket;
	private PrintWriter out;

	public StartService(String sparql) throws Exception {
		
		String JsonConfig = new String(Files.readAllBytes(Paths.get("config.json"))); 
		JSONParser jcparser = new JSONParser(); 
		JSONObject jcobject = (JSONObject) jcparser.parse(JsonConfig);
		long sourceport = JsonPath.read(jcobject, "$.source-port");
		long targetport = JsonPath.read(jcobject, "$.target-port");
		String targetip = JsonPath.read(jcobject, "$.target-ip");
		String flinkloc = JsonPath.read(jcobject, "$.flink-loc");
		String rmlstreamerloc = JsonPath.read(jcobject, "$.RMLStreamer-loc");
		String sparqljsloc = JsonPath.read(jcobject, "$.SPARQLJS-loc");
			
		
		  try {  
	            
	     //logsources
			
		        List<String> logSources = JsonPath.read(jcobject, "$.logSources");
		        List<String> logtitle = JsonPath.read(jcobject,"$.logSources[*].title");
		        List<String> llogLocation = JsonPath.read(jcobject,"$.logSources[*].logLocation");
				List<String> llogMeta = JsonPath.read(jcobject,"$.logSources[*].logMeta");
		        List<String> rmlMapper = JsonPath.read(jcobject,"$.logSources[*].rmlMapper");
		        List<String> lgrokFile = JsonPath.read(jcobject,"$.logSources[*].grokFile");
		        List<String> lgrokPattern = JsonPath.read(jcobject,"$.logSources[*].grokPattern");
		        List<String> lregexPattern = JsonPath.read(jcobject,"$.logSources[*].regexPattern");
		        List<String> lvocabulary = JsonPath.read(jcobject,"$.logSources[*].vocabulary");
		   	 
		        String pq = parseSPARQL(sparqljsloc, sparql);
		        ArrayList<String> prefixes = QueryTranslator2.parsePrefixes(pq);
				
		   		for(int i=0;i<logSources.size();i++) {
		   		
		   			if(prefixes.contains(lvocabulary.get(i).toString())){
						
		   				System.out.println("processing log: "+logtitle.get(i));
						//submit job on flink
						String jobcommand= flinkloc+" run "+rmlstreamerloc+" toTCPSocket -s "+targetip+":"+targetport+" -m "+rmlMapper.get(i);
						//   System.out.print(jobcommand);
						Runtime rt = Runtime.getRuntime();
						rt.exec(jobcommand);

						//run Tcp Server first
						serverSocket = new ServerSocket((int) sourceport);
						System.out.println("Listening at port: " + sourceport);
						clientSocket = serverSocket.accept();
						System.out.println("New client connected");
						out = new PrintWriter(clientSocket.getOutputStream(), true);
	   		   
	   			  //start parsing	
	   			    log.info("parsing start");
	   				parse(llogLocation.get(i), llogMeta.get(i),lgrokFile.get(i), lgrokPattern.get(i),
	   								pq, lregexPattern.get(i), lvocabulary.get(i),out);	
	   			}
	   		}
	        
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
			
		}
		
		public void parse(String logfolder, String logmeta, String grokfile, String grokpattern, String parsedQuery, 
				String regexPattern, String vocab, PrintWriter out) throws Exception {
		   
	    	String generalGrokPattern = QueryTranslator2.parseGeneralRegexPattern(regexPattern,vocab);
	    	
			ArrayList<String> regexPatterns = new ArrayList<String>();
	    	if(generalGrokPattern==null) {
				  regexPatterns= QueryTranslator2.parseRegexPattern(parsedQuery,regexPattern);  
			  }

			  ArrayList<String> filterRegex = QueryTranslator2.parseFilter(parsedQuery);
	    	
	    		 JsonNode jsondata=null;
				 JsonNode jsondataTemp=null;
				 File folder = new File(logfolder);
				 
				
				ArrayList<String> files = Util.listFilesForFolder(folder);
				if(files.size()!=0) {
				
			     for (String file : files) {
			    	 	System.out.println("processing file: "+logfolder+file);
			    	 	String logfile = logfolder+file;
			    
			    	 	BufferedReader in = new BufferedReader(new FileReader(logfile));
		    	        while (in.ready()) {
			    	        String line = in.readLine();	
			    			if(generalGrokPattern!=null) {
			    				jsondataTemp = GrokHelper.parseGeneralGrok(grokfile,generalGrokPattern,line);
							}else {
								jsondataTemp = GrokHelper.parseGrok(grokfile, regexPatterns, line);    				 
							}
		    				if(filterRegex.size()!=0) {
								boolean c = checkAllFilter(filterRegex, jsondataTemp);
								 if(c) {
									 jsondata=jsondataTemp;
		 						 }
		    				 }else {
								 jsondata=jsondataTemp;
		    				 }
		    				
		    				if(jsondata!=null){
		    					JsonNode jsondataUUID = addUUID(jsondata);
		    					out.println(jsondataUUID);
		    				}
		    				
		    				
		    			  } 
	    
					
			      }
			     }    
	    
	    	
		}
	    	
	   	 JsonNode addUUID(JsonNode json) throws org.json.simple.parser.ParseException{
	 		UUID ui = UUID.randomUUID();
	 		((ObjectNode) json).put("id", ui.toString());
	 		return json;
	 			
	 		
	 	}
	 	private boolean checkAllFilter(List<String> filterRegex, JsonNode jsondataTemp ) throws org.json.simple.parser.ParseException{
			ArrayList<Boolean> resFilter = new ArrayList<Boolean>();
			
			 for (int k=0;k<filterRegex.size();k++){
				 String[] fr = filterRegex.get(k).split("=");
							 	  Boolean  cf = checkFilterJsonWithVariableRegex(jsondataTemp,fr[0],fr[1]);
								resFilter.add(cf);
								
							}
			for (int k=0;k<resFilter.size();k++){
								
							}
			if(resFilter.contains(false)){
				return false;
			}else{
				return true;
			}
		}
		public boolean checkFilterJsonWithVariableRegex(JsonNode json, String variable, String regex) throws org.json.simple.parser.ParseException {
			if(json.get(variable)==null){
				return true;
			}else{
				if(checkRegexExist(json.get(variable).toString(),regex)){
					return true;
				}else {
					return false;
				}
			}
		}
	    private boolean checkRegexExist(String Line,String regex) {
			String uri = parseRegex(Line,regex);
			if(uri!=null) {
				return true;
			}else {
				return false;
			}
		}
		public static String parseRegex(String logline,String regex) {

	    	Pattern pattern = Pattern.compile(regex);
	    	Matcher matcher = pattern.matcher(logline);
	    	String dt = null;
	    	if (matcher.find())
	    	{
	    	    dt= matcher.group(0);
	    	}
			return dt;  
		
	    
	}
		public static void main( String[] args ) throws Exception
		  {    
				String parsedQueryFile = "experiment/example_query/query-apache.sparql";
				String parsedQuery = new String(Files.readAllBytes(Paths.get(parsedQueryFile))); 
				 
			    new StartService(parsedQuery);
				
		 	}
		
		public static String parseSPARQL(String sparqljsloc, String sparql) throws IOException {
		//create SPARQL file
				String sparqlfile="query.sparql";
				
				try {
					FileOutputStream outputStream = new FileOutputStream(sparqlfile);
				    byte[] strToBytes = sparql.getBytes();
				    outputStream.write(strToBytes);

				    outputStream.close();
				 } catch (Exception e) {
			            e.printStackTrace();
			        }
				    
				
				String jobcommand= sparqljsloc+" "+sparqlfile;
				Runtime rt = Runtime.getRuntime();
				Process proc = rt.exec(jobcommand);
				String pq = IOUtils.toString(proc.getInputStream(), "utf8");
				return pq;
		}
}