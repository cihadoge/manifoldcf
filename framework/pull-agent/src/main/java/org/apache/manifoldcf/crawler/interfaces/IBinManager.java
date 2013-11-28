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
package org.apache.manifoldcf.crawler.interfaces;

import org.apache.manifoldcf.core.interfaces.*;
import java.util.*;

/** This interface represents a class that tracks data in document bins.
*/
public interface IBinManager
{
  public static final String _rcsid = "@(#)$Id$";

  /** Install or upgrade this table.
  */
  public void install()
    throws ManifoldCFException;

  /** Uninstall.
  */
  public void deinstall()
    throws ManifoldCFException;

  /** Reset all bins */
  public void reset()
    throws ManifoldCFException;

  /** Get a bin value (and set next one).  If the record does not yet exist, create it with a starting value.
  * We expect this to happen within a transaction!! 
  *@param binName is the name of the bin (256 char max)
  *@param newBinValue is the value to use if there is no such bin yet.  This is the value that will be
  * returned; what will be stored will be that value + 1.
  *@return the counter value.
  */
  public double getIncrementBinValue(String binName, double newBinValue)
    throws ManifoldCFException;

}