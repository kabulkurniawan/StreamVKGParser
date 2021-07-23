package sepses.ondemand_extractor;

import sepses.parser.GrokHelper;
import java.io.BufferedReader;
import java.util.UUID;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	public StartService(String pq) throws Exception {
		
		String JsonConfig = new String(Files.readAllBytes(Paths.get("config.json"))); 
		JSONParser jcparser = new JSONParser(); 
		JSONObject jcobject = (JSONObject) jcparser.parse(JsonConfig);
		long port = JsonPath.read(jcobject, "$.port");
		
		//run Tcp Server first
		  try {
			    serverSocket = new ServerSocket((int) port);
	            System.out.println("Listening at port: " + port);
	            clientSocket = serverSocket.accept();
	            System.out.println("New client connected");
		        out = new PrintWriter(clientSocket.getOutputStream(), true);
		        
	            
	     //logsources
			
	        List<String> logSources = JsonPath.read(jcobject, "$.logSources");
	        List<String> llogLocation = JsonPath.read(jcobject,"$.logSources[*].logLocation");
	        List<String> llogMeta = JsonPath.read(jcobject,"$.logSources[*].logMeta");
	        List<String> lgrokFile = JsonPath.read(jcobject,"$.logSources[*].grokFile");
	        List<String> lgrokPattern = JsonPath.read(jcobject,"$.logSources[*].grokPattern");
	        List<String> lregexPattern = JsonPath.read(jcobject,"$.logSources[*].regexPattern");
	        List<String> lvocabulary = JsonPath.read(jcobject,"$.logSources[*].vocabulary");
	   	 
	        ArrayList<String> prefixes = QueryTranslator2.parsePrefixes(pq);
			
	   		for(int i=0;i<logSources.size();i++) {
	   				
	   		
	   			if(prefixes.contains(lvocabulary.get(i).toString())){

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
		    				
		    				out.println(jsondata);
		    				
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
				String parsedQueryFile = "experiment/example_query/query-audit.json";
				String parsedQuery = new String(Files.readAllBytes(Paths.get(parsedQueryFile))); 
				 
			    new StartService(parsedQuery);
				
		 	}
	
}