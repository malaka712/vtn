/*
 * Copyright (c) 2014-2015 NEC Corporation
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vtn.manager.internal.cluster;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.xml.bind.Unmarshaller;

import org.junit.Test;

import org.opendaylight.vtn.manager.VTNException;
import org.opendaylight.vtn.manager.util.ByteUtils;
import org.opendaylight.vtn.manager.util.EtherAddress;

import org.opendaylight.vtn.manager.flow.action.SetDlSrcAction;

import org.opendaylight.vtn.manager.internal.TestBase;

import org.opendaylight.controller.sal.packet.address.EthernetAddress;
import org.opendaylight.controller.sal.utils.StatusCode;

/**
 * JUnit test for {@link SetDlSrcActionImpl}.
 */
public class SetDlSrcActionImplTest extends TestBase {
    /**
     * Test case for getter methods.
     *
     * @throws Exception  An unexpected exception occurred.
     */
    @Test
    public void testGetter() throws Exception {
        for (EthernetAddress eaddr: createEthernetAddresses(false)) {
            byte[] bytes = eaddr.getValue();
            SetDlSrcAction act = new SetDlSrcAction(bytes);
            try {
                SetDlSrcActionImpl impl = new SetDlSrcActionImpl(act);
                assertEquals(act, impl.getFlowAction());
                assertArrayEquals(bytes, impl.getAddress().getBytes());
            } catch (Exception e) {
                unexpected(e);
            }
        }

        // null action.
        try {
            new SetDlSrcActionImpl(null);
            unexpected();
        } catch (VTNException e) {
            assertEquals(StatusCode.BADREQUEST, e.getStatus().getCode());
        }

        // Create invalid MAC addresses.
        ArrayList<byte[]> addrs = new ArrayList<byte[]>();
        addrs.add(null);

        // Invalid length.
        for (int len = 0; len <= 10; len++) {
            if (len != EtherAddress.SIZE) {
                byte[] addr = new byte[len];
                if (len > 0) {
                    addr[0] = (byte)len;
                }
                addrs.add(addr);
            }
        }

        // Broadcast address.
        addrs.add(new byte[]{-1, -1, -1, -1, -1, -1});

        // Multicast addresses.
        for (EthernetAddress eaddr: createEthernetAddresses(false)) {
            byte[] bytes = eaddr.getValue();
            bytes[0] |= (byte)0x1;
            addrs.add(bytes);
        }

        // Zero address.
        addrs.add(new byte[EtherAddress.SIZE]);

        for (byte[] addr: addrs) {
            SetDlSrcAction act = new SetDlSrcAction(addr);
            try {
                new SetDlSrcActionImpl(act);
                unexpected();
            } catch (VTNException e) {
                assertEquals(StatusCode.BADREQUEST, e.getStatus().getCode());
            }
        }

        // Specify invalid MAC address via JAXB.
        String xml = new StringBuilder(XML_DECLARATION).
            append("<setdlsrc address=\"bad MAC address\" />").toString();
        Unmarshaller um = createUnmarshaller(SetDlSrcAction.class);
        SetDlSrcAction act = unmarshal(um, xml, SetDlSrcAction.class);
        try {
            new SetDlSrcActionImpl(act);
            unexpected();
        } catch (VTNException e) {
            assertEquals(StatusCode.BADREQUEST, e.getStatus().getCode());
        }
    }

    /**
     * Test case for {@link SetDlSrcActionImpl#equals(Object)} and
     * {@link SetDlSrcActionImpl#hashCode()}.
     */
    @Test
    public void testEquals() {
        HashSet<Object> set = new HashSet<Object>();
        List<EthernetAddress> eaddrs = createEthernetAddresses(false);
        for (EthernetAddress eaddr: eaddrs) {
            byte[] bytes1 = eaddr.getValue();
            byte[] bytes2 = bytes1.clone();
            SetDlSrcAction act1 = new SetDlSrcAction(bytes1);
            SetDlSrcAction act2 = new SetDlSrcAction(bytes2);
            try {
                SetDlSrcActionImpl impl1 = new SetDlSrcActionImpl(act1);
                SetDlSrcActionImpl impl2 = new SetDlSrcActionImpl(act2);
                testEquals(set, impl1, impl2);
            } catch (Exception e) {
                unexpected(e);
            }
        }

        assertEquals(eaddrs.size(), set.size());
    }

    /**
     * Test case for {@link SetDlSrcActionImpl#toString()}.
     */
    @Test
    public void testToString() {
        String prefix = "SetDlSrcActionImpl[";
        String suffix = "]";
        for (EthernetAddress eaddr: createEthernetAddresses(false)) {
            byte[] bytes = eaddr.getValue();
            SetDlSrcAction act = new SetDlSrcAction(bytes);
            try {
                SetDlSrcActionImpl impl = new SetDlSrcActionImpl(act);
                String a = "addr=" + ByteUtils.toHexString(bytes);
                String required = joinStrings(prefix, suffix, ",", a);
                assertEquals(required, impl.toString());
            } catch (Exception e) {
                unexpected(e);
            }
        }
    }

    /**
     * Ensure that {@link SetDlSrcActionImpl} is serializable.
     */
    @Test
    public void testSerialize() {
        for (EthernetAddress eaddr: createEthernetAddresses(false)) {
            byte[] bytes = eaddr.getValue();
            SetDlSrcAction act = new SetDlSrcAction(bytes);
            try {
                SetDlSrcActionImpl impl = new SetDlSrcActionImpl(act);
                serializeTest(impl);
            } catch (Exception e) {
                unexpected(e);
            }
        }
    }
}
