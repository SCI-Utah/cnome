package edu.utah.sci.cyclist.core.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import edu.utah.sci.cyclist.core.Resources1;
import edu.utah.sci.cyclist.core.model.CyclistDatasource;

public class SimulationTablesPostProcessor {
	static Logger log = Logger.getLogger(SimulationTablesPostProcessor.class);
	
	//Calling external applications for post processing of sqlite database.
	private static final String  EXTERNAL_APPS = "externalApps";
	private static final String WINDOWS_OS = "windows";
	private static final String SAVED_POSTFIX = ".saved";
	
	
	private static final String FIX_AGENTS_TABLE_PHASE1 = "UPDATE Agents set ExitTime = EnterTime+(select Duration from Info where Agents.SimId=Info.SimId ) "+
			  "where ExitTime is null and Lifetime = -1";

	private static final String FIX_AGENTS_TABLE_PHASE2 = "update Agents set ExitTime=(EnterTime+Lifetime) where ExitTime is null and  Lifetime != -1;";
	
	//Must create the Facilities table in 2 steps, 
	//otherwise, if it just copies a BLOB column from another table, without explicitly creating it as a BLOB, the column is created without any type at all.
	private static final String FACILITIES_TABLE_CREATE = "create table if not exists Facilities (SimID BLOB,"+
																					"AgentId INTEGER,"+
																					"Spec TEXT,"+
																					"Prototype TEXT,"+
																					"InstitutionId INTEGER,"+
																					"RegionId INTEGER,"+
																					"EnterTime INTEGER,"+
																					"ExitTime INTEGER,"+
																					"Lifetime INTEGER)";
	
	private static final String FACILITIES_TABLE_UPDATE = "replace into Facilities(SimID,AgentId,Spec,Prototype,InstitutionId,RegionId,EnterTime,ExitTime,Lifetime) "+
														  "select f.SimId, f.AgentId, f.Spec, f.Prototype, i.AgentId, " +
														  "cast(-1 as INTEGER), f.EnterTime, f.ExitTime, f.Lifetime from Agents as f, Agents as i " +
														  "where f.Kind = 'Facility' and i.Kind = 'Inst' and f.ParentId = i.AgentId and f.SimId = i.SimId";

	private static final String FACILITIES_TABLE_INDEX = "create index if not exists Facilities_idx on Facilities (SimId ASC, AgentId ASC);";
	private static final String UPDATED_INDICATION_TABLE_CREATE = "create table if not exists UpdatedIndication (flag INTEGER DEFAULT 1)";
	private static final String TEST_UPDATED_QUERY = "SELECT name FROM sqlite_master WHERE type='table' AND name='UpdatedIndication'";
	
	private static final String QUANTITY_INVENTORY_BASE_CREATE = 
								   	"CREATE table if not exists QuantityInventoryBase as "
								   + "SELECT inv.SimId as SimId, tl.Time AS Time,cmp.NucId AS NucId,ag.AgentID as AgentID, "
								   + "  	cast(SUM(inv.Quantity*cmp.MassFrac) as REAL) AS Quantity "
								   + " FROM "
								   + "		Timelist AS tl "
								   + "		INNER JOIN Inventories AS inv ON inv.StartTime <= tl.Time AND inv.EndTime > tl.Time "
								   + "		INNER JOIN Agents AS ag ON ag.AgentId = inv.AgentId "
								   + "		INNER JOIN Compositions AS cmp ON cmp.QualId = inv.QualId "
								   + "	WHERE "
								   + "		inv.SimId = cmp.SimId AND inv.SimId = ag.SimId and tl.SimId=inv.SimId "
								   + "	GROUP BY inv.SimId, tl.Time, cmp.NucId, ag.AgentID; "
								   + "CREATE INDEX IF NOT EXISTS QuantityInventoryBase_idx ON QuantityInventoryBase (simid,agentid,time,nucid,quantity)";
	
