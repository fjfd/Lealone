/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.codefollower.lealone.atomicdb.service;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;

import com.codefollower.lealone.atomicdb.concurrent.JMXConfigurableThreadPoolExecutor;
import com.codefollower.lealone.atomicdb.concurrent.NamedThreadFactory;
import com.codefollower.lealone.atomicdb.config.DatabaseDescriptor;
import com.codefollower.lealone.atomicdb.dht.Range;
import com.codefollower.lealone.atomicdb.dht.Token;
import com.codefollower.lealone.atomicdb.gms.FailureDetector;
import com.codefollower.lealone.atomicdb.gms.Gossiper;
import com.codefollower.lealone.atomicdb.locator.TokenMetadata;
import com.codefollower.lealone.atomicdb.repair.*;
import com.codefollower.lealone.atomicdb.repair.messages.RepairMessage;
import com.codefollower.lealone.atomicdb.repair.messages.SyncComplete;
import com.codefollower.lealone.atomicdb.repair.messages.ValidationComplete;
import com.codefollower.lealone.atomicdb.utils.FBUtilities;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;


/**
 * ActiveRepairService is the starting point for manual "active" repairs.
 *
 * Each user triggered repair will correspond to one or multiple repair session,
 * one for each token range to repair. On repair session might repair multiple
 * column families. For each of those column families, the repair session will
 * request merkle trees for each replica of the range being repaired, diff those
 * trees upon receiving them, schedule the streaming ofthe parts to repair (based on
 * the tree diffs) and wait for all those operation. See RepairSession for more
 * details.
 *
 * The creation of a repair session is done through the submitRepairSession that
 * returns a future on the completion of that session.
 */
public class ActiveRepairService
{
    // singleton enforcement
    public static final ActiveRepairService instance = new ActiveRepairService();

    private static final ThreadPoolExecutor executor;
    static
    {
        executor = new JMXConfigurableThreadPoolExecutor(4,
                                                         60,
                                                         TimeUnit.SECONDS,
                                                         new LinkedBlockingQueue<Runnable>(),
                                                         new NamedThreadFactory("AntiEntropySessions"),
                                                         "internal");
    }

    public static enum Status
    {
        STARTED, SESSION_SUCCESS, SESSION_FAILED, FINISHED
    }

    /**
     * A map of active session.
     */
    private final ConcurrentMap<UUID, RepairSession> sessions;

    /**
     * Protected constructor. Use ActiveRepairService.instance.
     */
    protected ActiveRepairService()
    {
        sessions = new ConcurrentHashMap<>();
    }

    /**
     * Requests repairs for the given keyspace and column families.
     *
     * @return Future for asynchronous call or null if there is no need to repair
     */
    public RepairFuture submitRepairSession(Range<Token> range, String keyspace, boolean isSequential, Collection<String> dataCenters, String... cfnames)
    {
        RepairSession session = new RepairSession(range, keyspace, isSequential, dataCenters, cfnames);
        if (session.endpoints.isEmpty())
            return null;
        RepairFuture futureTask = new RepairFuture(session);
        executor.execute(futureTask);
        return futureTask;
    }

    public void addToActiveSessions(RepairSession session)
    {
        sessions.put(session.getId(), session);
        Gossiper.instance.register(session);
        FailureDetector.instance.registerFailureDetectionEventListener(session);
    }

    public void removeFromActiveSessions(RepairSession session)
    {
        FailureDetector.instance.unregisterFailureDetectionEventListener(session);
        Gossiper.instance.unregister(session);
        sessions.remove(session.getId());
    }

    public void terminateSessions()
    {
        for (RepairSession session : sessions.values())
        {
            session.forceShutdown();
        }
    }

    // for testing only. Create a session corresponding to a fake request and
    // add it to the sessions (avoid NPE in tests)
    RepairFuture submitArtificialRepairSession(RepairJobDesc desc)
    {
        RepairSession session = new RepairSession(desc.sessionId, desc.range, desc.keyspace, false, null, new String[]{desc.columnFamily});
        sessions.put(session.getId(), session);
        RepairFuture futureTask = new RepairFuture(session);
        executor.execute(futureTask);
        return futureTask;
    }

    /**
     * Return all of the neighbors with whom we share the provided range.
     *
     * @param keyspaceName keyspace to repair
     * @param toRepair token to repair
     * @param dataCenters the data centers to involve in the repair
     *
     * @return neighbors with whom we share the provided range
     */
    public static Set<InetAddress> getNeighbors(String keyspaceName, Range<Token> toRepair, Collection<String> dataCenters)
    {
        if (dataCenters != null && !dataCenters.contains(DatabaseDescriptor.getLocalDataCenter()))
            throw new IllegalArgumentException("The local data center must be part of the repair");

        StorageService ss = StorageService.instance;
        Map<Range<Token>, List<InetAddress>> replicaSets = ss.getRangeToAddressMap(keyspaceName);
        Range<Token> rangeSuperSet = null;
        for (Range<Token> range : ss.getLocalRanges(keyspaceName))
        {
            if (range.contains(toRepair))
            {
                rangeSuperSet = range;
                break;
            }
            else if (range.intersects(toRepair))
            {
                throw new IllegalArgumentException("Requested range intersects a local range but is not fully contained in one; this would lead to imprecise repair");
            }
        }
        if (rangeSuperSet == null || !replicaSets.containsKey(rangeSuperSet))
            return Collections.emptySet();

        Set<InetAddress> neighbors = new HashSet<>(replicaSets.get(rangeSuperSet));
        neighbors.remove(FBUtilities.getBroadcastAddress());

        if (dataCenters != null)
        {
            TokenMetadata.Topology topology = ss.getTokenMetadata().cloneOnlyTokenMap().getTopology();
            Set<InetAddress> dcEndpoints = Sets.newHashSet();
            Multimap<String,InetAddress> dcEndpointsMap = topology.getDatacenterEndpoints();
            for (String dc : dataCenters)
            {
                Collection<InetAddress> c = dcEndpointsMap.get(dc);
                if (c != null)
                   dcEndpoints.addAll(c);
            }
            return Sets.intersection(neighbors, dcEndpoints);
        }

        return neighbors;
    }

    public void handleMessage(InetAddress endpoint, RepairMessage message)
    {
        RepairJobDesc desc = message.desc;
        RepairSession session = sessions.get(desc.sessionId);
        if (session == null)
            return;
        switch (message.messageType)
        {
            case VALIDATION_COMPLETE:
                ValidationComplete validation = (ValidationComplete) message;
                session.validationComplete(desc, endpoint, validation.tree);
                break;
            case SYNC_COMPLETE:
                // one of replica is synced.
                SyncComplete sync = (SyncComplete) message;
                session.syncComplete(desc, sync.nodes, sync.success);
                break;
            default:
                break;
        }
    }
}
