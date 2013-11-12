/*
 * Copyright (c) 2013 NEC Corporation
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vtn.manager.internal;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.controller.forwardingrulesmanager.FlowEntry;
import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.core.NodeConnector;
import org.opendaylight.controller.sal.match.Match;
import org.opendaylight.controller.sal.match.MatchType;
import org.opendaylight.controller.sal.utils.NodeConnectorCreator;
import org.opendaylight.controller.sal.utils.NodeCreator;
import org.opendaylight.vtn.manager.VBridgeIfPath;
import org.opendaylight.vtn.manager.VBridgePath;
import org.opendaylight.vtn.manager.VTenantPath;
import org.opendaylight.vtn.manager.internal.cluster.FlowGroupId;
import org.opendaylight.vtn.manager.internal.cluster.MacVlan;
import org.opendaylight.vtn.manager.internal.cluster.VTNFlow;

/**
 * JUnit test for {@link VTNFlowDatabaseTest}
 */
public class VTNFlowDatabaseTest extends TestUseVTNManagerBase {

    @BeforeClass
    public static void beforeClass() {
        stubMode = 2;
    }

    /**
     * Test method for
     * {@link VTNFlowDatabase#create(VTNManagerImpl)},
     * {@link VTNFlowDatabase#VTNFlowDatabase(java.lang.String)}.
     */
    @Test
    public void testCreate() {
        String tname = "testcreate";
        String tnameOther = "other";
        VTNFlowDatabase fdb = new VTNFlowDatabase("testcreate");
        VTNFlowDatabase fdbOther = new VTNFlowDatabase("other");
        VTNFlow testFlow0 = fdb.create(vtnMgr);
        VTNFlow testFlow1 = fdb.create(vtnMgr);
        VTNFlow otherFlow0 = fdbOther.create(vtnMgr);

        assertNotNull(testFlow0);
        assertNotNull(testFlow1);
        assertNotNull(otherFlow0);

        assertEquals(tname, testFlow0.getGroupId().getTenantName());
        assertEquals(tname, testFlow1.getGroupId().getTenantName());
        assertEquals(tnameOther, otherFlow0.getGroupId().getTenantName());

        assertFalse(testFlow0.getGroupId().equals(testFlow1.getGroupId()));
        assertFalse(testFlow0.getGroupId().equals(otherFlow0.getGroupId()));
    }

