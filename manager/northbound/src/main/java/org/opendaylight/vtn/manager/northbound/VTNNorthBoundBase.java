/*
 * Copyright (c) 2013-2015 NEC Corporation
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vtn.manager.northbound;

import java.security.Principal;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.opendaylight.vtn.manager.IVTNFlowDebugger;
import org.opendaylight.vtn.manager.IVTNManager;
import org.opendaylight.vtn.manager.VTNException;
import org.opendaylight.vtn.manager.flow.filter.FlowFilter;
import org.opendaylight.vtn.manager.flow.filter.FlowFilterId;
import org.opendaylight.vtn.manager.util.ByteUtils;

import org.opendaylight.controller.containermanager.IContainerManager;
import org.opendaylight.controller.northbound.commons.RestMessages;
import org.opendaylight.controller.northbound.commons.exception.
    BadRequestException;
import org.opendaylight.controller.northbound.commons.exception.
    InternalServerErrorException;
import org.opendaylight.controller.northbound.commons.exception.
    NotAcceptableException;
import org.opendaylight.controller.northbound.commons.exception.
    ResourceConflictException;
import org.opendaylight.controller.northbound.commons.exception.
    ResourceNotFoundException;
import org.opendaylight.controller.northbound.commons.exception.
    ServiceUnavailableException;
import org.opendaylight.controller.northbound.commons.exception.
    UnauthorizedException;
import org.opendaylight.controller.northbound.commons.utils.NorthboundUtils;
import org.opendaylight.controller.sal.authorization.Privilege;
import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.core.UpdateType;
import org.opendaylight.controller.sal.packet.address.EthernetAddress;
import org.opendaylight.controller.sal.utils.ServiceHelper;
import org.opendaylight.controller.sal.utils.Status;
import org.opendaylight.controller.sal.utils.StatusCode;

/**
 * Base class of VTN Manager REST API.
 */
public abstract class VTNNorthBoundBase {
    /**
     * The type of the flow filter list for incoming packets.
     */
    private static final String  FF_LIST_INCOMING = "in";

    /**
     * The type of the flow filter list for outgoing packets.
     */
    private static final String  FF_LIST_OUTGOING = "out";

    /**
     * HTTP user name sent by a client.
     */
    private String  userName;

    /**
     * Set security context.
     *
     * @param context  A security context to be set.
     */
    @Context
    public void setSecurityContext(SecurityContext context) {
        if (context != null) {
            Principal p = context.getUserPrincipal();
            if (p != null) {
                userName = p.getName();
            }
        }
    }

    /**
     * Return HTTP user name.
     *
     * @return  User name.
     */
    protected String getUserName() {
        return userName;
    }

    /**
     * Check access privilege.
     *
     * @param containerName  The name of the container.
     * @param priv  Access privilege.
     * @throws UnauthorizedException
     *    A client is not authorized.
     * @throws ResourceNotFoundException
     *    Invalid container name is specified.
     */
    protected void checkPrivilege(String containerName, Privilege priv) {
        IContainerManager containerManager = (IContainerManager)ServiceHelper.
            getGlobalInstance(IContainerManager.class, this);
        if (containerManager == null) {
            throw serviceUnavailable("Container");
        }
        if (!containerManager.doesContainerExist(containerName)) {
            String msg = containerName + ": " +
                RestMessages.NOCONTAINER.toString();
            throw new ResourceNotFoundException(msg);
        }

        String name = getUserName();
        if (!NorthboundUtils.isAuthorized(name, containerName, priv, this)) {
            String msg = "User is not authorized to perform this operation " +
                "on container " + containerName;
            throw new UnauthorizedException(msg);
        }
    }

    /**
     * Return a ServiceUnavailableException.
     *
     * @param service  The name of unavailable service.
     * @return  A {@link ServiceUnavailableException} instance.
     */
    protected ServiceUnavailableException serviceUnavailable(String service) {
        String msg = service + ": " + RestMessages.SERVICEUNAVAILABLE;
        return new ServiceUnavailableException(msg);
    }

