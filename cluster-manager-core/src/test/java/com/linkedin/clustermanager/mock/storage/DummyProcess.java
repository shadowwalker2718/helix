package com.linkedin.clustermanager.mock.storage;

import java.io.File;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.linkedin.clustermanager.ClusterManager;
import com.linkedin.clustermanager.ClusterManagerFactory;
import com.linkedin.clustermanager.NotificationContext;
import com.linkedin.clustermanager.model.Message;
import com.linkedin.clustermanager.participant.StateMachineEngine;
import com.linkedin.clustermanager.participant.statemachine.StateModel;
import com.linkedin.clustermanager.participant.statemachine.StateModelFactory;
import com.linkedin.clustermanager.tools.ClusterSetup;
import com.linkedin.clustermanager.tools.ClusterStateVerifier;

public class DummyProcess
{

  public static final String zkServer = "zkSvr";
  public static final String cluster = "cluster";
  public static final String hostAddress = "host";
  public static final String hostPort = "port";
  public static final String relayCluster = "relayCluster";
  public static final String help = "help";
  public static final String configFile = "configFile";
  public static final String transDelay = "transDelay";

  private final String zkConnectString;
  private final String clusterName;
  private final String instanceName;
  private ClusterManager manager;
  private DummyStateModelFactory stateModelFactory;
  private StateMachineEngine genericStateMachineHandler;

  private String _file = null;
  private int _transDelayInMs = 0;

  public DummyProcess(String zkConnectString, String clusterName,
      String instanceName, String file, int delay)
  {
    this.zkConnectString = zkConnectString;
    this.clusterName = clusterName;
    this.instanceName = instanceName;
    this._file = file;
    _transDelayInMs = delay > 0 ? delay : 0;
  }

  public void start() throws Exception
  {
    if (_file == null)
      manager = ClusterManagerFactory.getZKBasedManagerForParticipant(
          clusterName, instanceName, zkConnectString);
    else
      manager = ClusterManagerFactory.getFileBasedManagerForParticipant(
          clusterName, instanceName, _file);

    stateModelFactory = new DummyStateModelFactory(_transDelayInMs);
    genericStateMachineHandler = new StateMachineEngine(stateModelFactory);
    manager.addMessageListener(genericStateMachineHandler, instanceName);

    if (_file != null)
    {
      ClusterStateVerifier.VerifyFileBasedClusterStates(_file, instanceName,
          stateModelFactory);

    }
  }

  public static class DummyStateModelFactory extends StateModelFactory
  {
    int _delay;
    public DummyStateModelFactory( int delay)
    {
      _delay = delay;
    }
    @Override
    public StateModel createNewStateModel(String stateUnitKey)
    {
      DummyStateModel model = new DummyStateModel();
      model.setDelay(_delay);
      return model;
    }
  }

