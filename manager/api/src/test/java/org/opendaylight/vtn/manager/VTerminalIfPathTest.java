/*
 * Copyright (c) 2014 NEC Corporation
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vtn.manager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.Test;

import org.opendaylight.vtn.manager.flow.filter.RedirectFilter;

import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.core.NodeConnector;
import org.opendaylight.controller.sal.core.UpdateType;
import org.opendaylight.controller.sal.utils.NodeConnectorCreator;
import org.opendaylight.controller.sal.utils.NodeCreator;

/**
 * JUnit test for {@link VTerminalIfPath}.
 */
public class VTerminalIfPathTest extends TestBase {
    /**
     * Test case for getter methods.
     */
    @Test
    public void testGetter() {
        for (String tname: createStrings("tenant")) {
            for (String vtname: createStrings("term")) {
                VTerminalPath vtpath = new VTerminalPath(tname, vtname);
                for (String iname: createStrings("ifname")) {
                    VTerminalIfPath path =
                        new VTerminalIfPath(tname, vtname, iname);
                    assertEquals(tname, path.getTenantName());
                    assertEquals(vtname, path.getTerminalName());
                    assertEquals(iname, path.getInterfaceName());
                    assertEquals("vTerminal-IF", path.getNodeType());
                    checkRedirectFilter(path);

                    path = new VTerminalIfPath(vtpath, iname);
                    assertEquals(tname, path.getTenantName());
                    assertEquals(vtname, path.getTerminalName());
                    assertEquals(iname, path.getInterfaceName());
                    assertEquals("vTerminal-IF", path.getNodeType());
                    checkRedirectFilter(path);

                    VTenantPath clone = path.clone();
                    assertNotSame(clone, path);
                    assertEquals(clone, path);

                    for (String name: new String[]{null, tname + "_new"}) {
                        VTerminalIfPath path1 = path.replaceTenantName(name);
                        assertEquals(name, path1.getTenantName());
                        assertEquals(vtname, path1.getTerminalName());
                        assertEquals(iname, path1.getInterfaceName());
                        assertEquals("vTerminal-IF", path1.getNodeType());
                        checkRedirectFilter(path1);
                    }
                }
            }
        }
    }

    /**
     * Ensure that {@link VTenantPath#contains(VTenantPath)} works.
     */
    @Test
    public void testContains() {
        for (String tname: createStrings("tenant")) {
            for (String vtname: createStrings("term")) {
                for (String iname: createStrings("ifname")) {
                    VTerminalIfPath path =
                        new VTerminalIfPath(tname, vtname, iname);
                    assertFalse(path.contains(null));
                    assertTrue(path.contains(path));

                    String notMatched = "not_matched";
                    containsTest(path, tname, vtname, iname, true);
                    containsTest(path, notMatched, vtname, iname, false);
                    containsTest(path, tname, notMatched, iname, false);
                    containsTest(path, tname, vtname, notMatched, false);
                }
            }
        }
    }

    /**
     * Test case for {@link VTerminalIfPath#vInterfaceChanged(IVTNManagerAware,VInterface,UpdateType)}.
     */
    @Test
    public void testVInterfaceChanged() {
        TestVTNManagerAware listener = new TestVTNManagerAware();
        String iname = "if_1";
        VTerminalIfPath path = new VTerminalIfPath("tenant", "vterm", iname);
        VInterfaceConfig iconf = new VInterfaceConfig("vTerminal 1", null);
        VInterface vif = new VInterface(iname, VNodeState.DOWN,
                                        VNodeState.UNKNOWN, iconf);
        UpdateType type = UpdateType.ADDED;
        List<List<Object>> expected = new ArrayList<List<Object>>();
        expected.add(toList(path, vif, type));
        path.vInterfaceChanged(listener, vif, type);

        vif = new VInterface(iname, VNodeState.UP, VNodeState.UP, iconf);
        type = UpdateType.CHANGED;
        expected.add(toList(path, vif, type));
        path.vInterfaceChanged(listener, vif, type);

        type = UpdateType.REMOVED;
        expected.add(toList(path, vif, type));
        path.vInterfaceChanged(listener, vif, type);

        for (VTNListenerType ltype: VTNListenerType.values()) {
            List<List<Object>> called = listener.getArguments(ltype);

            switch (ltype) {
            case VTERMINAL_IF:
                assertEquals(expected, called);
                break;

            default:
                assertEquals(null, called);
                break;
            }
        }
    }

    /**
     * Test case for {@link VTerminalIfPath#portMapChanged(IVTNManagerAware,PortMap,UpdateType)}.
     */
    @Test
    public void testPortMapChanged() {
        TestVTNManagerAware listener = new TestVTNManagerAware();
        String iname = "if_1";
        VTerminalIfPath path = new VTerminalIfPath("tenant", "vterm", iname);
        Node node = NodeCreator.createOFNode(Long.valueOf(123L));
        NodeConnector port = NodeConnectorCreator.
            createOFNodeConnector(Short.valueOf((short)1), node);
        SwitchPort swport = new SwitchPort("OF", "1");
        PortMapConfig pmconf = new PortMapConfig(node, swport, (short)0);
        PortMap pmap = new PortMap(pmconf, null);
        UpdateType type = UpdateType.ADDED;
        List<List<Object>> expected = new ArrayList<List<Object>>();
        expected.add(toList(path, pmap, type));
        path.portMapChanged(listener, pmap, type);

        pmap = new PortMap(pmconf, port);
        type = UpdateType.CHANGED;
        expected.add(toList(path, pmap, type));
        path.portMapChanged(listener, pmap, type);

        type = UpdateType.REMOVED;
        expected.add(toList(path, pmap, type));
        path.portMapChanged(listener, pmap, type);

        for (VTNListenerType ltype: VTNListenerType.values()) {
            List<List<Object>> called = listener.getArguments(ltype);

            switch (ltype) {
            case PORTMAP_VTERM:
                assertEquals(expected, called);
                break;

            default:
                assertEquals(null, called);
                break;
            }
        }
    }