    /**
     * Convert failure status returned by the VTN manager into web application
     * exception.
     *
     * @param status  Failure status.
     * @return  An exception.
     */
    protected WebApplicationException getException(Status status) {
        assert !status.isSuccess();

        StatusCode code = status.getCode();
        String desc = status.getDescription();
        if (code == StatusCode.BADREQUEST) {
            return new BadRequestException(desc);
        }
        if (code == StatusCode.CONFLICT) {
            return new ResourceConflictException(desc);
        }
        if (code == StatusCode.NOTACCEPTABLE) {
            return new NotAcceptableException(desc);
        }
        if (code == StatusCode.NOTFOUND) {
            throw new ResourceNotFoundException(desc);
        }

        String msg = RestMessages.INTERNALERROR + ": " + desc;
        return new InternalServerErrorException(msg);
    }

    /**
     * Return the VTN manager service associated with the specified container.
     *
     * @param containerName  The container name.
     * @return  The VTN manager service associated with the specified
     *          container.
     * @throws ServiceUnavailableException
     *    Unable to get VTN manager service.
     * @throws ResourceNotFoundException
     *    Invalid container name is specified.
     */
    protected IVTNManager getVTNManager(String containerName) {
        IVTNManager mgr = (IVTNManager)ServiceHelper.
            getInstance(IVTNManager.class, containerName, this);
        if (mgr == null) {
            throw serviceUnavailable("VTN Manager");
        }

        return mgr;
    }

    /**
     * Return the VTN flow debugger service associated with the specified
     * container.
     *
     * @param containerName  The container name.
     * @return  The VTN flow debugger service associated with the specified
     *          container.
     * @throws ServiceUnavailableException
     *    Unable to get VTN manager service.
     * @throws ResourceNotFoundException
     *    Invalid container name is specified.
     */
    protected IVTNFlowDebugger getVTNFlowDebugger(String containerName) {
        IVTNFlowDebugger debugger = (IVTNFlowDebugger)ServiceHelper.
            getInstance(IVTNFlowDebugger.class, containerName, this);
        if (debugger == null) {
            throw serviceUnavailable("VTN Flow Debugger");
        }

        return debugger;
    }

    /**
     * Parse a string representation of Ethernet address.
     *
     * @param str  A string representation of Ethernet address.
     * @return  An {@code EthernetAddress} object.
     * @throws BadRequestException
     *    Invalid string is passed to {@code str}.
     */
    protected EthernetAddress parseEthernetAddress(String str) {
        try {
            byte[] b = ByteUtils.toBytes(str);
            return new EthernetAddress(b);
        } catch (Exception e) {
            throw new BadRequestException("Invalid MAC address.");
        }
    }

    /**
     * Convert the specified string into a VLAN ID.
     *
     * <p>
     *   Note that {@code null} is converted into {@code 0}.
     * </p>
     *
     * @param str    A string representation of a VLAN ID.
     * @return  A VLAN ID.
     * @throws BadRequestException
     *    Invalid string is passed to {@code str}.
     */
    protected short parseVlanId(String str) {
        if (str == null) {
            return (short)0;
        }

        try {
            return Short.parseShort(str);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid VLAN ID.");
        }
    }

    /**
     * Convert the specified string into a {@link Node} instance.
     *
     * @param str  A string representation of a {@link Node} instance.
     * @return     A {@link Node} instance.
     * @throws BadRequestException
     *    Invalid string is passed to {@code str}.
     */
    protected Node parseNode(String str) {
        Node node = Node.fromString(str);
        if (node == null) {
            throw new BadRequestException("Invalid node: " + str);
        }

        return node;
    }

    /**
     * Convert the specified string into a {@link Node} instance.
     *
     * @param type  Type of the node corresponding to the physical switch.
     * @param id    A string which represents identifier of the node
     *              corresponding to the physical switch.
     * @return      A {@link Node} instance.
     * @throws BadRequestException
     *    Invalid string is passed to {@code type} or {@code id}.
     */
    protected Node parseNode(String type, String id) {
        Node node = Node.fromString(type, id);
        if (node == null) {
            StringBuilder builder = new StringBuilder("Invalid node: ");
            builder.append(type).append('|').append(id);
            throw new BadRequestException(builder.toString());
        }

        return node;
    }

