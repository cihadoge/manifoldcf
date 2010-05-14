/* $Id$ */

/**
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements. See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.lcf.agents.outputconnection;

import java.util.*;
import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import org.apache.lcf.agents.interfaces.CacheKeyFactory;
import org.apache.lcf.agents.system.LCF;


/** This class is the manager of the outputconnection description.  Inside, a database table is managed,
* with appropriate caching.
* Note well: The database handle is instantiated here using the DBInterfaceFactory.  This is acceptable because the
* actual database that this table is located in is fixed.
*/
public class OutputConnectionManager extends org.apache.lcf.core.database.BaseTable implements IOutputConnectionManager
{
  public static final String _rcsid = "@(#)$Id$";

  // Special field suffix
  private final static String passwordSuffix = "password";

  // Database fields
  protected final static String nameField = "connectionname";
  protected final static String descriptionField = "description";
  protected final static String classNameField = "classname";
  protected final static String maxCountField = "maxcount";
  protected final static String configField = "configxml";

  // Cache manager
  ICacheManager cacheManager;
  // Thread context
  IThreadContext threadContext;

  /** Constructor.
  *@param threadContext is the thread context.
  */
  public OutputConnectionManager(IThreadContext threadContext, IDBInterface database)
    throws LCFException
  {
    super(database,"outputconnections");

    cacheManager = CacheManagerFactory.make(threadContext);
    this.threadContext = threadContext;
  }

  /** Install the manager.
  */
  public void install()
    throws LCFException
  {
    // Always have an outer loop, in case retries required
    while (true)
    {
      Map existing = getTableSchema(null,null);
      if (existing == null)
      {
        // Install the "objects" table.
        HashMap map = new HashMap();
        map.put(nameField,new ColumnDescription("VARCHAR(32)",true,false,null,null,false));
        map.put(descriptionField,new ColumnDescription("VARCHAR(255)",false,true,null,null,false));
        map.put(classNameField,new ColumnDescription("VARCHAR(255)",false,false,null,null,false));
        map.put(maxCountField,new ColumnDescription("BIGINT",false,false,null,null,false));
        map.put(configField,new ColumnDescription("LONGTEXT",false,true,null,null,false));
        performCreate(map,null);
      }
      else
      {
        // Upgrade code, if needed, goes here.
      }

      // Index management code goes here.

      break;
    }
  }

  /** Uninstall the manager.
  */
  public void deinstall()
    throws LCFException
  {
    performDrop(null);
  }

  /** Export configuration */
  public void exportConfiguration(java.io.OutputStream os)
    throws java.io.IOException, LCFException
  {
    // Write a version indicator
    LCF.writeDword(os,1);
    // Get the authority list
    IOutputConnection[] list = getAllConnections();
    // Write the number of authorities
    LCF.writeDword(os,list.length);
    // Loop through the list and write the individual repository connection info
    int i = 0;
    while (i < list.length)
    {
      IOutputConnection conn = list[i++];
      LCF.writeString(os,conn.getName());
      LCF.writeString(os,conn.getDescription());
      LCF.writeString(os,conn.getClassName());
      LCF.writeString(os,conn.getConfigParams().toXML());
      LCF.writeDword(os,conn.getMaxConnections());
    }
  }

  /** Import configuration */
  public void importConfiguration(java.io.InputStream is)
    throws java.io.IOException, LCFException
  {
    int version = LCF.readDword(is);
    if (version != 1)
      throw new java.io.IOException("Unknown repository connection configuration version: "+Integer.toString(version));
    int count = LCF.readDword(is);
    int i = 0;
    while (i < count)
    {
      IOutputConnection conn = create();
      conn.setName(LCF.readString(is));
      conn.setDescription(LCF.readString(is));
      conn.setClassName(LCF.readString(is));
      conn.getConfigParams().fromXML(LCF.readString(is));
      conn.setMaxConnections(LCF.readDword(is));
      // Attempt to save this connection
      save(conn);
      i++;
    }
  }

