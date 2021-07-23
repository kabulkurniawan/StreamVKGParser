package sepses.parser;
import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;

public class Util {
	 
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
		
		public static void emptyFile(String file) throws Exception {
			PrintWriter writer = new PrintWriter(file);
			writer.flush();
			writer.close();
		}
		
	    public static ArrayList<String> listFilesForFolder(final File folder) {
	    	ArrayList<String> rulefiles = new ArrayList<String>();
	    	
	        for (final File fileEntry : folder.listFiles()) {
	            if (fileEntry.isDirectory()) {
	                listFilesForFolder(fileEntry);
	            } else {
	            	rulefiles.add(fileEntry.getName());
	                // System.out.println(fileEntry.getName());
	            }
	        }
	        
	        return rulefiles;
	    }
	    public static void deleteFileInDirectory(String directory) throws Exception {
			File f = new File(directory);
			FileUtils.cleanDirectory(f); 
		}
	    public static void deleteFile(String filename) { 
	        File f = new File(filename); 
	        f.delete();
	      }
	    
	    public static long datetimeToLong(String time, String format) throws ParseException {
	    	
		    SimpleDateFormat df = new SimpleDateFormat(format);
		    Date date = df.parse(time);
		    long epoch = date.getTime();
		    return epoch;
	    }
}
