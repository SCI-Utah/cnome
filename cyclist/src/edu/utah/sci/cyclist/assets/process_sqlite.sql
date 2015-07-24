-- VERSION: 1.0

--  Cyclist info
CREATE table if not exists Cyclist (
	Version TEXT primary key,
	Date Date);	
		
--  Ensure Agents compatibility
UPDATE Agents set ExitTime = EnterTime+(select Duration from Info where Agents.SimId=Info.SimId ) 
	   WHERE ExitTime is null and Lifetime = -1;
	   
UPDATE Agents set ExitTime=(EnterTime+Lifetime) where ExitTime is null and  Lifetime != -1;	   

--  Create Facilities
create table if not exists Facilities (
	SimID BLOB,
	AgentId INTEGER,
	Spec TEXT,
	Prototype TEXT,
	InstitutionId INTEGER,
	RegionId INTEGER,
	EnterTime INTEGER,
	ExitTime INTEGER,
	Lifetime INTEGER);
	
REPLACE into Facilities(SimID,AgentId,Spec,Prototype,InstitutionId,RegionId,EnterTime,ExitTime,Lifetime) 
	select f.SimId, f.AgentId, f.Spec, f.Prototype, i.AgentId, 
		   cast(-1 as INTEGER), f.EnterTime, f.ExitTime, f.Lifetime from Agents as f, Agents as i 
	 where f.Kind = 'Facility' and i.Kind = 'Inst' and f.ParentId = i.AgentId and f.SimId = i.SimId;
	 
CREATE INDEX if not exists Facilities_idx on Facilities (SimId ASC, AgentId ASC);

--  Inventory
CREATE table if not exists QuantityInventoryBase as 
	SELECT inv.SimId as SimId, tl.Time AS Time,cmp.NucId AS NucId,ag.AgentID as AgentID, 
	  	cast(SUM(inv.Quantity*cmp.MassFrac) as REAL) AS Quantity 
	 FROM 
		Timelist AS tl 
		INNER JOIN Inventories AS inv ON inv.StartTime <= tl.Time AND inv.EndTime > tl.Time 
		INNER JOIN Agents AS ag ON ag.AgentId = inv.AgentId 
		INNER JOIN Compositions AS cmp ON cmp.QualId = inv.QualId 
	 WHERE 
		inv.SimId = cmp.SimId AND inv.SimId = ag.SimId and tl.SimId=inv.SimId 
	 GROUP BY inv.SimId, tl.Time, cmp.NucId, ag.AgentID; 

CREATE INDEX IF NOT EXISTS QuantityInventoryBase_idx ON QuantityInventoryBase (simid,agentid,time,nucid,quantity);
	 	
CREATE view if not exists  QuantityInventory as 
	SELECT base.SimID as SimID, Time, Quantity, NucId, base.AgentId as AgentId, Kind, Spec, Prototype
	 FROM
		QuantityInventoryBase as base, Agents ag 
	 WHERE 
		base.SimID = ag.SimID 
		AND base.AgentId = ag.AgentId;
		
--  Transacted
CREATE table if not exists QuantityTransactedBase as 
	SELECT res.SimId as SimId, tr.Time AS Time,cmp.NucId AS NucId, ag.AgentID as AgentID, 
		cast(SUM(cmp.MassFrac * res.Quantity) AS REAL) AS Quantity 
	FROM 
		Resources AS res 
		INNER JOIN Transactions AS tr ON tr.ResourceId = res.ResourceId 
		INNER JOIN Agents AS ag ON ag.AgentId = tr.SenderID 
		INNER JOIN Compositions AS cmp ON cmp.QualId = res.QualId 
		WHERE 
			tr.SimId = res.SimId AND ag.SimId = tr.SimId and cmp.SimId=res.SimId 
		GROUP BY res.SimId, cmp.NucId, tr.Time, ag.AgentID
		ORDER BY tr.Time ASC;
		
CREATE INDEX IF NOT EXISTS QuantityTransactedBase_idx ON QuantityTransactedBase (simid,agentid,time,nucid,quantity); ;

CREATE view if not exists QuantityTransacted as 
	SELECT base.SimID as SimID, Time, Quantity, NucId, base.AgentId as AgentId, Kind, Spec, Prototype
	FROM
		QuantityTransactedBase as base, Agents ag 
	WHERE 
		base.SimID = ag.SimID 
		AND base.AgentId = ag.AgentId;	
		
		