  /** Obtain a list of the output connections, ordered by name.
  *@return an array of connection objects.
  */
  public IOutputConnection[] getAllConnections()
    throws LCFException
  {
    beginTransaction();
    try
    {
      // Read all the tools
      StringSetBuffer ssb = new StringSetBuffer();
      ssb.add(getOutputConnectionsKey());
      StringSet localCacheKeys = new StringSet(ssb);
      IResultSet set = performQuery("SELECT "+nameField+",lower("+nameField+") AS sortfield FROM "+getTableName()+" ORDER BY sortfield ASC",null,
        localCacheKeys,null);
      String[] names = new String[set.getRowCount()];
      int i = 0;
      while (i < names.length)
      {
        IResultRow row = set.getRow(i);
        names[i] = row.getValue(nameField).toString();
        i++;
      }
      return loadMultiple(names);
    }
    catch (LCFException e)
    {
      signalRollback();
      throw e;
    }
    catch (Error e)
    {
      signalRollback();
      throw e;
    }
    finally
    {
      endTransaction();
    }
  }

  /** Load an output connection by name.
  *@param name is the name of the output connection.
  *@return the loaded connection object, or null if not found.
  */
  public IOutputConnection load(String name)
    throws LCFException
  {
    return loadMultiple(new String[]{name})[0];
  }

  /** Load multiple output connections by name.
  *@param names are the names to load.
  *@return the loaded connection objects.
  */
  public IOutputConnection[] loadMultiple(String[] names)
    throws LCFException
  {
    // Build description objects
    OutputConnectionDescription[] objectDescriptions = new OutputConnectionDescription[names.length];
    int i = 0;
    StringSetBuffer ssb = new StringSetBuffer();
    while (i < names.length)
    {
      ssb.clear();
      ssb.add(getOutputConnectionKey(names[i]));
      objectDescriptions[i] = new OutputConnectionDescription(names[i],new StringSet(ssb));
      i++;
    }

    OutputConnectionExecutor exec = new OutputConnectionExecutor(this,objectDescriptions);
    cacheManager.findObjectsAndExecute(objectDescriptions,null,exec,getTransactionID());
    return exec.getResults();
  }

  /** Create a new output connection object.
  *@return the new object.
  */
  public IOutputConnection create()
    throws LCFException
  {
    OutputConnection rval = new OutputConnection();
    return rval;
  }

  /** Save an output connection object.
  *@param object is the object to save.
  */
  public void save(IOutputConnection object)
    throws LCFException
  {
    StringSetBuffer ssb = new StringSetBuffer();
    ssb.add(getOutputConnectionsKey());
    ssb.add(getOutputConnectionKey(object.getName()));
    StringSet cacheKeys = new StringSet(ssb);
    ICacheHandle ch = cacheManager.enterCache(null,cacheKeys,getTransactionID());
    try
    {
      beginTransaction();
      try
      {
        performLock();
        // Notify of a change to the configuration
        LCF.noteConfigurationChange();
        // See whether the instance exists
        ArrayList params = new ArrayList();
        params.add(object.getName());
        IResultSet set = performQuery("SELECT * FROM "+getTableName()+" WHERE "+
          nameField+"=? FOR UPDATE",params,null,null);
        HashMap values = new HashMap();
        values.put(descriptionField,object.getDescription());
        values.put(classNameField,object.getClassName());
        values.put(maxCountField,new Long((long)object.getMaxConnections()));
        String configXML = object.getConfigParams().toXML();
        values.put(configField,configXML);
        boolean notificationNeeded = false;

        if (set.getRowCount() > 0)
        {
          IResultRow row = set.getRow(0);
          String oldXML = (String)row.getValue(configField);
          if (oldXML == null || !oldXML.equals(configXML))
            notificationNeeded = true;

          // Update
          params.clear();
          params.add(object.getName());
          performUpdate(values," WHERE "+nameField+"=?",params,null);
        }
        else
        {
          // Insert
          values.put(nameField,object.getName());
          // We only need the general key because this is new.
          performInsert(values,null);
        }
        
        // If notification required, do it.
        if (notificationNeeded)
          AgentManagerFactory.noteOutputConnectionChange(threadContext,object.getName());

        cacheManager.invalidateKeys(ch);
      }
      catch (LCFException e)
      {
        signalRollback();
        throw e;
      }
      catch (Error e)
      {
        signalRollback();
        throw e;
      }
      finally
      {
        endTransaction();
      }
    }
    finally
    {
      cacheManager.leaveCache(ch);
    }
  }