    /**
     * Convert the type of flow filter list into boolean value.
     *
     * @param listType  The type of the flow filter list (case insensitive).
     * @return  {@code true} is returned if the given type represents the
     *          flow filter list for outgoing packets.
     *          {@code false} is returned if the given type represents the
     *          flow filter list for incoming packets.
     * @throws BadRequestException
     *    Invalid string was passed to {@code listType}.
     */
    protected boolean getFlowFilterType(String listType) {
        if (FF_LIST_INCOMING.equalsIgnoreCase(listType)) {
            return false;
        }
        if (FF_LIST_OUTGOING.equalsIgnoreCase(listType)) {
            return true;
        }

        throw new BadRequestException("Invalid flow filter type: " + listType);
    }

    /**
     * Return all flow filters in the specified flow filter list.
     *
     * @param container  The name of the container.
     * @param fid        Flow filter identifier.
     * @return  A {@link FlowFilterList} instance.
     */
    protected FlowFilterList getFlowFilters(String container,
                                            FlowFilterId fid) {
        checkPrivilege(container, Privilege.READ);

        IVTNManager mgr = getVTNManager(container);
        try {
            return new FlowFilterList(mgr.getFlowFilters(fid));
        } catch (VTNException e) {
            throw getException(e.getStatus());
        }
    }

    /**
     * Return the specified flow filter in the specified flow filter list.
     *
     * @param container  The name of the container.
     * @param fid        Flow filter identifier.
     * @param index      The index which specifies the flow filter.
     * @return  A {@link FlowFilter} instance.
     */
    protected FlowFilter getFlowFilter(String container, FlowFilterId fid,
                                       int index) {
        checkPrivilege(container, Privilege.READ);

        IVTNManager mgr = getVTNManager(container);
        try {
            return mgr.getFlowFilter(fid, index);
        } catch (VTNException e) {
            throw getException(e.getStatus());
        }
    }

    /**
     * Create or modify the flow filter in the specified flow filter list.
     *
     * @param uriInfo    Requested URI information.
     * @param container  The name of the container.
     * @param fid        Flow filter identifier.
     * @param index      The index which specifies the flow filter.
     * @param filter     A {@link FlowFilter} instance.
     * @return  Response as dictated by the HTTP Response Status code.
     */
    protected Response putFlowFilter(UriInfo uriInfo, String container,
                                     FlowFilterId fid, int index,
                                     FlowFilter filter) {
        checkPrivilege(container, Privilege.WRITE);

        IVTNManager mgr = getVTNManager(container);
        try {
            UpdateType result = mgr.setFlowFilter(fid, index, filter);
            if (result == null) {
                return Response.noContent().build();
            }
            if (result == UpdateType.ADDED) {
                // Return CREATED with Location header.
                return Response.created(uriInfo.getAbsolutePath()).build();
            }

            return Response.ok().build();
        } catch (VTNException e) {
            throw getException(e.getStatus());
        }
    }

    /**
     * Delete all flow filters in the specified flow filter list.
     *
     * @param container  The name of the container.
     * @param fid        Flow filter identifier.
     * @return  Response as dictated by the HTTP Response Status code.
     */
    protected Response deleteFlowFilters(String container, FlowFilterId fid) {
        checkPrivilege(container, Privilege.WRITE);

        IVTNManager mgr = getVTNManager(container);
        Status status = mgr.clearFlowFilter(fid);
        if (status == null) {
            return Response.noContent().build();
        }
        if (status.isSuccess()) {
            return Response.ok().build();
        }

        throw getException(status);
    }

    /**
     * Delete the flow filter in the specified flow filter list.
     *
     * @param container  The name of the container.
     * @param fid        Flow filter identifier.
     * @param index      The index which specifies the flow filter.
     * @return  Response as dictated by the HTTP Response Status code.
     */
    protected Response deleteFlowFilter(String container, FlowFilterId fid,
                                        int index) {
        checkPrivilege(container, Privilege.WRITE);

        IVTNManager mgr = getVTNManager(container);
        Status status = mgr.removeFlowFilter(fid, index);
        if (status == null) {
            return Response.noContent().build();
        }
        if (status.isSuccess()) {
            return Response.ok().build();
        }

        throw getException(status);
    }
}