    /**
     * Test method for
     * {@link VTNFlowDatabase#install(VTNManagerImpl, VTNFlow)}.
     */
    @Test
    public void testInstall() {
        VTNFlowDatabase fdb = new VTNFlowDatabase("test");
        VTNFlow flow = fdb.create(vtnMgr);

        Node node0 = NodeCreator.createOFNode(Long.valueOf(0L));
        Node node1 = NodeCreator.createOFNode(Long.valueOf(1L));
        Set<Short> portIds = new HashSet<Short>(createShorts((short) 10, (short) 2,
                                                             false));
        Set<NodeConnector> ncset0
            = NodeConnectorCreator.createOFNodeConnectorSet(portIds, node0);
        Set<NodeConnector> ncset1
            = NodeConnectorCreator.createOFNodeConnectorSet(portIds, node1);

        int pri = 1;

        // add one flowEntry.
        Iterator<NodeConnector> it = ncset0.iterator();
        NodeConnector innc = it.next();
        NodeConnector outnc = it.next();
        addFlowEntry(vtnMgr, flow, innc, (short) 0, outnc, pri);
        fdb.install(vtnMgr, flow);
        flushFlowTasks();

        ConcurrentMap<FlowGroupId, VTNFlow> db = vtnMgr.getFlowDB();
        VTNFlow rflow = db.get(flow.getGroupId());
        assertEquals(flow, rflow);
        assertEquals(1, stubObj.getFlowEntries().size());

        fdb.clear(vtnMgr);
        flushFlowTasks();
        assertEquals(0, db.values().size());

        // add 2 flowEntries.
        it = ncset1.iterator();
        outnc = it.next();
        innc = it.next();
        flow = addFlowEntry(vtnMgr, flow, innc, (short) 0, outnc, pri);

        fdb.install(vtnMgr, flow);
        flushFlowTasks();

        rflow = db.get(flow.getGroupId());
        assertEquals(flow, rflow);
        assertEquals(2, stubObj.getFlowEntries().size());

        // add Flow which have same groupID.
        VTNFlow conflictFlow = new VTNFlow(rflow.getGroupId());
        fdb.install(vtnMgr, conflictFlow);
        flushFlowTasks();
        VTNFlow flowAfter = db.get(rflow.getGroupId());
        assertEquals(rflow, flowAfter);

        // add Flow which have different groupID and same ingress flow.
        conflictFlow = fdb.create(vtnMgr);
        it = ncset0.iterator();
        innc = it.next();
        outnc = it.next();
        addFlowEntry(vtnMgr, conflictFlow, innc, (short) 0, outnc, pri);

        it = ncset1.iterator();
        outnc = it.next();
        innc = it.next();
        Match match = new Match();
        match.setField(MatchType.IN_PORT, innc);
        match.setField(MatchType.DL_SRC,
                       new byte[] {0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
        ActionList actions = new ActionList(innc.getNode());
        actions.addOutput(outnc);
        conflictFlow.addFlow(vtnMgr, match, actions, pri);

        fdb.install(vtnMgr, conflictFlow);
        flushFlowTasks();

        flowAfter = db.get(conflictFlow.getGroupId());
        assertNull(flowAfter);

        // clear all entry.
        fdb.clear(vtnMgr);
        flushFlowTasks();
        assertEquals(0, db.values().size());

        // clear all entry (with no entry).
        fdb.clear(vtnMgr);
        flushFlowTasks();
        assertEquals(0, db.values().size());

        // in case VTNManager is not worked.
        stopVTNManager(false);
        fdb.install(vtnMgr, conflictFlow);
        assertEquals(0, db.values().size());

    }

    /**
     * Test method for
     * {@link VTNFlowDatabase#flowRemoved(VTNManagerImpl, FlowEntry)},
     * {@link VTNFlowDatabase#flowRemoved(VTNManagerImpl, FlowGroupId)}.
     */
    @Test
    public void testFlowRemovedFlowEntryAndFlowGroupId() {
        VTNFlowDatabase fdb = new VTNFlowDatabase("test");

        Node node0 = NodeCreator.createOFNode(Long.valueOf(0L));
        Node node1 = NodeCreator.createOFNode(Long.valueOf(1L));
        Set<Short> portIds0 = new HashSet<Short>(createShorts((short) 10, (short) 4,
                                                              false));
        Set<NodeConnector> ncSet0
            = NodeConnectorCreator.createOFNodeConnectorSet(portIds0, node0);

        int pri = 1;

        Set<VTNFlow> flows = new HashSet<VTNFlow>();
        for (NodeConnector innc0 : ncSet0) {
            VTNFlow flow = fdb.create(vtnMgr);
            NodeConnector outnc
                = NodeConnectorCreator.createOFNodeConnector(Short.valueOf((short) 15),
                                                             innc0.getNode());
            addFlowEntry(vtnMgr, flow, innc0, (short) 0, outnc, pri);

            NodeConnector innc1
                = NodeConnectorCreator.createOFNodeConnector((Short) innc0.getID(),
                                                             node1);
            outnc = NodeConnectorCreator.createOFNodeConnector(Short.valueOf((short) 15),
                                                               innc1.getNode());
            addFlowEntry(vtnMgr, flow, innc1, (short) 0, outnc, pri);

            fdb.install(vtnMgr, flow);
            flushFlowTasks();

            flows.add(flow);
        }

        int numFlows = flows.size();
        ConcurrentMap<FlowGroupId, VTNFlow> db = vtnMgr.getFlowDB();
        assertEquals(numFlows, db.values().size());
        assertEquals(numFlows * 2, stubObj.getFlowEntries().size());

        // test flowRemoved(VTNMangerImpl, FlowEntry)
        for (VTNFlow flow : flows) {
            int i = 0;
            for (FlowEntry ent : flow.getFlowEntries()) {
                String emsg = ent.toString();
                fdb.flowRemoved(vtnMgr, ent);
                flushFlowTasks();

                if (i == 0) {
                    VTNFlow regFlow = db.get(flow.getGroupId());
                    assertNull(emsg, regFlow);
                    assertEquals(emsg, numFlows - 1, db.values().size());
                    assertEquals(emsg, numFlows * 2 - 1,
                                 stubObj.getFlowEntries().size());

                    // because florwRemoved(VTNManagerImpl, FlowEntry) is invoked
                    // when VTN flow was expired, this invoked
                    // after FlowEnry have already been removed.
                    // In this test case need to remove FlowEntry in DB of stub.
                    stubObj.uninstallFlowEntry(ent);

                    Set<VTNFlow> revert = new HashSet<VTNFlow>();
                    revert.add(flow);
                    revertFlowEntries(vtnMgr, fdb, revert, numFlows,
                                      numFlows * 2);
                } else {
                    VTNFlow regFlow = db.get(flow.getGroupId());
                    assertNotNull(emsg, regFlow);
                    assertEquals(emsg, numFlows, db.values().size());
                    assertEquals(emsg, numFlows * 2,
                                 stubObj.getFlowEntries().size());
                }

                i++;
            }
        }

        // specify null to FlowEntry.
        FlowEntry fent = null;
        fdb.flowRemoved(vtnMgr, fent);
        flushFlowTasks();

        assertEquals(numFlows, db.values().size());
        assertEquals(numFlows * 2, stubObj.getFlowEntries().size());

        // check flowRemoved(VTNMangerImpl, FlowGroupId)
        for (VTNFlow flow : flows) {
            fdb.flowRemoved(vtnMgr, flow.getGroupId());
            flushFlowTasks();

            // flowRemoved(VTNManagerImpl, FlowGroupID) is invoked
            // from remove cluster node.
            // this case flowEntry in stub is removed by remove cluster node.
            // so that in this test case FlowEntry still exist in DB.
            VTNFlow regFlow = db.get(flow.getGroupId());
            assertNotNull(regFlow);
            assertEquals(numFlows, db.values().size());
            assertEquals(numFlows * 2, stubObj.getFlowEntries().size());

            // need to remove FlowEntry in DB of stub before revert Flow.
            for (FlowEntry ent : flow.getFlowEntries()) {
                stubObj.uninstallFlowEntry(ent);
            }

            Set<VTNFlow> revert = new HashSet<VTNFlow>();
            revert.add(flow);
            revertFlowEntries(vtnMgr, fdb, revert, numFlows, numFlows * 2);
        }

        // specify null to FlowEntry.
        FlowGroupId gid = null;
        fdb.flowRemoved(vtnMgr, gid);
        flushFlowTasks();

        assertEquals(numFlows, db.values().size());
        assertEquals(numFlows * 2, stubObj.getFlowEntries().size());

        // after clear.
        fdb.clear(vtnMgr);
        flushFlowTasks();

        VTNFlow flow = flows.iterator().next();
        List<FlowEntry> flowEntries = flow.getFlowEntries();
        fdb.flowRemoved(vtnMgr, flowEntries.get(0));
        flushFlowTasks();
        assertEquals(0, db.values().size());
        assertEquals(0, stubObj.getFlowEntries().size());

        fdb.flowRemoved(vtnMgr, flow.getGroupId());
        flushFlowTasks();
        assertEquals(0, db.values().size());
        assertEquals(0, stubObj.getFlowEntries().size());
    }

    /**
     * Test method for
     * {@link VTNFlowDatabase#removeFlows(VTNManagerImpl, Node, boolean)},
     * {@link VTNFlowDatabase#removeFlows(VTNManagerImpl, Node)}.
     */
    @Test
    public void testRemoveFlowsNode() {
        VTNFlowDatabase fdb = new VTNFlowDatabase("test");

        Node node0 = NodeCreator.createOFNode(Long.valueOf(0L));
        Node node1 = NodeCreator.createOFNode(Long.valueOf(1L));
        Node node2 = NodeCreator.createOFNode(Long.valueOf(2L));
        Set<Short> portIds0 = new HashSet<Short>(createShorts((short) 10, (short) 5,
                                                              false));
        Set<Short> portIds1 = new HashSet<Short>(createShorts((short) 10, (short) 4,
                                                              false));
        Set<Short> portIds2 = new HashSet<Short>(createShorts((short) 10, (short) 3,
                                                              false));
        Set<NodeConnector> ncset0
            = NodeConnectorCreator.createOFNodeConnectorSet(portIds0, node0);
        Set<NodeConnector> ncset1
            = NodeConnectorCreator.createOFNodeConnectorSet(portIds1, node1);
        Set<NodeConnector> ncset2
            = NodeConnectorCreator.createOFNodeConnectorSet(portIds2, node2);

        int pri = 1;

        // add flowEntry as test parameter.
        VTNFlow flow0 = fdb.create(vtnMgr);
        NodeConnector outnc
            = NodeConnectorCreator.createOFNodeConnector(Short.valueOf((short) 15),
                                                         node0);
        for (NodeConnector innc : ncset0) {
            addFlowEntry(vtnMgr, flow0, innc, (short) 0, outnc, pri);
        }
        fdb.install(vtnMgr, flow0);
        flushFlowTasks();

        VTNFlow flow1 = fdb.create(vtnMgr);
        outnc = NodeConnectorCreator.createOFNodeConnector(Short.valueOf((short) 15),
                                                           node1);
        for (NodeConnector innc : ncset1) {
            addFlowEntry(vtnMgr, flow1, innc, (short) 0, outnc, pri);
        }
        fdb.install(vtnMgr, flow1);
        flushFlowTasks();

        VTNFlow flow2 = fdb.create(vtnMgr);
        outnc = NodeConnectorCreator.createOFNodeConnector(Short.valueOf((short) 15),
                                                           node2);
        for (NodeConnector innc : ncset2) {
           addFlowEntry(vtnMgr, flow2, innc, (short) 0, outnc, pri);
        }
        fdb.install(vtnMgr, flow2);
        flushFlowTasks();

        int numEntry = ncset0.size() + ncset1.size() + ncset2.size();
        ConcurrentMap<FlowGroupId, VTNFlow> db = vtnMgr.getFlowDB();
        assertEquals(3, db.values().size());
        assertEquals(numEntry, stubObj.getFlowEntries().size());

        // remove Flows related to node1.
        // flow entries in switch are also removed.
        FlowRemoveTask task = fdb.removeFlows(vtnMgr, node1, true);
        assertNotNull(task);
        flushFlowTasks();

        assertEquals(ncset0.size() + ncset2.size(),
                     stubObj.getFlowEntries().size());

        VTNFlow rflow = db.get(flow0.getGroupId());
        assertEquals(flow0, rflow);
        rflow = db.get(flow1.getGroupId());
        assertEquals(null, rflow);
        rflow = db.get(flow2.getGroupId());
        assertEquals(flow2, rflow);

        // revert flow1
        fdb.install(vtnMgr, flow1);
        flushFlowTasks();
        assertEquals(ncset0.size() + ncset1.size() + ncset2.size(),
                     stubObj.getFlowEntries().size());

        // remove Flows related to node2.
        // flow entries in switch aren't removed.
        task = fdb.removeFlows(vtnMgr, node2);
        assertNotNull(task);
        flushFlowTasks();

        assertEquals(ncset0.size() + ncset1.size() + ncset2.size(),
                     stubObj.getFlowEntries().size());

        rflow = db.get(flow0.getGroupId());
        assertEquals(flow0, rflow);
        rflow = db.get(flow1.getGroupId());
        assertEquals(flow1, rflow);
        rflow = db.get(flow2.getGroupId());
        assertEquals(null, rflow);

        // specify node which already removed.
        task = fdb.removeFlows(vtnMgr, node2);
        flushFlowTasks();
        assertNull(task);

        fdb.install(vtnMgr, flow2);
        flushFlowTasks();

        // in case there are no flow entry.
        fdb.clear(vtnMgr);
        flushFlowTasks();

        task = fdb.removeFlows(vtnMgr, node0);
        assertNull(task);
        flushFlowTasks();
        assertEquals(0, stubObj.getFlowEntries().size());

    }

    /**
     * Test method for
     * {@link VTNFlowDatabase#removeFlows(VTNManagerImpl, NodeConnector)}.
     */
    @Test
    public void testRemoveFlowsNodeConnector() {
        VTNFlowDatabase fdb = new VTNFlowDatabase("test");

        Node node0 = NodeCreator.createOFNode(Long.valueOf(0L));
        Node node1 = NodeCreator.createOFNode(Long.valueOf(1L));
        Node node2 = NodeCreator.createOFNode(Long.valueOf(2L));
        Set<Short> portIds0 = new HashSet<Short>(createShorts((short) 10, (short) 5,
                                                              false));
        Set<Short> portIds1 = new HashSet<Short>(createShorts((short) 10, (short) 4,
                                                              false));
        Set<Short> portIds2 = new HashSet<Short>(createShorts((short) 10, (short) 3,
                                                              false));
        Set<NodeConnector> ncset0
            = NodeConnectorCreator.createOFNodeConnectorSet(portIds0, node0);
        Set<NodeConnector> ncset1
            = NodeConnectorCreator.createOFNodeConnectorSet(portIds1, node1);
        Set<NodeConnector> ncset2
            = NodeConnectorCreator.createOFNodeConnectorSet(portIds2, node2);

        int pri = 1;

        // add flow entries.
        Set<VTNFlow> flows = new HashSet<VTNFlow>();
        int i = 0;
        Iterator<NodeConnector> incset1 = ncset1.iterator();
        Iterator<NodeConnector> incset2 = ncset2.iterator();
        for (NodeConnector innc : ncset0) {
            VTNFlow flow = fdb.create(vtnMgr);
            NodeConnector outnc
                = NodeConnectorCreator.createOFNodeConnector(Short.valueOf((short) 15),
                                                             node0);
            addFlowEntry(vtnMgr, flow, innc, (short) 0, outnc, pri);

            if(i > 0) {
                innc = incset1.next();
                outnc = NodeConnectorCreator.createOFNodeConnector(Short.valueOf((short) 15),
                                                                   node1);
                addFlowEntry(vtnMgr, flow, innc, (short) 0, outnc, pri);
            }
            if (i > 1) {
                innc = incset2.next();
                outnc = NodeConnectorCreator.createOFNodeConnector(Short.valueOf((short) 15),
                                                                   node2);
                addFlowEntry(vtnMgr, flow, innc, (short) 0, outnc, pri);
            }

            fdb.install(vtnMgr, flow);
            flushFlowTasks();
            flows.add(flow);

            if (++i > 2) {
                break;
            }
        }
        assertEquals(6, stubObj.getFlowEntries().size());

        // remove by specifying node connector.
        NodeConnector removePort
            = NodeConnectorCreator.createOFNodeConnector(Short.valueOf((short) 15),
                                                         node2);
        FlowRemoveTask task = fdb.removeFlows(vtnMgr, removePort);
        assertNotNull(task);
        flushFlowTasks();

        assertEquals(3, stubObj.getFlowEntries().size());
        ConcurrentMap<FlowGroupId, VTNFlow> db = vtnMgr.getFlowDB();
        for (VTNFlow flow : flows) {
            VTNFlow rflow = db.get(flow.getGroupId());
            if (flow.getFlowNodes().contains(node2)) {
                rflow = db.get(flow.getGroupId());
                assertEquals(null, rflow);
            } else {
                rflow = db.get(flow.getGroupId());
                assertEquals(flow, rflow);
            }
        }

        // specify port which was already removed.
        task = fdb.removeFlows(vtnMgr, removePort);
        assertNull(task);
        flushFlowTasks();
        assertEquals(3, stubObj.getFlowEntries().size());

        // in case there are no flow entry.
        fdb.clear(vtnMgr);
        flushFlowTasks();

        task = fdb.removeFlows(vtnMgr, removePort);
        assertNull(task);
        flushFlowTasks();
        assertEquals(0, stubObj.getFlowEntries().size());
    }

    /**
     * Test method for
     * {@link VTNFlowDatabase#removeFlows(VTNManagerImpl, VTenantPath)},
     * {@link VTNFlowDatabase#removeFlows(VTNManagerImpl, MacVlan)}.
     */
    @Test
    public void testRemoveFlowsVTenantPathAndMacVlan() {
        VTNFlowDatabase fdb = new VTNFlowDatabase("test");

        Node node0 = NodeCreator.createOFNode(Long.valueOf(0L));
        Set<Short> portIds0 = new HashSet<Short>(createShorts((short) 10, (short) 5,
                                                              false));
        Set<NodeConnector> ncset0
            = NodeConnectorCreator.createOFNodeConnectorSet(portIds0, node0);

        int pri = 1;

        // add flow entries.
        Set<VTNFlow> flows = new HashSet<VTNFlow>();
        NodeConnector outnc
            = NodeConnectorCreator.createOFNodeConnector(Short.valueOf((short) 15),
                                                         node0);
        String tname = "tenant";
        String bname = "bridge";
        String ifname = "interface";
        VTenantPath tpath = new VTenantPath(tname);
        VBridgePath bpath = new VBridgePath(tname, bname);
        VBridgeIfPath bifpath = new VBridgeIfPath(tname, bname, ifname);
        Set<VTenantPath> pathSet = new HashSet<VTenantPath>();
        MacVlan[] macVlans = new MacVlan[] {
                new MacVlan(new byte[] {0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
                            (short) 0),
                new MacVlan(new byte[] {0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
                            (short) 4095),
                new MacVlan(new byte[] {0x00, 0x00, 0x00, 0x00, 0x00, 0x01},
                            (short) 0),
        };

        int numEntries = 0;
        for (NodeConnector innc : ncset0) {
            VTNFlow flow = fdb.create(vtnMgr);
            Match match = new Match();
            match.setField(MatchType.IN_PORT, innc);
            ActionList actions = new ActionList(innc.getNode());
            actions.addOutput(outnc);
            flow.addFlow(vtnMgr, match, actions, pri);

            MacVlan macVlan = null;
            if (numEntries == 0) {
                pathSet.add(tpath);
                macVlan = macVlans[0];
            } else if (numEntries == 1) {
                pathSet.add(bpath);
                macVlan = macVlans[1];
            } else if (numEntries == 2) {
                pathSet.add(bifpath);
                macVlan = macVlans[2];
            } else if (numEntries >= 3) {
                // when numEntries >= 0, add empty pathSet and add null to macVlan.
                pathSet.clear();
            }
            flow.addDependency(pathSet);
            flow.addDependency(macVlan);

            fdb.install(vtnMgr, flow);
            flushFlowTasks();
            flows.add(flow);
            numEntries++;
        }

        ConcurrentMap<FlowGroupId, VTNFlow> db = vtnMgr.getFlowDB();
        assertEquals(numEntries, db.values().size());
        assertEquals(numEntries, stubObj.getFlowEntries().size());

        // specify VBridgeIfPath
        FlowRemoveTask task = fdb.removeFlows(vtnMgr, bifpath);
        assertNotNull(task);
        flushFlowTasks();
        assertEquals(numEntries - 1, stubObj.getFlowEntries().size());
        Set<VTNFlow> revertFlows = new HashSet<VTNFlow>();
        revertFlows = checkFlowDBEntriesVTenantPath(vtnMgr, fdb, flows, bifpath);

        revertFlowEntries(vtnMgr, fdb, revertFlows, numEntries, numEntries);

        // specify VBridge
        task = fdb.removeFlows(vtnMgr, bpath);
        assertNotNull(task);
        flushFlowTasks();
        assertEquals(numEntries - 2, stubObj.getFlowEntries().size());
        revertFlows.clear();
        revertFlows = checkFlowDBEntriesVTenantPath(vtnMgr, fdb, flows, bpath);

        revertFlowEntries(vtnMgr, fdb, revertFlows, numEntries, numEntries);

        // specify VTenant
        task = fdb.removeFlows(vtnMgr, tpath);
        assertNotNull(task);
        flushFlowTasks();
        assertEquals(numEntries - 3, stubObj.getFlowEntries().size());
        revertFlows.clear();
        revertFlows = checkFlowDBEntriesVTenantPath(vtnMgr, fdb, flows, tpath);

        task = fdb.removeFlows(vtnMgr, tpath);
        assertNull(task);
        flushFlowTasks();
        assertEquals(numEntries - 3, stubObj.getFlowEntries().size());

        revertFlowEntries(vtnMgr, fdb, revertFlows, numEntries, numEntries);

        // not match tenant
        VTenantPath notMatchPath = new VTenantPath("notmatch");
        task = fdb.removeFlows(vtnMgr, notMatchPath);
        assertNull(task);
        flushFlowTasks();
        assertEquals(numEntries, stubObj.getFlowEntries().size());
        revertFlows.clear();
        revertFlows = checkFlowDBEntriesVTenantPath(vtnMgr, fdb, flows, notMatchPath);

        // for MacVlan
        for (MacVlan macVlan : macVlans) {
            String emsg = macVlan.toString();
            task = fdb.removeFlows(vtnMgr, macVlan);
            assertNotNull(emsg, task);
            flushFlowTasks();
            assertEquals(emsg,
                         numEntries - 1, stubObj.getFlowEntries().size());
            revertFlows.clear();
            revertFlows = checkFlowDBEntriesMacVlan(vtnMgr, fdb, flows, macVlan);

            revertFlowEntries(vtnMgr, fdb, revertFlows, numEntries, numEntries);
        }

        // not match MacVlan
        MacVlan notMatchMv = new MacVlan(new byte[] {(byte) 0x00, (byte) 0x00, (byte) 0x00,
                                                     (byte) 0xff, (byte) 0xff, (byte) 0xff},
                                         (short) 1);
        task = fdb.removeFlows(vtnMgr, notMatchMv);
        assertNull(task);
        flushFlowTasks();
        assertEquals(numEntries, stubObj.getFlowEntries().size());
        revertFlows.clear();
        revertFlows = checkFlowDBEntriesMacVlan(vtnMgr, fdb, flows, notMatchMv);

        // in case there are no flow entry.
        fdb.clear(vtnMgr);
        flushFlowTasks();

        task = fdb.removeFlows(vtnMgr, tpath);
        assertNull(task);
        flushFlowTasks();
        assertEquals(0, stubObj.getFlowEntries().size());

        task = fdb.removeFlows(vtnMgr, macVlans[0]);
        assertNull(task);
        flushFlowTasks();
        assertEquals(0, stubObj.getFlowEntries().size());
    }

    /**
     * Check entries in flowDB
     * after {@link removeFlows(VTNManager, VTenantPath)} was invoked.
     *
     * @param mgr   VTNManager service.
     * @param fdb   VTNFlowDatabase.
     * @param flows flows initially installed.
     * @param rpath VTenantPath.
     *              it is expected that a FlowEntry dependesOn this was removed.
     * @return Set of VTNFlow which was removed.
     */
    private Set<VTNFlow> checkFlowDBEntriesVTenantPath (VTNManagerImpl mgr,
            VTNFlowDatabase fdb, Set<VTNFlow> flows, VTenantPath rpath) {
        Set<VTNFlow> revertFlows = new HashSet<VTNFlow>();
        ConcurrentMap<FlowGroupId, VTNFlow> db = vtnMgr.getFlowDB();

        for (VTNFlow flow : flows) {
            VTNFlow rflow = db.get(flow.getGroupId());
            if (flow.dependsOn(rpath)) {
                rflow = db.get(flow.getGroupId());
                assertEquals(null, rflow);
                revertFlows.add(flow);
            } else {
                rflow = db.get(flow.getGroupId());
                assertEquals(flow, rflow);
            }
        }

        return revertFlows;
    }

    /**
     * Check Flow entries in flowDB
     * after {@link removeFlows(VTNManager, MacVlan)} was invoked.
     *
     * @param mgr       VTNManager service.
     * @param fdb       VTNFlowDatabase.
     * @param flows     flows initially installed.
     * @param macVlan   MacVlan.
     *                  it is expected that a FlowEntry dependesOn this was removed.
     * @return Set of VTNFlow which was removed.
     */
    private Set<VTNFlow> checkFlowDBEntriesMacVlan (VTNManagerImpl mgr,
            VTNFlowDatabase fdb, Set<VTNFlow> flows, MacVlan macVlan) {
        Set<VTNFlow> revertFlows = new HashSet<VTNFlow>();
        ConcurrentMap<FlowGroupId, VTNFlow> db = vtnMgr.getFlowDB();

        for (VTNFlow flow : flows) {
            VTNFlow rflow = db.get(flow.getGroupId());
            if (flow.dependsOn(macVlan)) {
                rflow = db.get(flow.getGroupId());
                assertEquals(null, rflow);
                revertFlows.add(flow);
            } else {
                rflow = db.get(flow.getGroupId());
                assertEquals(flow, rflow);
            }
        }

        return revertFlows;
    }

    /**
     * Install FlowEntry which is specified.
     *
     * @param mgr           VTN Manager service
     * @param fdb           VTNFlowDatabase.
     * @param revertFlows   VTNFlows which is installed.
     * @param numFlows      The expected number of VTNFlows after install.
     * @param numEntries    The expected number of FlowEntries after install.
     */
    private void revertFlowEntries (VTNManagerImpl mgr, VTNFlowDatabase fdb,
                                    Set<VTNFlow> revertFlows, int numFlows,
                                    int numEntries) {
        ConcurrentMap<FlowGroupId, VTNFlow> db = vtnMgr.getFlowDB();
        for (VTNFlow flow : revertFlows) {
            fdb.install(vtnMgr, flow);
            flushFlowTasks();
        }
        assertEquals(numFlows, db.values().size());
        assertEquals(numEntries, stubObj.getFlowEntries().size());

    }

    /**
     * Test method for {@link VTNFlowDatabase#removeFlows(VTNManagerImpl, List)}.
     */
    @Test
    public void testRemoveFlowsListOfVTNFlow() {
        VTNFlowDatabase fdb = new VTNFlowDatabase("test");

        Node node0 = NodeCreator.createOFNode(Long.valueOf(0L));
        Set<Short> portIds0 = new HashSet<Short>(createShorts((short) 10, (short) 5,
                                                              false));
        Set<NodeConnector> ncset0
            = NodeConnectorCreator.createOFNodeConnectorSet(portIds0, node0);

        int pri = 1;

        // add flowEntry as test parameter.
        Set<VTNFlow> flows = new HashSet<VTNFlow>();
        NodeConnector outnc
            = NodeConnectorCreator.createOFNodeConnector(Short.valueOf((short) 15),
                                                         node0);
        for (NodeConnector innc : ncset0) {
            VTNFlow flow = fdb.create(vtnMgr);
            addFlowEntry(vtnMgr, flow, innc, (short) 0, outnc, pri);

            fdb.install(vtnMgr, flow);
            flushFlowTasks();

            flows.add(flow);
        }

        ConcurrentMap<FlowGroupId, VTNFlow> db = vtnMgr.getFlowDB();
        assertEquals(flows.size(), db.values().size());
        assertEquals(flows.size(), stubObj.getFlowEntries().size());

        List<VTNFlow> removeFlows = new ArrayList<VTNFlow>();
        FlowRemoveTask task = fdb.removeFlows(vtnMgr, removeFlows);
        assertNull(task);
        assertEquals(flows.size(), db.values().size());
        assertEquals(flows.size(), stubObj.getFlowEntries().size());

        int numTotal = flows.size();
        for (VTNFlow flow : flows) {
            removeFlows.add(flow);
            task = fdb.removeFlows(vtnMgr, removeFlows);
            assertNotNull(task);
            flushFlowTasks();
            assertEquals("(numRemove)" + removeFlows.size() + ",(flow)" + flow.toString(),
                         numTotal - removeFlows.size(), db.values().size());
            assertEquals("(numRemove)" + removeFlows.size() + ",(flow)" + flow.toString(),
                         numTotal - removeFlows.size(), stubObj.getFlowEntries().size());

            checkFlowDBEntriesListOfVTNFlow(vtnMgr, fdb, flows, removeFlows);
            Set<VTNFlow> removeSets = new HashSet<VTNFlow>(removeFlows);
            revertFlowEntries(vtnMgr, fdb, removeSets, numTotal, numTotal);
        }

        // specify non match flow
        VTNFlow flowNew = new VTNFlow(new FlowGroupId("test_1"));
        NodeConnector innc
            = NodeConnectorCreator.createOFNodeConnector(Short.valueOf((short)1),
                                                         node0);

        addFlowEntry(vtnMgr, flowNew, innc, (short) 0, outnc, pri);

        List<VTNFlow> flowsNew = new ArrayList<VTNFlow>();
        flowsNew.add(flowNew);

        int preSize = db.values().size();
        int preEntries = stubObj.getFlowEntries().size();

        task = fdb.removeFlows(vtnMgr, flowsNew);
        assertNull(task);
        flushFlowTasks();

        assertEquals(preSize, db.values().size());
        assertEquals(preEntries, stubObj.getFlowEntries().size());

        // in case there are no flow entry.
        fdb.clear(vtnMgr);
        flushFlowTasks();

        task = fdb.removeFlows(vtnMgr, removeFlows);
        assertNull(task);
        flushFlowTasks();
        assertEquals(0, db.values().size());
        assertEquals(0, stubObj.getFlowEntries().size());
    }

    /**
     * Check Flow entries in flowDB
     * after {@link removeFlows(VTNManager, List<VTNFlow>)} was invoked.
     *
     * @param mgr           VTNManager service.
     * @param fdb           VTNFlowDatabase.
     * @param flows         flows initially installed.
     * @param removeFlows   List of VTNFlows which was removed.
     */
    private void checkFlowDBEntriesListOfVTNFlow(VTNManagerImpl mgr,
                                                 VTNFlowDatabase fdb,
                                                 Set<VTNFlow> flows,
                                                 List<VTNFlow> removeFlows) {
        ConcurrentMap<FlowGroupId, VTNFlow> db = vtnMgr.getFlowDB();

        for (VTNFlow flow : flows) {
            VTNFlow rflow = db.get(flow.getGroupId());
            if (removeFlows.contains(flow)) {
                rflow = db.get(flow.getGroupId());
                assertEquals(null, rflow);
            } else {
                rflow = db.get(flow.getGroupId());
                assertEquals(flow, rflow);
            }
        }
    }

    /**
     * Test method for
     * {@link VTNFlowDatabase#containsIngressFlow(FlowEntry)}.
     */
    @Test
    public void testContainsIngressFlow() {
        VTNFlowDatabase fdb = new VTNFlowDatabase("test");

        Node node0 = NodeCreator.createOFNode(Long.valueOf(0L));
        Node node1 = NodeCreator.createOFNode(Long.valueOf(1L));
        Set<Short> portIds0 = new HashSet<Short>(createShorts((short) 10, (short) 5,
                                                              false));
        Set<Short> portIds1 = new HashSet<Short>(createShorts((short) 10, (short) 5,
                                                              false));
        Set<NodeConnector> ncSet0
            = NodeConnectorCreator.createOFNodeConnectorSet(portIds0, node0);
        Set<NodeConnector> ncSet1
            = NodeConnectorCreator.createOFNodeConnectorSet(portIds1, node1);
        Set<Set<NodeConnector>> ncSets = new HashSet<Set<NodeConnector>>();
        ncSets.add(ncSet0);
        ncSets.add(ncSet1);

        int pri = 1;

        for (Set<NodeConnector> incSet : ncSets) {
            Set<NodeConnector> encSet = null;
            if (incSet.equals(ncSet0)) {
                encSet = ncSet1;
            } else {
                encSet = ncSet0;
            }

            // add flowEntry as test parameter.
            Set<VTNFlow> flows = new HashSet<VTNFlow>();
            Iterator<NodeConnector> iNcset1 = encSet.iterator();
            for (NodeConnector innc : incSet) {
                VTNFlow flow = fdb.create(vtnMgr);
                NodeConnector outnc
                    = NodeConnectorCreator.createOFNodeConnector(Short.valueOf((short) 15),
                                                                 innc.getNode());
                addFlowEntry(vtnMgr, flow, innc, (short) 0, outnc, pri);

                if (!iNcset1.hasNext()) {
                    break;
                }
                NodeConnector innc1 = iNcset1.next();
                outnc = NodeConnectorCreator.createOFNodeConnector(Short.valueOf((short) 15),
                                                                   innc1.getNode());
                addFlowEntry(vtnMgr, flow, innc1, (short) 0, outnc, pri);

                fdb.install(vtnMgr, flow);
                flushFlowTasks();

                flows.add(flow);
            }

            for (VTNFlow flow : flows) {
                for (FlowEntry ent : flow.getFlowEntries()) {
                    if (ent.getNode().equals(incSet.iterator().next().getNode())) {
                        assertTrue(ent.toString(), fdb.containsIngressFlow(ent));
                    } else {
                        assertFalse(ent.toString(), fdb.containsIngressFlow(ent));
                    }
                }
            }

            fdb.clear(vtnMgr);

            // call after cleared.
            FlowEntry ent = flows.iterator().next().getFlowEntries().get(0);
            assertFalse(fdb.containsIngressFlow(ent));
        }
    }


    // private methods

    /**
     * Add FlowEntry to VTNFlow.
     *
     * @param   flow        A {@link VTNFlow}.
     * @param   inPort      A ingress {@link NodeConector}.
     * @param   inVlan      A incoming VLAN ID.
     * @param   outPort     A outgoing {@link NodeConnector}.
     * @param   priority    A priority of FlowEntry.
     * @return {@link VTNFlow}.
     */
    private VTNFlow addFlowEntry(VTNManagerImpl mgr, VTNFlow flow,
            NodeConnector inPort, short inVlan, NodeConnector outPort,
            int priority) {
        Match match = new Match();
        match.setField(MatchType.IN_PORT, inPort);
        match.setField(MatchType.DL_VLAN, inVlan);
        ActionList actions = new ActionList(outPort.getNode());
        actions.addOutput(outPort);
        flow.addFlow(mgr, match, actions, priority);

        return flow;
    }
}