  public static class DummyStateModel extends StateModel
  {
    int _transDelay = 0;
    public void setDelay(int delay)
    {
      _transDelay = delay > 0 ? delay : 0;
    }
    public void onBecomeSlaveFromOffline(Message message,
        NotificationContext context)
    {
      try
      {
        Thread.currentThread();
		Thread.sleep(_transDelay);
      } catch (InterruptedException e)
      {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      System.out.println("DummyStateModel.onBecomeSlaveFromOffline()");
    }

    public void onBecomeSlaveFromMaster(Message message,
        NotificationContext context)
    {
      try
      {
        Thread.currentThread();
		Thread.sleep(_transDelay);
      } catch (InterruptedException e)
      {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      System.out.println("DummyStateModel.onBecomeSlaveFromMaster()");

    }

    public void onBecomeMasterFromSlave(Message message,
        NotificationContext context)
    {
      try
      {
        Thread.currentThread();
		Thread.sleep(_transDelay);
      } catch (InterruptedException e)
      {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      System.out.println("DummyStateModel.onBecomeMasterFromSlave()");

    }

    public void onBecomeOfflineFromSlave(Message message,
        NotificationContext context)
    {
      try
      {
        Thread.currentThread();
		Thread.sleep(_transDelay);
      } catch (InterruptedException e)
      {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      System.out.println("DummyStateModel.onBecomeOfflineFromSlave()");

    }
    
    public void onBecomeDropped(Message message,
        NotificationContext context)
    {
      try
      {
        Thread.currentThread().sleep(_transDelay);
      } catch (InterruptedException e)
      {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      System.out.println("DummyStateModel.onBecomeDropped()");

    }
  }

  @SuppressWarnings("static-access")
  private static Options constructCommandLineOptions()
  {
    Option helpOption = OptionBuilder.withLongOpt(help)
        .withDescription("Prints command-line options info").create();

    Option zkServerOption = OptionBuilder.withLongOpt(zkServer)
        .withDescription("Provide zookeeper address").create();
    zkServerOption.setArgs(1);
    zkServerOption.setRequired(true);
    zkServerOption.setArgName("ZookeeperServerAddress(Required)");

    Option clusterOption = OptionBuilder.withLongOpt(cluster)
        .withDescription("Provide cluster name").create();
    clusterOption.setArgs(1);
    clusterOption.setRequired(true);
    clusterOption.setArgName("Cluster name (Required)");

    Option hostOption = OptionBuilder.withLongOpt(hostAddress)
        .withDescription("Provide host name").create();
    hostOption.setArgs(1);
    hostOption.setRequired(true);
    hostOption.setArgName("Host name (Required)");

    Option portOption = OptionBuilder.withLongOpt(hostPort)
        .withDescription("Provide host port").create();
    portOption.setArgs(1);
    portOption.setRequired(true);
    portOption.setArgName("Host port (Required)");

    // add an option group including either --zkSvr or --configFile
    Option fileOption = OptionBuilder.withLongOpt(configFile)
        .withDescription("Provide file to read states/messages").create();
    fileOption.setArgs(1);
    fileOption.setRequired(true);
    fileOption.setArgName("File to read states/messages (Optional)");
    
    Option transDelayOption = OptionBuilder.withLongOpt(transDelay)
      .withDescription("Provide state trans delay").create();
    transDelayOption.setArgs(1);
    transDelayOption.setRequired(false);
    transDelayOption.setArgName("Delay time in state transition, in MS");

    OptionGroup optionGroup = new OptionGroup();
    optionGroup.addOption(zkServerOption);
    optionGroup.addOption(fileOption);

    Options options = new Options();
    options.addOption(helpOption);
    // options.addOption(zkServerOption);
    options.addOption(clusterOption);
    options.addOption(hostOption);
    options.addOption(portOption);
    options.addOption(transDelayOption);
    
    options.addOptionGroup(optionGroup);

    return options;
  }

  public static void printUsage(Options cliOptions)
  {
    HelpFormatter helpFormatter = new HelpFormatter();
    helpFormatter.printHelp("java " + ClusterSetup.class.getName(), cliOptions);
  }

  public static CommandLine processCommandLineArgs(String[] cliArgs)
      throws Exception
  {
    CommandLineParser cliParser = new GnuParser();
    Options cliOptions = constructCommandLineOptions();
    CommandLine cmd = null;

    try
    {
      return cliParser.parse(cliOptions, cliArgs);
    } catch (ParseException pe)
    {
      System.err
          .println("CommandLineClient: failed to parse command-line options: "
              + pe.toString());
      printUsage(cliOptions);
      System.exit(1);
    }
    return null;
  }

  public static void main(String[] args) throws Exception
  {
    String zkConnectString = "localhost:2181";
    String clusterName = "test-cluster";
    String instanceName = "localhost_8900";
    String file = null;
    int delay = 0;

    if (args.length > 0)
    {
      CommandLine cmd = processCommandLineArgs(args);
      zkConnectString = cmd.getOptionValue(zkServer);
      clusterName = cmd.getOptionValue(cluster);

      String host = cmd.getOptionValue(hostAddress);
      String portString = cmd.getOptionValue(hostPort);
      int port = Integer.parseInt(portString);
      instanceName = host + "_" + port;
      
      file = cmd.getOptionValue(configFile);
      if (file != null)
      {
        File f = new File(file);
        if (!f.exists())
        {
          System.err.println("static config file doesn't exist");
          System.exit(1);
        }
      }
      if(cmd.hasOption(transDelay))
      {
        try
        {
          delay = Integer.parseInt(cmd.getOptionValue(transDelay));
          if(delay < 0)
          {
            throw new Exception ("delay must be positive");
          }
        }
        catch(Exception e)
        {
          e.printStackTrace();
          delay = 0;
        }
      }
    }
    // Espresso_driver.py will consume this
    System.out.println("Dummy process started");

    DummyProcess process = new DummyProcess(zkConnectString, clusterName,
        instanceName, file, delay);

    process.start();
    Thread.currentThread().join();
  }
}