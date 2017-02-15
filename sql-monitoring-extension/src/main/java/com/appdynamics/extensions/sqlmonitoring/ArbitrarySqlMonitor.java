package com.appdynamics.extensions.sqlmonitoring;

import com.appdynamics.TaskInputArgs;
import com.appdynamics.extensions.PathResolver;
import com.appdynamics.extensions.crypto.CryptoUtil;
import com.appdynamics.extensions.yml.YmlReader;

import com.google.common.base.Strings;
import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
import com.singularity.ee.agent.systemagent.api.MetricWriter;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.TaskOutput;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;

//import org.apache.commons.logging.Log;
//import org.apache.commons.logging.LogFactory;


import org.apache.log4j.Logger;


import org.joda.time.DateTime;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Maps;

public class ArbitrarySqlMonitor extends AManagedMonitor
{
    public String metricPrefix;
    public static final String CONFIG_ARG = "config-file";
    public static final String LOG_PREFIX = "log-prefix";
    private static String logPrefix;
//    private static final Log logger = LogFactory.getLog(ArbitrarySqlMonitor.class);
    private static final Logger logger = Logger.getLogger(ArbitrarySqlMonitor.class);

    private String metricPath;  
    private String dateStampFromFile = null; 
    private String relativePath = null;
    private String timeper_in_sec = null;
    private String execution_freq_in_secs = null;
    float diffInMillis = -1.0F;
    boolean hasDateStamp = false;
    boolean metricOverlap = false;
    BufferedReader br = null;
    Float DiffInSec = null;
    
//    //caching 
//    Cache<String, BigInteger> previousMetricsMap;
//  //  Cache<String, String> metricMap;
//	private Cache<String, Integer> metricMap;
	
	private Cache<String, ArrayList> metricMap;

	public ArbitrarySqlMonitor() {
		 metricMap = CacheBuilder.newBuilder()
	                .expireAfterWrite(1, TimeUnit.MINUTES).build();
		
    }

    private String cleanFieldName(String name)
    {
    	/**
         * Unicode characters sometimes look weird in the UI, so we replace all Unicode hyphens with
         * regular hyphens. The \d{Pd} character class matches all hyphen characters.
         * @see <a href="URL#http://www.regular-expressions.info/unicode.html">this reference</a>
         */
       
       name =name.replace(':','|');
       
       
       return name;
    	//return name.replaceAll("\\p{Pd}", "-").replaceAll("_", " ");
       
    }
    
