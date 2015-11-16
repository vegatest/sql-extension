package com.appdynamics.extensions.sqlmonitoring;

/**
 * A simple bean to hold Metric name and value pair
 * @author stevew
 */
public class SQLExtnMetric
{
    private String name;
    private String value;
    
    public SQLExtnMetric()
    {
        this.name = null;
        this.value = null;
    }
    
    public SQLExtnMetric(String name, String value)
    {
        this.name = name;
        this.value = value;
    }
    
    public void setName(String name)
    {
        this.name = name;
    }
    
    public void setValue(String value)
    {
        this.value = value;
    }
    
    public String getName()
    {
        return this.name;
    }
    
    public String getValue()
    {
        return this.value;
    }
    
    @Override
    public String toString()
    {
        return name + " = " + value;
    }
}
