package org.apache.helix.monitoring;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.helix.ConfigAccessor;
import org.apache.helix.HelixDataAccessor;
import org.apache.helix.MonitoringTestHelper;
import org.apache.helix.PropertyKey;
import org.apache.helix.TestHelper;
import org.apache.helix.ZNRecord;
import org.apache.helix.ZkUnitTestBase;
import org.apache.helix.api.id.ClusterId;
import org.apache.helix.api.id.ResourceId;
import org.apache.helix.integration.manager.ClusterControllerManager;
import org.apache.helix.manager.zk.ZKHelixDataAccessor;
import org.apache.helix.manager.zk.ZKHelixManager;
import org.apache.helix.manager.zk.ZkBaseDataAccessor;
import org.apache.helix.model.HelixConfigScope;
import org.apache.helix.model.HelixConfigScope.ConfigScopeProperty;
import org.apache.helix.model.IdealState.RebalanceMode;
import org.apache.helix.model.MonitoringConfig;
import org.apache.helix.model.builder.HelixConfigScopeBuilder;
import org.apache.helix.tools.ClusterStateVerifier;
import org.junit.Assert;
import org.testng.annotations.Test;

import com.aphyr.riemann.Proto.Event;
import com.aphyr.riemann.client.RiemannClient;

public class TestClientServerMonitoring extends ZkUnitTestBase {
  @Test
  public void testMonitoring() throws Exception {
    final int NUM_PARTICIPANTS = 0;
    final int NUM_PARTITIONS = 8;
    final int NUM_REPLICAS = 1;

    String className = TestHelper.getTestClassName();
    String methodName = TestHelper.getTestMethodName();
    String clusterName = className + "_" + methodName;
    System.out.println("START " + clusterName + " at " + new Date(System.currentTimeMillis()));

    // Set up monitoring cluster
    TestHelper.setupCluster(clusterName, ZK_ADDR, 12918, // participant port
        "localhost", // participant name prefix
        "MonitoringService", // resource name prefix
        1, // resources
        NUM_PARTITIONS, // partitions per resource
        NUM_PARTICIPANTS, // number of nodes
        NUM_REPLICAS, // replicas
        "OnlineOffline", // pick a built-in state model
        RebalanceMode.FULL_AUTO, // let Helix handle rebalancing
        true); // do rebalance

    // Enable auto-join
    HelixConfigScope scope =
        new HelixConfigScopeBuilder(ConfigScopeProperty.CLUSTER).forCluster(clusterName).build();
    ConfigAccessor configAccessor = new ConfigAccessor(_gZkClient);
    configAccessor.set(scope, ZKHelixManager.ALLOW_PARTICIPANT_AUTO_JOIN, "" + true);

    // Start controller
    ClusterControllerManager controller =
        new ClusterControllerManager(ZK_ADDR, clusterName, "controller");
    controller.syncStart();

    // Start monitoring server
    int port = MonitoringTestHelper.availableTcpPort();
    MonitoringConfig monitoringConfig = new MonitoringConfig(RiemannConfigs.DEFAULT_RIEMANN_CONFIG);
    monitoringConfig.setConfig(MonitoringTestHelper.getRiemannConfigString(port));

    RiemannConfigs.Builder builder = new RiemannConfigs.Builder().addConfig(monitoringConfig);
    RiemannMonitoringServer server = new RiemannMonitoringServer(builder.build());
    server.start();

    // Start Riemann agent
    RiemannAgent agent = new RiemannAgent(ZK_ADDR, clusterName, port);
    agent.start();

    // Check live-instance
    final HelixDataAccessor accessor =
        new ZKHelixDataAccessor(clusterName, new ZkBaseDataAccessor<ZNRecord>(_gZkClient));
    final PropertyKey.Builder keyBuilder = accessor.keyBuilder();
    List<String> liveInstances = accessor.getChildNames(keyBuilder.liveInstances());
    Assert.assertNotNull(liveInstances);
    Assert.assertEquals(liveInstances.size(), 1);

    // Check external-view
    boolean result =
        ClusterStateVerifier
            .verifyByZkCallback(new ClusterStateVerifier.BestPossAndExtViewZkVerifier(ZK_ADDR,
                clusterName));
    Assert.assertTrue(result);

    // Connect monitoring client
    final RiemannMonitoringClient client =
        new RiemannMonitoringClient(ZK_ADDR, ClusterId.from(clusterName),
            ResourceId.from("MonitoringService0"), 1);
    client.connect();

    final RiemannClient rclient = RiemannClient.tcp("localhost", port);
    rclient.connect();

    // Test MonitoringEvent#send()
    MonitoringEvent event = new MonitoringEvent().tag("test").ttl(5);
    client.send(ResourceId.from("TestDB"), event, false);

    // Check monitoring server has received the event with tag="test"
    result = TestHelper.verify(new TestHelper.Verifier() {

      @Override
      public boolean verify() throws Exception {
        List<Event> events = rclient.query("tagged \"test\"");
        return (events.size() == 1) && (events.get(0).getTagsCount() == 1)
            && (events.get(0).getTags(0).equals("test"));
      }
    }, 5 * 1000);
    Assert.assertTrue(result);

    // Test MonitoringEvent#sendAndFlush()
    MonitoringEvent event2 = new MonitoringEvent().tag("test2").ttl(5);
    client.sendAndFlush(ResourceId.from("TestDB2"), event2);

    // Check monitoring server has received the event with tag="test2"
    result = TestHelper.verify(new TestHelper.Verifier() {

      @Override
      public boolean verify() throws Exception {
        List<Event> events = rclient.query("tagged \"test2\"");
        return (events.size() == 1) && (events.get(0).getTagsCount() == 1)
            && (events.get(0).getTags(0).equals("test2"));
      }
    }, 5 * 1000);
    Assert.assertTrue(result);

    // Test MonitoringEvent#every()
    client.every(ResourceId.from("TestDB3"), 1, 0, TimeUnit.SECONDS, new Runnable() {

      @Override
      public void run() {
        MonitoringEvent event3 =
            new MonitoringEvent().tag("test3").resource(ResourceId.from("db" + System.currentTimeMillis())).ttl(5);
        client.send(ResourceId.from("TestDB3"), event3, false);
      }
    });

    // Check monitoring server has received at least 2 event2 with tag="test3"
    result = TestHelper.verify(new TestHelper.Verifier() {

      @Override
      public boolean verify() throws Exception {
        List<Event> events = rclient.query("tagged \"test3\"");
        return (events.size() > 2) && (events.get(0).getTagsCount() == 1)
            && (events.get(0).getTags(0).equals("test3"));
      }
    }, 10 * 1000);
    Assert.assertTrue(result);

    // Stop client
    client.disconnect();
    rclient.disconnect();

    // Stop controller
    controller.syncStop();

    System.out.println("END " + clusterName + " at " + new Date(System.currentTimeMillis()));
  }

}