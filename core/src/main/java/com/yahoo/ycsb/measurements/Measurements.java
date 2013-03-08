/**                                                                                                                                                                                
 * Copyright (c) 2010 Yahoo! Inc. All rights reserved.                                                                                                                             
 *                                                                                                                                                                                 
 * Licensed under the Apache License, Version 2.0 (the "License"); you                                                                                                             
 * may not use this file except in compliance with the License. You                                                                                                                
 * may obtain a copy of the License at                                                                                                                                             
 *                                                                                                                                                                                 
 * http://www.apache.org/licenses/LICENSE-2.0                                                                                                                                      
 *                                                                                                                                                                                 
 * Unless required by applicable law or agreed to in writing, software                                                                                                             
 * distributed under the License is distributed on an "AS IS" BASIS,                                                                                                               
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or                                                                                                                 
 * implied. See the License for the specific language governing                                                                                                                    
 * permissions and limitations under the License. See accompanying                                                                                                                 
 * LICENSE file.                                                                                                                                                                   
 */

package com.yahoo.ycsb.measurements;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.yahoo.ycsb.measurements.exporter.MeasurementsExporter;

/**
 * Collects latency measurements, and reports them when requested.
 * 
 * @author cooperb
 *
 */
public class Measurements
{
	private static final String MEASUREMENT_TYPE = "measurementtype";

	private static final String MEASUREMENT_TYPE_DEFAULT = "HISTOGRAM:com.yahoo.ycsb.measurements.OneMeasurementHistogram,TIMESERIES:com.yahoo.ycsb.measurements.OneMeasurementTimeSeries";

	static Measurements singleton=null;
	
	static Properties measurementproperties=null;
	
	public static void setProperties(Properties props)
	{
		measurementproperties=props;
	}

      /**
       * Return the singleton Measurements object.
       */
	public synchronized static Measurements getMeasurements()
	{
		if (singleton==null)
		{
			singleton=new Measurements(measurementproperties);
		}
		return singleton;
	}

	HashMap<String,OneMeasurement> data;

	private Properties _props;
	private Map<String,Class<? extends OneMeasurement>> measurementClasses;
	
      /**
       * Create a new object with the specified properties.
       */
	@SuppressWarnings("unchecked")
	public Measurements(Properties props)
	{
		data=new HashMap<String,OneMeasurement>();
		
		_props=props;
		
		measurementClasses = new HashMap<>();
		String[] measurementCls = _props.getProperty(MEASUREMENT_TYPE, MEASUREMENT_TYPE_DEFAULT).split(",");
		
		for(String mClassDescription : measurementCls){
			try {
				String[] cls = mClassDescription.split(":");
				if(cls.length < 2){
					System.err.println("Invalid format, use LABEL:com.example.package.classname");
				}
				measurementClasses.put(cls[0]+"_", (Class<? extends OneMeasurement>) Class.forName(cls[1]));
			} catch (ClassNotFoundException e) {
				System.err.println("Didn't find class " + mClassDescription);
				
			}
			
		}
		//measurementClasses.put("TIMESERIES_", OneMeasurementTimeSeries.class);
	}

      /**
       * Report a single value of a single metric. E.g. for read latency, operation="READ" and latency is the measured value.
       */
	public void measure(String operation, int latency) 
	{
		initDataForOperation(operation);
		for(String k: measurementClasses.keySet()){
			String dataKey = k+operation;
			data.get(dataKey).measure(latency);			
		}
	}
	
	private void initDataForOperation(String operation){
		for(String k: measurementClasses.keySet()){
			String dataKey = k+operation;
			if (!data.containsKey(dataKey))
			{
				synchronized(this)
				{
					if (!data.containsKey(dataKey))
					{
						Constructor<? extends OneMeasurement> ctor = null;
						try {
							ctor = measurementClasses.get(k).getConstructor(String.class, Properties.class);
							OneMeasurement m = ctor.newInstance(dataKey, _props);
							data.put(dataKey,m);
						} catch (NoSuchMethodException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e){
							// do not handle non working OneMeasurements
							System.err.println("Ignore Measurement class " + measurementClasses.get(k).getCanonicalName());
							measurementClasses.remove(k);
						}
						
					}
				}
			}
		}
	}

      /**
       * Report a return code for a single DB operaiton.
       */
	public void reportReturnCode(String operation, int code)
	{
		initDataForOperation(operation);
		for(String k: measurementClasses.keySet()){
			String dataKey = k+operation;
			data.get(dataKey).reportReturnCode(code);
		}
	}
	
  /**
   * Export the current measurements to a suitable format.
   * 
   * @param exporter Exporter representing the type of format to write to.
   * @throws IOException Thrown if the export failed.
   */
  public void exportMeasurements(MeasurementsExporter exporter) throws IOException
  {
    for (OneMeasurement measurement : data.values())
    {
      measurement.exportMeasurements(exporter);
    }
  }
	
      /**
       * Return a one line summary of the measurements.
       */
	public String getSummary()
	{
		String ret="";
		for (OneMeasurement m : data.values())
		{
			ret+=m.getSummary()+" ";
		}
		
		return ret;
	}
}