  /** Delete an output connection.
  *@param name is the name of the connection to delete.  If the
  * name does not exist, no error is returned.
  */
  public void delete(String name)
    throws LCFException
  {
    StringSetBuffer ssb = new StringSetBuffer();
    ssb.add(getOutputConnectionsKey());
    ssb.add(getOutputConnectionKey(name));
    StringSet cacheKeys = new StringSet(ssb);
    ICacheHandle ch = cacheManager.enterCache(null,cacheKeys,getTransactionID());
    try
    {
      beginTransaction();
      try
      {
        // Check if anything refers to this connection name
        if (AgentManagerFactory.isOutputConnectionInUse(threadContext,name))
          throw new LCFException("Can't delete output connection '"+name+"': existing entities refer to it");
        LCF.noteConfigurationChange();
        ArrayList params = new ArrayList();
        params.add(name);
        performDelete("WHERE "+nameField+"=?",params,null);
        cacheManager.invalidateKeys(ch);
      }
      catch (LCFException e)
      {
        signalRollback();
        throw e;
      }
      catch (Error e)
      {
        signalRollback();
        throw e;
      }
      finally
      {
        endTransaction();
      }
    }
    finally
    {
      cacheManager.leaveCache(ch);
    }

  }

  /** Get a list of output connections that share the same connector.
  *@param className is the class name of the connector.
  *@return the repository connections that use that connector.
  */
  public String[] findConnectionsForConnector(String className)
    throws LCFException
  {
    StringSetBuffer ssb = new StringSetBuffer();
    ssb.add(getOutputConnectionsKey());
    StringSet localCacheKeys = new StringSet(ssb);
    ArrayList params = new ArrayList();
    params.add(className);
    IResultSet set = performQuery("SELECT "+nameField+" FROM "+getTableName()+" WHERE "+classNameField+"=?",params,
      localCacheKeys,null);
    String[] rval = new String[set.getRowCount()];
    int i = 0;
    while (i < rval.length)
    {
      IResultRow row = set.getRow(i);
      rval[i] = (String)row.getValue(nameField);
      i++;
    }
    java.util.Arrays.sort(rval);
    return rval;
  }

  /** Check if underlying connector exists.
  *@param name is the name of the connection to check.
  *@return true if the underlying connector is registered.
  */
  public boolean checkConnectorExists(String name)
    throws LCFException
  {
    beginTransaction();
    try
    {
      StringSetBuffer ssb = new StringSetBuffer();
      ssb.add(getOutputConnectionKey(name));
      StringSet localCacheKeys = new StringSet(ssb);
      ArrayList params = new ArrayList();
      params.add(name);
      IResultSet set = performQuery("SELECT "+classNameField+" FROM "+getTableName()+" WHERE "+nameField+"=?",params,
        localCacheKeys,null);
      if (set.getRowCount() == 0)
        throw new LCFException("No such connection: '"+name+"'");
      IResultRow row = set.getRow(0);
      String className = (String)row.getValue(classNameField);
      IOutputConnectorManager cm = OutputConnectorManagerFactory.make(threadContext);
      return cm.isInstalled(className);
    }
    catch (LCFException e)
    {
      signalRollback();
      throw e;
    }
    catch (Error e)
    {
      signalRollback();
      throw e;
    }
    finally
    {
      endTransaction();
    }
  }