    public TaskOutput execute(Map<String, String> taskArguments, TaskExecutionContext taskContext)  
    {	

    	
    	Long timeper_in_secConv = null;
    
    	 String status = "Success";
    	
    	//relativePath for reading/writing time stamp that tracks last execution of queries
    	if(null != taskArguments.get("timestamp-file")) {
    		relativePath = taskArguments.get("timestamp-file");
    	}
    	else if(null != taskArguments.get("machineAgent-relativePath")) {
    		relativePath = taskArguments.get("machineAgent-relativePath");
    		relativePath += "timeStamp.txt";
    	}
    	
    	else {
    		relativePath = "timeStamp.txt"; //if no path specified timestamp file will be created at root of machine agent directory
    	}
    	
    	
    
    	timeper_in_sec = taskArguments.get("timeper_in_sec");
    	execution_freq_in_secs = taskArguments.get("execution_freq_in_secs");
    	timeper_in_secConv = Long.valueOf(timeper_in_sec).longValue();
    
    	 setLogPrefix(taskArguments.get(LOG_PREFIX));
    	logger.debug(logPrefix+"timePeriod_in_sec: " + timeper_in_sec);
    	logger.debug(logPrefix+"path: " + relativePath);
    
    	
    	DateTime currentTime = new DateTime(new DateTime());
    	DateTime timeLastExecuted = new DateTime(new DateTime());
		
        if (taskArguments != null)
        {
            try 
            {	 	
            	String sCurrentLine;     
    	        br = new BufferedReader(new FileReader(relativePath));
    			
  							
    	        if(br != null)
    	        { 			 
    	        	while ((sCurrentLine = br.readLine()) != null) 
    	        	{  		
    	        		dateStampFromFile = sCurrentLine; 					
    	        		timeLastExecuted = DateTime.parse(dateStampFromFile);
    	        	}   			
    	        } 		
    	    } 
            catch (FileNotFoundException fex) {
        		logger.info(logPrefix+"timestamp file not found in "+relativePath+". File will be created");
        	}
            catch (IOException e) 
    	    {
            	e.printStackTrace(); 
    	    } 
            finally 
    	    {
            	try 
            	{
            		if (br != null)br.close();
            	} 
            	
            	catch (IOException ex) 
            	{
            		ex.printStackTrace();
            	}
    	    }
        	
           
            logger.info(getLogPrefix() + "Starting the SQL Monitoring task.");
            
            if (logger.isDebugEnabled()) 
            {
                logger.debug(getLogPrefix() + "Task Arguments Passed:" + taskArguments);
            }
           
            String configFilename = getConfigFilename(taskArguments.get("config-file"));

            try 
            {
            	     
            	Object obj = YmlReader.readFromFile(configFilename, Configuration.class);
                Configuration config = (Configuration) obj;

                if (config.getCommands().isEmpty()) 
                {
                    return new TaskOutput("Failure");
                }
                
				if (isCacheInvalidated(config)) {
					// something has been invalidated in the cache. Invalidate
					// the entire cache
					logger.info(logPrefix+"CACHE HAS BEEN INVALIDATED. CacheSize is "
							+ metricMap.size() + " no of commands is "
							+ config.getCommands().size());
					String cacheTimeout = config.getServers().get(0)
							.getCacheTimeout(); // only support one server in a
												// monitor at this time
					if (cacheTimeout == null) {
						cacheTimeout = "30";// in seconds. Default value
						logger.info(logPrefix+"using default cache value of 30s");
					}
					metricMap = CacheBuilder
							.newBuilder()
							.expireAfterWrite(
									(new Long(cacheTimeout)).longValue(),
									TimeUnit.SECONDS).build();
					logger.debug(logPrefix+"Built cache with value of "+(new Long(cacheTimeout)).longValue());

					// Move the timestamp update logic here. Update the
					// timestamp file only when cache is being refreshed.
					// Otherwise don't touch it
					File timeStampFile = new File(relativePath);
			    	FileWriter timeStampFileWriter = new FileWriter(timeStampFile.getAbsoluteFile());
	            	BufferedWriter timeStampFileBufferedWriter = new BufferedWriter(timeStampFileWriter); 
					logger.debug(logPrefix+"instant time (current time): " + currentTime);
					logger.debug(logPrefix+"old time (Time last executed query): "
							+ timeLastExecuted);
					diffInMillis = Math.abs(timeLastExecuted.getMillis()
							- currentTime.getMillis());
					float diffInSec = diffInMillis / 1000;
					float diffInMin = diffInSec / 60;

					if (timeper_in_secConv > diffInSec) {
						logger.debug(logPrefix+"execution frequency > time between query execution; no duplicate data.  Time in Minutes since last execution of queries: "
								+ diffInMin);
						logger.debug(logPrefix+"execution frequency in seconds: "
								+ timeper_in_secConv);
						logger.debug(logPrefix+"Time in sec: " + diffInSec);
						timeLastExecuted = new DateTime();
						timeStampFileBufferedWriter.write(timeLastExecuted.toString());
						timeStampFileBufferedWriter.close();
						logger.debug(logPrefix+"date written to file: "
								+ timeLastExecuted.toString());
						metricOverlap = false;

					} else if (timeper_in_secConv <= diffInSec) {
						logger.debug(logPrefix+"execution frequency < diffInSec");
						logger.debug(logPrefix+"Time in sec: " + diffInSec);
						timeLastExecuted = new DateTime();
						timeStampFileBufferedWriter.write(timeLastExecuted.toString());
						timeStampFileBufferedWriter.close();

						// store this in instance variable, then pass value into
						// queries
						DiffInSec = diffInSec;
						logger.debug(logPrefix+"execution frequency < diffInMin; DiffInSec variable value: "
								+ DiffInSec);
						metricOverlap = true;

						if(timeStampFileBufferedWriter !=null){
							timeStampFileBufferedWriter.close();
						}
						if(timeStampFileWriter != null){
							timeStampFileWriter.close();
						}
					}

				} else {
					logger.info(logPrefix+"CACHE IS VALID");
				}
				
				status = executeCommands(config, status);

            }
            catch (Exception ioe) 
            {
                logger.error(logPrefix+"Exception", ioe);
            }
            finally{
            	
            }
            
        }
        return new TaskOutput(status);

    }