	private static final String QUANTITY_INVENTORY_VIEW_CREATE = 
								  "CREATE view if not exists  QuantityInventory as "
								+ " SELECT base.SimID as SimID, Time, Quantity, NucId, base.AgentId as AgentId, Kind, Spec, Prototype"
								+ " FROM"
								+ "  	QuantityInventoryBase as base, Agents ag "
								+ " WHERE "
								+ "		base.SimID = ag.SimID "
								+ " AND base.AgentId = ag.AgentId";
	
	private static final String QUANTITY_TRANSACTED_BASE_CREATE = 
								  "CREATE table if not exists QuantityTransactedBase as "
								+ "SELECT res.SimId as SimId, tr.Time AS Time,cmp.NucId AS NucId, ag.AgentID as AgentID, "
								+ "		cast(SUM(cmp.MassFrac * res.Quantity) AS REAL) AS Quantity "
								+ "	FROM "
								+ "		Resources AS res "
								+ "		INNER JOIN Transactions AS tr ON tr.ResourceId = res.ResourceId "
								+ "		INNER JOIN Agents AS ag ON ag.AgentId = tr.SenderID "
								+ "		INNER JOIN Compositions AS cmp ON cmp.QualId = res.QualId "
								+ "	WHERE "
								+ "		tr.SimId = res.SimId AND ag.SimId = tr.SimId and cmp.SimId=res.SimId "
							    + "	GROUP BY res.SimId, cmp.NucId, tr.Time, ag.AgentID"
								+ "	ORDER BY tr.Time ASC;"
							    + " CREATE INDEX IF NOT EXISTS QuantityTransactedBase_idx ON QuantityTransactedBase (simid,agentid,time,nucid,quantity); ";

	
	private static final String QUANTITY_TRANSACTED_VIEW_CREATE = 
			  "CREATE view if not exists QuantityTransacted as "
			+ " SELECT base.SimID as SimID, Time, Quantity, NucId, base.AgentId as AgentId, Kind, Spec, Prototype"
			+ " FROM"
			+ "  	QuantityTransactedBase as base, Agents ag "
			+ " WHERE "
			+ "		base.SimID = ag.SimID "
			+ " AND base.AgentId = ag.AgentId";
	
	private QueryOperation[] UpdateTablesRunningOrderTbl = 
		{
			 new QueryOperation("Fix Agents table phase #1",FIX_AGENTS_TABLE_PHASE1),
		     new QueryOperation("Fix Agents table phase #2",FIX_AGENTS_TABLE_PHASE2),
		     
		     new QueryOperation("Create Facilitites table",FACILITIES_TABLE_CREATE),
		     new QueryOperation("Update Facilitites table",FACILITIES_TABLE_UPDATE),
		     new QueryOperation("Create Facilitites index",FACILITIES_TABLE_INDEX),
		     
		     new QueryOperation("Create base table QuantityInventoryBase",QUANTITY_INVENTORY_BASE_CREATE),
		     new QueryOperation("Create view QuantityInventory",QUANTITY_INVENTORY_VIEW_CREATE),
		     
		     new QueryOperation("Create base table QuantityTransactedBase",QUANTITY_TRANSACTED_BASE_CREATE),
		     new QueryOperation("Create view QuantityTransacted",QUANTITY_TRANSACTED_VIEW_CREATE),
		     
		     new QueryOperation("Create table UpdatedIndication",UPDATED_INDICATION_TABLE_CREATE)
		};
	
	private static final Map<String,String> applicationsMap;
    static
    {
    	applicationsMap = new HashMap<String, String>();
    	applicationsMap.put("darwin-64", "cycpost-darwin-amd64");
    	applicationsMap.put("darwin-386", "cycpost-darwin-386");
    	applicationsMap.put("mac-64", "cycpost-darwin-amd64");
    	applicationsMap.put("mac-386", "cycpost-darwin-386");
    	applicationsMap.put("windows-64", "cycpost-windows-amd64.exe");
    	applicationsMap.put("windows-386", "cycpost-windows-386.exe");
    	applicationsMap.put("linux-64", "cycpost-linux-amd64");
    	applicationsMap.put("linux-386", "cycpost-linux-386");
    	applicationsMap.put("linux-arm", "cycpost-linux-arm");
    }
    