  // Schema related
  /** Return the name column.
  *@return the name column.
  */
  public String getConnectionNameColumn()
  {
    return nameField;
  }

  // Caching strategy: Individual connection descriptions are cached, and there is a global cache key for the list of
  // output connections.

  /** Construct a key which represents the general list of output connectors.
  *@return the cache key.
  */
  protected static String getOutputConnectionsKey()
  {
    return CacheKeyFactory.makeOutputConnectionsKey();
  }

  /** Construct a key which represents an individual output connection.
  *@param connectionName is the name of the connector.
  *@return the cache key.
  */
  protected static String getOutputConnectionKey(String connectionName)
  {
    return CacheKeyFactory.makeOutputConnectionKey(connectionName);
  }

  // Other utility methods.

  /** Fetch multiple output connections at a single time.
  *@param connectionNames are a list of connection names.
  *@return the corresponding output connection objects.
  */
  protected OutputConnection[] getOutputConnectionsMultiple(String[] connectionNames)
    throws LCFException
  {
    OutputConnection[] rval = new OutputConnection[connectionNames.length];
    HashMap returnIndex = new HashMap();
    int i = 0;
    while (i < connectionNames.length)
    {
      rval[i] = null;
      returnIndex.put(connectionNames[i],new Integer(i));
      i++;
    }
    beginTransaction();
    try
    {
      i = 0;
      StringBuffer sb = new StringBuffer();
      ArrayList params = new ArrayList();
      int j = 0;
      int maxIn = getMaxInClause();
      while (i < connectionNames.length)
      {
        if (j == maxIn)
        {
          getOutputConnectionsChunk(rval,returnIndex,sb.toString(),params);
          sb.setLength(0);
          params.clear();
          j = 0;
        }
        if (j > 0)
          sb.append(',');
        sb.append('?');
        params.add(connectionNames[i]);
        i++;
        j++;
      }
      if (j > 0)
        getOutputConnectionsChunk(rval,returnIndex,sb.toString(),params);
      return rval;
    }
    catch (Error e)
    {
      signalRollback();
      throw e;
    }
    catch (LCFException e)
    {
      signalRollback();
      throw e;
    }
    finally
    {
      endTransaction();
    }
  }

  /** Read a chunk of output connections.
  *@param rval is the place to put the read policies.
  *@param returnIndex is a map from the object id (resource id) and the rval index.
  *@param idList is the list of id's.
  *@param params is the set of parameters.
  */
  protected void getOutputConnectionsChunk(OutputConnection[] rval, Map returnIndex, String idList, ArrayList params)
    throws LCFException
  {
    IResultSet set;
    set = performQuery("SELECT * FROM "+getTableName()+" WHERE "+
      nameField+" IN ("+idList+")",params,null,null);
    int i = 0;
    while (i < set.getRowCount())
    {
      IResultRow row = set.getRow(i++);
      String name = row.getValue(nameField).toString();
      int index = ((Integer)returnIndex.get(name)).intValue();
      OutputConnection rc = new OutputConnection();
      rc.setName(name);
      rc.setDescription((String)row.getValue(descriptionField));
      rc.setClassName((String)row.getValue(classNameField));
      rc.setMaxConnections((int)((Long)row.getValue(maxCountField)).longValue());
      String xml = (String)row.getValue(configField);
      if (xml != null && xml.length() > 0)
        rc.getConfigParams().fromXML(xml);
      rval[index] = rc;
    }

  }

  // The cached instance will be an OutputConnection.  The cached version will be duplicated when it is returned
  // from the cache.
  //
  // The description object is based completely on the name.

  /** This is the object description for an output connection object.
  */
  protected static class OutputConnectionDescription extends org.apache.lcf.core.cachemanager.BaseDescription
  {
    protected String connectionName;
    protected String criticalSectionName;
    protected StringSet cacheKeys;