	private boolean isCacheInvalidated(Configuration config) {
		for (Iterator<Command> iterator = config.getCommands().iterator(); iterator.hasNext();) {
			Command command = (Command) iterator.next();
			String query = command.getCommand();
			if(metricMap != null && metricMap.getIfPresent(query) ==null){
				logger.debug(logPrefix+"Query causing cache miss "+query+ " EOQ");
				return true; //If any query is no longer cached, then entire cache needs to be invalidated
			}
			
		}
		return false; //If we reached here. All commands are in cache. Cache is valid
		//return metricMap != null && metricMap.size()!=config.getCommands().size();
	}
  
    private String executeCommands(Configuration config, String status) 
    {
        Connection conn = null;
        
        try 
        {
            for (Server server : config.getServers()) 
            {
                conn = connect(server);              

                for (Command command : config.getCommands()) 
                {
                    try 
                    {
                        int counter = 1;
                        logger.info(logPrefix+"sql statement: " + counter++);
                        String statement = command.getCommand().trim();
                        String displayPrefix = command.getDisplayPrefix();
                                
                        if(displayPrefix == null)
                        {
                            logger.debug(logPrefix+"no displayPrefix set...");
                            command.setDisplayPrefix("Custom Metrics|default|");
                            logger.debug("..." + command.getDisplayPrefix());
                        }
                        
                        if (statement != null) 
                        {                       	                           
                            logger.debug(logPrefix+"Running " + statement);                        
                            executeQuery(conn, statement, displayPrefix);
                        } 
                        else 
                        {
                            logger.error(logPrefix+"Didn't find statement: " + counter);
                        }
                    } 
                    catch (Exception e) 
                    {
                        e.printStackTrace();
                    }
                }
                if (conn != null) 
                {
                    try 
                    {
                        conn.close();
                    } catch (SQLException e) 
                    {
                        e.printStackTrace();
                    }
                }
            }
        } 
        catch (SQLException sqle) 
        {
            logger.error(logPrefix+"SQLException: ", sqle);
            status = "Failure";
        } 
        catch (ClassNotFoundException ce) 
        {
            logger.error(logPrefix+"Class not found: ", ce);
            status = "Failure";
        } 
        finally 
        {
            if (conn != null) 
            {
            	try 
            	{
            		conn.close();
            	} 
            	catch (SQLException e) 
            	{
            		e.printStackTrace();
            	}
            }
        }
        return status;
    }

