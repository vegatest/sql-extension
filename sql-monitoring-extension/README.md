riak-monitoring-extension
=========================

An AppDynamics extension to be used with a stand alone Java machine agent to provide metrics from riak instances.

## Metrics Provided ##
  The metrics provided can be configured in <MACHINE_AGENT_HOME>/monitors/RiakMonitor/

  node_gets, node_gets_total, node_puts,node_puts_total,vnode_gets,vnode_gets_total,vnode_puts_total,memory_processes_used,sys_process_count,
  pbc_connect,pbc_active.

  You can view the names of any other metrics to be configured here http://docs.basho.com/riak/latest/ops/running/stats-and-monitoring/

  We also send a health check metric "Health Check=1"  (when success) or "Health Check=-1" (when failure).

## Installation ##

1. Download and unzip RiakMonitor.zip from AppSphere.
2. Copy the RiakMonitor directory to `<MACHINE_AGENT_HOME>/monitors`.


## Configuration ##

###Note
Please make sure to not use tab (\t) while editing yaml files. You may want to validate the yaml file using a yaml validator http://yamllint.com/

1. Configure the riak instances by editing the config.yaml file in `<MACHINE_AGENT_HOME>/monitors/RiakMonitor/`. Below is the format

    ```
        # List of riak servers
        servers:
          - host: "myDebian.sandbox.appdynamics.com"
            port: 8098
            displayName: myDebian

          - host: "myUbuntu.sandbox.appdynamics.com"
            port: 8098
            displayName: myUbuntu


        metrics: [
          "node_gets",
          "node_gets_total",
          "node_puts",
          "node_puts_total",
          "vnode_gets",
          "vnode_gets_total",
          "vnode_puts_total",
          "memory_processes_used",
          "sys_process_count",
          "pbc_connect",
          "pbc_active"
        ]


        #prefix used to show up metrics in AppDynamics
        metricPrefix:  "Custom Metrics|Riak|"

        # number of concurrent tasks
        numberOfThreads: 10

        #timeout for the thread in seconds
        threadTimeout: 30

        #configuration for making http calls
        httpConfig: {
          use-ssl : false,
          proxy-host : "",
          proxy-port : "",
          proxy-username : "",
          proxy-password : "",
          proxy-use-ssl : "",
          socket-timeout : 10, # in seconds
          connect-timeout : 10 # in seconds
        }


    ```

2. Configure the path to the config.yaml file by editing the <task-arguments> in the monitor.xml file. Below is the sample

     ```
         <task-arguments>
             <!-- config file-->
             <argument name="config-file" is-required="true" default-value="monitors/RiakMonitor/config.yaml" />
              ....
         </task-arguments>

     ```

###Cluster level metrics : 

We support cluster level metrics only if each node in the cluster have a separate machine agent installed on it. There are two configurations required for this setup 

1. Make sure that nodes belonging to the same cluster has the same <tier-name> in the <MACHINE_AGENT_HOME>/conf/controller-info.xml, we can gather cluster level metrics.  The tier-name here should be your cluster name. 

2. Make sure that in every node in the cluster, the <MACHINE_AGENT_HOME>/monitors/RiakMonitor/config.yaml should emit the same metric path. To achieve this make the displayName to be empty string and remove the trailing "|" in the metricPrefix.  

To make it more clear,assume that Riak "Node A" and Riak "Node B" belong to the same cluster "ClusterAB". In order to achieve cluster level as well as node level metrics, you should do the following
        
1. Both Node A and Node B should have separate machine agents installed on them. Both the machine agent should have their own Riak extension.
    
2. In the Node A's and Node B's machine agents' controller-info.xml make sure that you have the tier name to be your cluster name , "ClusterAB" here. Also, nodeName in controller-info.xml is Node A and Node B resp.
        
3. The config.yaml for Node A and Node B should be


```
        # List of riak servers
        servers:
          - host: "myDebian.sandbox.appdynamics.com"
            port: 8098
            displayName: ""



        metrics: [
          "node_gets",
          "node_gets_total",
          "node_puts",
          "node_puts_total",
          "vnode_gets",
          "vnode_gets_total",
          "vnode_puts_total",
          "memory_processes_used",
          "sys_process_count",
          "pbc_connect",
          "pbc_active"
        ]


        #prefix used to show up metrics in AppDynamics
        metricPrefix:  "Custom Metrics|Riak"

        # number of concurrent tasks
        numberOfThreads: 10

        #timeout for the thread in seconds
        threadTimeout: 30

        #configuration for making http calls
        httpConfig: {
          use-ssl : false,
          proxy-host : "",
          proxy-port : "",
          proxy-username : "",
          proxy-password : "",
          proxy-use-ssl : "",
          socket-timeout : 10, # in seconds
          connect-timeout : 10 # in seconds
        }


```

Now, if Node A and Node B are reporting say a metric called ReadLatency to the controller, with the above configuration they will be reporting it using the same metric path.
        
Node A reports Custom Metrics | ClusterAB | ReadLatency = 50 
Node B reports Custom Metrics | ClusterAB | ReadLatency = 500
        
The controller will automatically average out the metrics at the cluster (tier) level as well. So you should be able to see the cluster level metrics under
        
Application Performance Management | Custom Metrics | ClusterAB | ReadLatency = 225
        
Also, now if you want to see individual node metrics you can view it under
        
Application Performance Management | Custom Metrics | ClusterAB | Individual Nodes | Node A | ReadLatency = 50 
Application Performance Management | Custom Metrics | ClusterAB | Individual Nodes | Node B | ReadLatency = 500

Please note that for now the cluster level metrics are obtained by the averaging all the individual node level metrics in a cluster.

## Custom Dashboard ##

## Contributing ##

Always feel free to fork and contribute any changes directly via [GitHub][].

## Community ##

Find out more in the [Community][].

## Support ##

For any questions or feature request, please contact [AppDynamics Center of Excellence][].

**Version:** 1.0
**Controller Compatibility:** 3.7 or later
**Riak version tested on:** 1.4.8 

[GitHub]: https://github.com/Appdynamics/riak-monitoring-extension
[Community]: http://community.appdynamics.com/
[AppDynamics Center of Excellence]: mailto:ace-request@appdynamics.com
