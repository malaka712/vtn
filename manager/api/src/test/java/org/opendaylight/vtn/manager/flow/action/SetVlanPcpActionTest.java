/*
 * Copyright (c) 2014 NEC Corporation
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vtn.manager.flow.action;

import java.util.HashSet;

import org.junit.Test;

import org.opendaylight.vtn.manager.TestBase;

/**
 * JUnit test for {@link SetVlanPcpAction}.
 */
public class SetVlanPcpActionTest extends TestBase {
    /**
     * Test case for getter methods.
     */
    @Test
    public void testGetter() {
        byte[] bytes = {Byte.MIN_VALUE, 0, 1, 7, 100, Byte.MAX_VALUE};
        for (byte b: bytes) {
            SetVlanPcpAction act = new SetVlanPcpAction(b);
            assertEquals(b, act.getPriority());
        }
    }

    /**
     * Test case for {@link SetVlanPcpAction#equals(Object)} and
     * {@link SetVlanPcpAction#hashCode()}.
     */
    @Test
    public void testEquals() {
        HashSet<Object> set = new HashSet<Object>();
        byte[] bytes = {Byte.MIN_VALUE, 0, 1, 7, 100, Byte.MAX_VALUE};
        for (byte b: bytes) {
            SetVlanPcpAction act1 = new SetVlanPcpAction(b);
            SetVlanPcpAction act2 = new SetVlanPcpAction(b);
            testEquals(set, act1, act2);
        }

        assertEquals(bytes.length, set.size());
    }

    /**
     * Test case for {@link SetVlanPcpAction#toString()}.
     */
    @Test
    public void testToString() {
        byte[] bytes = {Byte.MIN_VALUE, 0, 1, 7, 100, Byte.MAX_VALUE};
        for (byte b: bytes) {
            SetVlanPcpAction act = new SetVlanPcpAction(b);
            assertEquals("SetVlanPcpAction[priority=" + b + "]",
                         act.toString());
        }
    }

    /**
     * Ensure that {@link SetVlanPcpAction} is serializable.
     */
    @Test
    public void testSerialize() {
        byte[] bytes = {Byte.MIN_VALUE, 0, 1, 7, 100, Byte.MAX_VALUE};
        for (byte b: bytes) {
            SetVlanPcpAction act = new SetVlanPcpAction(b);
            serializeTest(act);
        }
    }

    /**
     * Ensure that {@link SetVlanPcpAction} is mapped to XML root element.
     */
    @Test
    public void testJAXB() {
        byte[] bytes = {Byte.MIN_VALUE, 0, 1, 7, 100, Byte.MAX_VALUE};
        for (byte b: bytes) {
            SetVlanPcpAction act = new SetVlanPcpAction(b);
            jaxbTest(act, "setvlanpcp");
        }
    }

    /**
     * Ensure that {@link SetVlanPcpAction} is mapped to JSON object.
     */
    @Test
    public void testJSON() {
        byte[] bytes = {Byte.MIN_VALUE, 0, 1, 7, 100, Byte.MAX_VALUE};
        for (byte b: bytes) {
            SetVlanPcpAction act = new SetVlanPcpAction(b);
            jsonTest(act);
        }
    }
}