    private Data executeQuery(Connection conn, String query, String displayPrefix) 
    {
        Data retval = new Data();
        Statement stmt = null;
        java.sql.ResultSet rs = null;
        String newQuery = null;
        String customMetrics = "Custom Metrics|";
        displayPrefix = customMetrics + displayPrefix;
        
        try 
        {
            logger.debug(logPrefix+"dateStamp: " + dateStampFromFile);
            long rowcount = 0;
            ArrayList metricsList =  metricMap.getIfPresent(query);
            if(metricsList != null){
            	logger.info(logPrefix+"Cache hit for query: "+query);
            	for (Iterator iterator = metricsList.iterator(); iterator
						.hasNext();) {
            		SQLExtnMetric sqlMetric = (SQLExtnMetric) iterator.next();
            		logger.debug(logPrefix+"Sending cached metric: ");
            		logger.debug(logPrefix+"Metric path: "+sqlMetric.getName());
            		logger.debug(logPrefix+"Metric value: "+sqlMetric.getValue());
            		writemetric(sqlMetric.getName(),sqlMetric.getValue());
					
				}
            }
            else{

            	logger.info(logPrefix+"Cache miss for query: "+query);
                stmt = conn.createStatement(java.sql.ResultSet.TYPE_SCROLL_INSENSITIVE, java.sql.ResultSet.CONCUR_READ_ONLY);

	            if(query.contains("freqInSec"))
	            {
	            	logger.debug("query contains freqInSec - if loop hit ");
	            	
	            	if(metricOverlap == false)
	            	{
	            	    newQuery = query.replace("freqInSec", timeper_in_sec);
	                    rs = stmt.executeQuery(newQuery);              	
	                    logger.debug("skippedMetricWrite == false... query with timeDate replaced: " + newQuery);
	            	}
	            	else if(metricOverlap == true)
	            	{
	            	    String DiffInSecString = DiffInSec.toString();
	            	    newQuery = query.replace("freqInSec", DiffInSecString);
	                    rs = stmt.executeQuery(newQuery);
	                    logger.info(logPrefix+"query with timeDate replaced: " + newQuery);
	            	}           	
	            }
	            else
	            {
	            	rs = stmt.executeQuery(query);
	                logger.debug(logPrefix+"No freqInSec set in monitor.xml...");
	                logger.debug(logPrefix+"display prefix: " + displayPrefix);
	            }
	            
	            //get row and column count of result set
	            int rowCount = 0;
	            while (rs.next()) 
	            {
	                ++rowCount;              
	            }
	            logger.info(logPrefix+"row count of resultset: " + rowCount);
	            
	            ResultSetMetaData rsmd = rs.getMetaData();
	            int columnCount = rsmd.getColumnCount();
	            logger.info(logPrefix+"column count: " + columnCount);
	            //set cursor to beginning
	            rs.beforeFirst();
	           
	            
	            if(rowCount==0){
	            	//empty result set. 
	            	   ArrayList emptyMetricsList = new ArrayList();
	            	   metricMap.put(query, emptyMetricsList);
	            }
	           
            
	            //this deals with the single row/column case
	            else if(columnCount == 1 && rowCount == 1)
	            {           	           	
	            	if(rs.next())
	            	{
	            	    String key = cleanFieldName(rs.getMetaData().getColumnName(1));
	            	    logger.debug(logPrefix+"display prefix: " + displayPrefix);
	            	    logger.debug(logPrefix+"query result set has single row and column");
	                    String value = rs.getString(1);
	                    ResultSetMetaData metaData = rs.getMetaData();
	                    String name = metaData.getColumnLabel(1);
	                    retval.setName(name);
	                    retval.setValue(value); 
	                    
	                    String nameHolder = retval.getName();
	                    String valHolder = retval.getValue();               
	                    Data data = new Data(nameHolder, valHolder);                   	                
	                    String metricPath = displayPrefix + "|" + key ;//+ "|" + metricName;
	                    
	                    if(retval.getValue() != null)
	                    {                    	         
	                    	ArrayList<SQLExtnMetric> al = new ArrayList();
	                    	al.add(new SQLExtnMetric(metricPath,data.getValue()));
	                    	logger.debug(logPrefix+"Putting query "+query+" in cache");
	                    	metricMap.put(query,al);
	                    	
	                        writemetric(metricPath,data.getValue());
	                    
	                    	logger.info(logPrefix+"metric path: " + metricPath);
	                    	logger.info(logPrefix+"metric value: "  + " : " + rs.getString(1));
	                    }                
	            	}
	            }
	            // multi row columns returned, execute else statement below
	            else
	            {           
	            	ArrayList<SQLExtnMetric> al = new ArrayList();
            		while(rs.next())
            		{	        	
            			logger.debug(logPrefix+"while loop hit for multi row column..." + rowcount);
            			String key = cleanFieldName(rs.getString(1));
		                	
			            for (int i = 2; i <= rs.getMetaData().getColumnCount(); i++)
			            {      	            			            
			                logger.debug(logPrefix+"display prefix: " + displayPrefix);
			                logger.debug(logPrefix+"query result set has multiple rows and columns");
			            	String metricName = cleanFieldName(rs.getMetaData().getColumnName(i));	            		
			            	retval.setName(metricName);
			            	retval.setValue(rs.getString(i));       
			            	
			            	String nameHolder = retval.getName();
		                    String valHolder = retval.getValue();               
		                    Data data = new Data(nameHolder, valHolder);                   	                                  
		                    String metricPath = displayPrefix + "|" + key + "|" + metricName;
			            		
			            	if(retval.getValue() != null)
			                {            
			            		al.add(new SQLExtnMetric(metricPath,data.getValue()));
		                    	logger.debug(logPrefix+"Putting query "+query+" in cache");

			            		metricMap.put(query,al);
			                    writemetric(metricPath,data.getValue());	                    
			                    
			            		logger.info(logPrefix+"metric path: " + metricPath); 
			            		logger.info(logPrefix+"metric value: "  + " : " + rs.getString(i));
			                }  		
			            }
			        rowcount += 1;   	
            		} 
            	}
            }
                   
        } 
        catch (SQLException sqle) 
        {
            logger.error(logPrefix+"SQLException: ", sqle);
            logger.error(logPrefix+"timeper_in_sec (value passed to replace freqInSec): " + timeper_in_sec);
        } 
        finally 
        {
            if (rs != null) try 
            {
                rs.close();
            } 
            catch (SQLException e) 
            {}
            if (stmt != null) 
            {
            	try 
            	{
            	    stmt.close();
            	} 
            	catch (SQLException e) {}
            }
        }
        return retval;
    }

