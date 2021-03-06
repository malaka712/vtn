/*
 * Copyright (c) 2015 NEC Corporation
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vtn.manager.internal;

import java.util.List;
import java.util.Timer;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

import com.google.common.util.concurrent.FutureCallback;

import org.opendaylight.vtn.manager.VTNException;

import org.opendaylight.vtn.manager.internal.util.concurrent.VTNFuture;
import org.opendaylight.vtn.manager.internal.util.inventory.SalPort;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;

import org.opendaylight.controller.sal.packet.Packet;

import org.opendaylight.yangtools.yang.binding.Notification;
import org.opendaylight.yangtools.yang.binding.RpcService;

/**
 * This interface defines an internal OSGi service which provides MD-SAL
 * VTN Manager services used by AD-SAL interface.
 */
public interface VTNManagerProvider extends AutoCloseable, Executor, TxQueue {
    /**
     * Set AD-SAL VTN Manager service.
     *
     * @param mgr  VTN Manager service.
     */
    void setVTNManager(VTNManagerImpl mgr);

    /**
     * Shutdown all listener services.
     */
    void shutdown();

    /**
     * Notify that the configuration has been initialized.
     */
    void configLoaded();

    /**
     * Return a {@link VTNConfig} instance that contains current configuration.
     *
     * @return  A {@link VTNConfig} instance.
     */
    VTNConfig getVTNConfig();

    /**
     * Create a new read-only MD-SAL datastore transaction context.
     *
     * @return  A read-only MD-SAL datastore transaction context.
     */
    TxContext newTxContext();

    /**
     * Return the global timer.
     *
     * @return  The global timer.
     */
    Timer getTimer();

    /**
     * Execute the specified task asynchronously.
     *
     * @param task  A task to be executed on this thread pool.
     * @return  {@code true} is returned if the specified task was submitted.
     *          {@code false} is returned if the specified tas was rejected.
     * @throws NullPointerException
     *    {@code task} is {@code null}.
     */
    boolean executeTask(Runnable task);

    /**
     * Set the given future callback to the given future.
     *
     * <p>
     *   If the given future implements {@code ListenableFuture}, this method
     *   adds the given callback to the given future, and the callback will
     *   be invoked by a thread which is executing the future.
     *   If not, the given callback will be invoked on another thread
     *   asynchronously.
     * </p>
     *
     * @param future  A {@link Future} instance to wait for its completion.
     * @param cb      A {@link FutureCallback} instance.
     * @param <T>     The type of the object returned by the given future.
     */
    <T> void setCallback(Future<T> future, FutureCallback<? super T> cb);

    /**
     * Return a data broker service instance.
     *
     * @return  A {@link DataBroker} instance.
     */
    DataBroker getDataBroker();

    /**
     * Transmit the given packet.
     *
     * @param egress  A {@link SalPort} instance which specifies the egress
     *                switch port.
     * @param packet  A {@link Packet} instance to be transmitted.
     */
    void transmit(SalPort egress, Packet packet);

    /**
     * Return the packet route resolver associated with the system default
     * routing policy.
     *
     * @return  A {@link RouteResolver} instance on success.
     *          {@code null} if the routing management is already closed.
     */
    RouteResolver getRouteResolver();

    /**
     * Return a packet route resolver.
     *
     * @param id  The identifier of the path policy.
     * @return  A {@link RouteResolver} instance if found.
     *          {@code null} if not fonud.
     */
    RouteResolver getRouteResolver(Integer id);

    /**
     * Remove flow entries that match the given condition.
     *
     * <p>
     *   Note that this method affects all VTNs.
     * </p>
     *
     * @param selector  A {@link FlowSelector} instance.
     *                  All flow entries are removed if {@code null} is
     *                  specified.
     * @return  A list of {@link VTNFuture} instances associated with flow
     *          removal tasks.
     */
    List<VTNFuture<?>> removeFlows(FlowSelector selector);

    /**
     * Remove flow entries that match the given condition.
     *
     * <p>
     *   Note that this method removes flow entries present in the specified
     *   VTN.
     * </p>
     *
     * @param tname     The name of the VTN. All existing VTNs will be targeted
     *                  if {@code null} is specified.
     * @param selector  A {@link FlowSelector} instance.
     *                  All flow entries are removed if {@code null} is
     *                  specified.
     * @return  A list of {@link VTNFuture} instances associated with flow
     *          removal tasks.
     */
    List<VTNFuture<?>> removeFlows(String tname, FlowSelector selector);

    /**
     * Return an implementation of the specified RPC service.
     *
     * @param type  A class which specifies the RPC service.
     * @param <T>   The type of the RPC service.
     * @return  The proxy for the given RPC service.
     * @throws VTNException
     *    The specified RPC service was not found.
     */
    <T extends RpcService> T getRpcService(Class<T> type) throws VTNException;

    /**
     * Return an implementation of the RPC service provided by the VTN Manager.
     *
     * @param type  A class which specifies the RPC service.
     * @param <T>   The type of the RPC service.
     * @return  The proxy for the given RPC service.
     * @throws VTNException
     *    The specified RPC service was not found.
     */
    <T extends RpcService> T getVtnRpcService(Class<T> type)
        throws VTNException;

    /**
     * Publish the given MD-SAL notification.
     *
     * @param n  A {@link Notification} instance to be published.
     */
    void publish(Notification n);

    /**
     * Post a new MD-SAL transaction task.
     *
     * <p>
     *   This method associates a new {@link VTNFuture} instance with the
     *   given task, including all background tasks started by the task.
     *   Note that the given task is never canceled even if the returned
     *   {@link VTNFuture} is cancelled. {@link VTNFuture#cancel(boolean)} on
     *   the returned value only cancels the wait for background tasks.
     * </p>
     *
     * @param task  A {@link TxTask} instance.
     * @param <T>   The type of the object to be returned by the task.
     * @return  A {@link VTNFuture} associated with the given task and its
     *          background tasks.
     */
    <T> VTNFuture<T> postSync(TxTask<T> task);

    // AutoCloseable

    /**
     * Close the VTN Manager provider service.
     */
    @Override
    void close();
}