    public OutputConnectionDescription(String connectionName, StringSet invKeys)
    {
      super("outputconnectioncache");
      this.connectionName = connectionName;
      criticalSectionName = getClass().getName()+"-"+connectionName;
      cacheKeys = invKeys;
    }

    public String getConnectionName()
    {
      return connectionName;
    }

    public int hashCode()
    {
      return connectionName.hashCode();
    }

    public boolean equals(Object o)
    {
      if (!(o instanceof OutputConnectionDescription))
        return false;
      OutputConnectionDescription d = (OutputConnectionDescription)o;
      return d.connectionName.equals(connectionName);
    }

    public String getCriticalSectionName()
    {
      return criticalSectionName;
    }

    /** Get the cache keys for an object (which may or may not exist yet in
    * the cache).  This method is called in order for cache manager to throw the correct locks.
    * @return the object's cache keys, or null if the object should not
    * be cached.
    */
    public StringSet getObjectKeys()
    {
      return cacheKeys;
    }

  }

  /** This is the executor object for locating output connection objects.
  */
  protected static class OutputConnectionExecutor extends org.apache.lcf.core.cachemanager.ExecutorBase
  {
    // Member variables
    protected OutputConnectionManager thisManager;
    protected OutputConnection[] returnValues;
    protected HashMap returnMap = new HashMap();

    /** Constructor.
    *@param manager is the OutputConnectionManager.
    *@param objectDescriptions are the object descriptions.
    */
    public OutputConnectionExecutor(OutputConnectionManager manager, OutputConnectionDescription[] objectDescriptions)
    {
      super();
      thisManager = manager;
      returnValues = new OutputConnection[objectDescriptions.length];
      int i = 0;
      while (i < objectDescriptions.length)
      {
        returnMap.put(objectDescriptions[i].getConnectionName(),new Integer(i));
        i++;
      }
    }

    /** Get the result.
    *@return the looked-up or read cached instances.
    */
    public OutputConnection[] getResults()
    {
      return returnValues;
    }

    /** Create a set of new objects to operate on and cache.  This method is called only
    * if the specified object(s) are NOT available in the cache.  The specified objects
    * should be created and returned; if they are not created, it means that the
    * execution cannot proceed, and the execute() method will not be called.
    * @param objectDescriptions is the set of unique identifier of the object.
    * @return the newly created objects to cache, or null, if any object cannot be created.
    *  The order of the returned objects must correspond to the order of the object descriptinos.
    */
    public Object[] create(ICacheDescription[] objectDescriptions) throws LCFException
    {
      // Turn the object descriptions into the parameters for the ToolInstance requests
      String[] connectionNames = new String[objectDescriptions.length];
      int i = 0;
      while (i < connectionNames.length)
      {
        OutputConnectionDescription desc = (OutputConnectionDescription)objectDescriptions[i];
        connectionNames[i] = desc.getConnectionName();
        i++;
      }

      return thisManager.getOutputConnectionsMultiple(connectionNames);
    }


    /** Notify the implementing class of the existence of a cached version of the
    * object.  The object is passed to this method so that the execute() method below
    * will have it available to operate on.  This method is also called for all objects
    * that are freshly created as well.
    * @param objectDescription is the unique identifier of the object.
    * @param cachedObject is the cached object.
    */
    public void exists(ICacheDescription objectDescription, Object cachedObject) throws LCFException
    {
      // Cast what came in as what it really is
      OutputConnectionDescription objectDesc = (OutputConnectionDescription)objectDescription;
      OutputConnection ci = (OutputConnection)cachedObject;

      // Duplicate it!
      if (ci != null)
        ci = ci.duplicate();

      // In order to make the indexes line up, we need to use the hashtable built by
      // the constructor.
      returnValues[((Integer)returnMap.get(objectDesc.getConnectionName())).intValue()] = ci;
    }

    /** Perform the desired operation.  This method is called after either createGetObject()
    * or exists() is called for every requested object.
    */
    public void execute() throws LCFException
    {
      // Does nothing; we only want to fetch objects in this cacher.
    }


  }

}