	private void writemetric( String metricPath, String metricValue) {
		String aggregationType = MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION;
		String timeRollup = MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE; //METRIC_TIME_ROLLUP_TYPE_CURRENT
		String clusterRollup = MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL;           // use METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE   	
		MetricWriter writer = getMetricWriter(metricPath, aggregationType, timeRollup, clusterRollup);                    	
		writer.printMetric(metricValue);
	}

    private Connection connect(Server server) throws SQLException, ClassNotFoundException 
    {
        Connection conn = null;
        String driver = server.getDriver();
        String connectionString = server.getConnectionString();
        String user = server.getUser();
        String password = this.getPassword(server);

        if (driver != null && connectionString != null) 
        {
            Class.forName(driver);            
            logger.debug(logPrefix+"driver: " + driver);      
            conn = DriverManager.getConnection(connectionString, user, password);
            logger.debug(logPrefix+"Got connection " + conn);
        }
        return conn;
    }   
    
    protected String getMetricPrefix()
    {
        if (metricPath != null)
        {
            if (!metricPath.endsWith("|"))
            {
                metricPath += "|";
            }
            return metricPath;
        }
        else
        {
            return "Custom Metrics|SQLMonitor|";
        }
    }


    private String getConfigFilename(String filename) 
    {
        if (filename == null) 
        {
            return "";
        }
        //for absolute paths
        if (new File(filename).exists()) 
        {
            return filename;
        }
        //for relative paths
        File jarPath = PathResolver.resolveDirectory(AManagedMonitor.class);
        String configFileName = "";
        if (!Strings.isNullOrEmpty(filename)) 
        {
            configFileName = jarPath + File.separator + filename;
        }
        return configFileName;
    }

    private String getLogPrefix() 
    {
        return logPrefix;
    }

    private void setLogPrefix(String logPrefix) 
    {
        ArbitrarySqlMonitor.logPrefix = (logPrefix != null) ? logPrefix : "";
    }

    //below main method is for testing locally in Eclipse.
    //when extension runs entry point is not this main method but execute method
    public static void main(String[] argv) throws Exception
    {
    	Map<String, String> taskArguments = new HashMap<String, String>();
    	taskArguments.put("config-file", "c:\\MA5\\MachineAgent41\\monitors\\ArbitrarySQLMonitor\\config.yml");
    	taskArguments.put("log-prefix", "[SQLMonitorAppDExt]");
    	taskArguments.put("machineAgent-relativePath", "c:\\MA5\\MachineAgent41\\monitors\\ArbitrarySQLMonitor\\");  	
    	taskArguments.put("timeper_in_sec", "179");
    	taskArguments.put("execution_freq_in_secs", "180");
    	
    	taskArguments.put("cache-timeout", "180");
        new ArbitrarySqlMonitor().execute(taskArguments, null);
    }
    
    private String getPassword(Server config) {
		String password = null;
		
		if (config.getPassword() != null & config.getPassword().trim().length() >0){
				
			password = config.getPassword();
			
		} else {
			try {
				Map<String, String> args = Maps.newHashMap();
				args.put(TaskInputArgs.PASSWORD_ENCRYPTED, config.getPasswordEncrypted());
				args.put(TaskInputArgs.ENCRYPTION_KEY, config.getEncryptionKey());
				password = CryptoUtil.getPassword(args);
				
			} catch (IllegalArgumentException e) {
				String msg = "Encryption Key not specified. Please set the value in config.yaml.";
				logger.error(logPrefix+msg);
				throw new IllegalArgumentException(msg);
			}
		}
		
		return password;
	}
}