    private static ObjectProperty<String> _message = new SimpleObjectProperty<String>("");
    private static boolean _onlyLastMsg = false;
    
    private static void postMsg(String s) {
    	if (_onlyLastMsg)
    		_message.set(s);
    	else
    		_message.set( _message.get()+s+"\n");
    }
    
	public Task<Boolean> process(CyclistDatasource ds){
		return process(ds, false);
	}
	
	public Task<Boolean> process(CyclistDatasource ds, boolean last) {
		_onlyLastMsg = last;
		Task<Boolean> task = new Task<Boolean>() {
	         @Override protected Boolean call() throws Exception {
	        	 _message.addListener(new ChangeListener<String>() {	 
	 		        @Override 
	 		        public void changed(ObservableValue<? extends String> arg0,String oldVal, String newVal) {
	 		        	updateMessage(_message.getValue());
	 		        }
	 		    });
	        	return processTask(ds);
	         }
		 };
		 Thread th = new Thread(task);
         th.setDaemon(true);
         th.start();
	     return task;
	}
    
    public Boolean processTask(CyclistDatasource ds){
		_message.set("");
		Boolean isUpdated = false;
		Connection conn = null;
		
		//First check if db is already updated.
		try{
			conn = ds.getConnection();
			isUpdated = dbIsUpdated(conn);
		}catch(Exception ex){
			ex.printStackTrace();
		}finally{
			//Need to close connection to enable updates of the database by external applications.
			try {
				conn.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			ds.releaseConnection();
		}
		
		//
		if(!isUpdated){
			Boolean result = true;
			String dsPath = ds.getProperties().getProperty("path");
			//Save the current db in a temporary file
			String savedPath = saveSqliteFile(dsPath);
			if(!savedPath.isEmpty()){
				result = updateSqliteSimTables(ds);
				//If result is true it means the new tables have been created by the external applications, 
				//Should also add the internal tables needed for the simulation database.
				if(result){
					result = createAdditionalTables(ds);
				}
				//If one of the updates has failed - roll back to the saved database.
				if(!result){
					cancelDbChanges(savedPath,dsPath);
					postMsg("Update database failed!");
				}else{
					//No need for the saved file anymore.
					File file = new File(savedPath);
					if(file.exists()){
						file.delete();
					}
				}
				postMsg("Done");
				return result;
			}
			return false;
		}else{
			postMsg("database is already updated ");
			return true;
		}
	}
	
	/**
	 * Checks if the update indication table exists in the current database.
	 * If it exists, it means the database is already updated, and no further update is required.
	 * @param ds - the data source to check.
	 * @return Boolean - true if the indication table was found, false otherwise.
	 */
	public static Boolean isDbUpdateRequired(CyclistDatasource ds){
		Statement stmt;
		try (Connection conn = ds.getConnection()) {
			stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(TEST_UPDATED_QUERY);
			return !rs.next();
		}catch (SQLException e) {
			// TODO: should NOT catch this rather let it propagate up
			log.error("SQL error while testing if db needs post processing: "+e.getMessage());
			return false;
		}finally{
			ds.releaseConnection();
		}
	}
	
	/*
	 * Saves the selected data source in a temporary file, until being sure
	 * that all the db operations have succeeded.
	 * @param CyclistDatasource ds - the datasource to copy.
	 */
	private static String saveSqliteFile(String path){
		if(! path.isEmpty()){
			String copiedPath = path+SAVED_POSTFIX;
			File oldFile = new File(path);
			File newFile = new File(copiedPath);
			try {
				if(newFile.exists()){
					newFile.delete();
				}
				FileUtils.copyFile(oldFile, newFile);
				return copiedPath;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return "";
			}
		}else{
			return "";
		}
	}
	
	/*
	 * In case that the db changes have failed, get back the old saved file.
	 */
	private static void cancelDbChanges(String savedPath, String path){
		File file = new File(path);
		if(file.exists()){
			file.delete();
		}
		File savedFile = new File(savedPath);
		if(savedFile.exists()){
			try {
				FileUtils.copyFile(savedFile, file);
				savedFile.delete();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	/*
	 * Checks if the update indication table exists in the current database.
	 * If it exists, it means the database is already updated.
	 * @param Connection conn - the connection to the database.
	 * @return Boolean - true if the indication table was found, false otherwise.
	 */
	private static Boolean dbIsUpdated(Connection conn){
		Statement stmt;
		try {
			stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(TEST_UPDATED_QUERY);
			if(rs.next()){
				return true;
			}else{
				return false;
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}
		
	/*
	 * Runs an application which creates the tables for the simulation.
	 * The application is chosen based on the operating system and its architecture.
	 * @param : Connection conn - the connection to the database.
	 * @param : CyclistDatasource ds - the data source to work on.
	 */
	private static Boolean updateSqliteSimTables(CyclistDatasource ds){
		String dsPath = ds.getProperties().getProperty("path");
		
		String currPath = Resources1.getCurrentPath();
		
		//Path always contains one directory below the main directory
		//e.g bin/ or the jar 
		//Go one directory up to be in the main level.
		//Then go to the external applications directory.
		int lastIndex = currPath.lastIndexOf("/");
		if(lastIndex >= 0){
			currPath = currPath.substring(0,lastIndex)+"/"+EXTERNAL_APPS;
		}
		
		String os = OsUtil.getOsDef();
		postMsg("Running external application. os="+os);
		String app = applicationsMap.get(os);
		
		Process process = null;
		
		if(app == null || app.isEmpty()){
			postMsg("No app has been found for os: " + os);
			return false;
		}
		
		if(os.indexOf(WINDOWS_OS)>=0){
				currPath += "/" + app;
		} else{
				currPath ="/" + currPath+ "/"+ app;
			
				File file = new File(currPath);
				file.setExecutable(true, false);
		        file.setReadable(true, false);
		        file.setWritable(true, false);
		}
		
		postMsg("path= " + currPath);
			
		//Indication whether or not the new tables have been produced.
	    Boolean isAlreadyProcessed = false;
	    
		if(!currPath.isEmpty()){
			try{
				process = Runtime.getRuntime().exec(new String[]{currPath,dsPath});
				InputStream is = process.getInputStream();
			    InputStreamReader isr = new InputStreamReader(is);
			    BufferedReader br = new BufferedReader(isr);
			    String line;
			    
			    while ((line = br.readLine()) != null) {
			      postMsg(line);
			      //Tables already exist - no need to reproduce additional tables.
			      if(line.indexOf("post processed") > -1){
//			    	  //If reached here the "UpdatedIndication" table doesn't exit. It means some of the other tables
			    	  //are missing, so even though the external applications update is not needed,
			    	  //it should try to produce the other tables anyway.
//			    	  isAlreadyProcessed = true;  
			    	  ;
			      }else{
			    	  //An error occurred - should not continue.
			    	  return false;
			      }
			    }
			} catch(Exception ex){
				return false;
			}
		    
		}else{
			//If no path to run the table creation application 
			//Send true, so the following process steps won't be run.
			isAlreadyProcessed = true;
		}
	    return !isAlreadyProcessed;
	}
	
	/*
	 * Runs SQL queries to create the facilities tables, based on the tables created by the post processing apps.
	 * @param conn - the connection to the database.
	 * @return true if succeed , false if fails.
	 */
	private Boolean createAdditionalTables(CyclistDatasource ds){
		Statement stmt;
		Connection conn = null;
		
		try {
			conn = ds.getConnection();
			stmt = conn.createStatement();
			postMsg("running queries:");
			for(QueryOperation queryOp : UpdateTablesRunningOrderTbl){
				postMsg(queryOp.name);
				stmt.executeUpdate(queryOp.query);
			}
			return true;
		} catch (SQLException e) {
			log.warn("Create additional table failed:", e);
			return false;
		}finally{
			try {
				conn.close();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			ds.releaseConnection();
		}
	}
	
	public class QueryOperation{
		public QueryOperation(String name,String query){
			this.name = name;
			this.query = query;
		}
		public String name;
		public String query;
	}
}