    /**
     * Test case for {@link VTerminalIfPath#equals(Object)} and
     * {@link VTerminalIfPath#hashCode()}.
     */
    @Test
    public void testEquals() {
        HashSet<Object> set = new HashSet<Object>();
        List<String> tnames = createStrings("tenant");
        List<String> vtnames = createStrings("term");
        List<String> inames = createStrings("ifname");
        for (String tname: tnames) {
            VTenantPath tpath = new VTenantPath(tname);
            for (String vtname: vtnames) {
                VTerminalPath vtpath = new VTerminalPath(tname, vtname);
                VBridgePath bpath = new VBridgePath(tname, vtname);
                for (String iname: inames) {
                    VTerminalIfPath p1 =
                        new VTerminalIfPath(tname, vtname, iname);
                    VTerminalIfPath p2 =
                        new VTerminalIfPath(copy(tname), copy(vtname),
                                            copy(iname));
                    testEquals(set, p1, p2);

                    // An instance of VTenantPath, VBridgePath, VTerminalPath,
                    // and VBridgeIfPath must be treated as different object
                    // even if it has the same path component.
                    VBridgeIfPath bifpath =
                        new VBridgeIfPath(tname, vtname, iname);
                    assertFalse(tpath.equals(p1));
                    assertFalse(p1.equals(tpath));
                    assertFalse(vtpath.equals(p1));
                    assertFalse(p1.equals(vtpath));
                    assertFalse(bpath.equals(p1));
                    assertFalse(p1.equals(bpath));
                    assertFalse(bifpath.equals(p1));
                    assertFalse(p1.equals(bifpath));
                }
            }
        }

        int required = tnames.size() * vtnames.size() * inames.size();
        assertEquals(required, set.size());
    }

    /**
     * Test case for {@link VTerminalIfPath#toString()}.
     */
    @Test
    public void testToString() {
        String prefix = "vTerminal-IF:";
        for (String tname: createStrings("tenant")) {
            for (String vtname: createStrings("term")) {
                for (String iname: createStrings("ifname")) {
                    VTerminalIfPath path =
                        new VTerminalIfPath(tname, vtname, iname);
                    String tn = (tname == null) ? "<null>" : tname;
                    String vn = (vtname == null) ? "<null>" : vtname;
                    String in = (iname == null) ? "<null>" : iname;
                    String required =
                        joinStrings(prefix, null, ".", tn, vn, in);
                    assertEquals(required, path.toString());
                }
            }
        }
    }

    /**
     * Ensure that {@link VTerminalIfPath} is serializable.
     */
    @Test
    public void testSerialize() {
        for (String tname: createStrings("tenant")) {
            for (String vtname: createStrings("term")) {
                for (String iname: createStrings("ifname")) {
                    VTerminalIfPath path =
                        new VTerminalIfPath(tname, vtname, iname);
                    serializeTest(path);
                }
            }
        }
    }

    /**
     * Internal method for {@link #testContains()}.
     *
     * @param path      A {@link VTerminalIfPath} instance to be tested.
     * @param tname     The name of the tenant for test.
     * @param vtname    The name of the vBridge for test.
     * @param iname     The name of the interface for test.
     * @param expected  Expected result.
     */
    private void containsTest(VTerminalIfPath path, String tname,
                              String vtname, String iname, boolean expected) {
        VTenantPath tpath = new VTenantPath(tname);
        assertEquals(false, path.contains(tpath));

        VBridgePath vtpath = new VBridgePath(tpath, vtname);
        assertEquals(false, path.contains(vtpath));

        VTerminalPath tmpath = new VTerminalPath(tpath, vtname);
        assertEquals(false, path.contains(tmpath));

        VBridgeIfPath bipath = new VBridgeIfPath(tname, vtname, iname);
        assertEquals(false, path.contains(bipath));

        VTerminalIfPath ipath = new VTerminalIfPath(tmpath, iname);
        assertEquals(expected, path.contains(ipath));
    }


    /**
     * Ensure that {@link VTerminalIfPath#getRedirectFilter(boolean)} works
     * correctly.
     *
     * @param path  A {@link VTerminalIfPath} instance.
     */
    private void checkRedirectFilter(VTerminalIfPath path) {
        boolean[] bools = {true, false};
        for (boolean b: bools) {
            RedirectFilter rf = path.getRedirectFilter(b);
            assertEquals(path, rf.getDestination());
            assertEquals(b, rf.isOutput());
        }
    }
}
