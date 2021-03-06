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
package com.codefollower.lealone.atomicdb.streaming.management;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import javax.management.openmbean.*;

import com.codefollower.lealone.atomicdb.streaming.ProgressInfo;
import com.codefollower.lealone.atomicdb.streaming.SessionInfo;
import com.codefollower.lealone.atomicdb.streaming.StreamSession;
import com.codefollower.lealone.atomicdb.streaming.StreamSummary;
import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;


public class SessionInfoCompositeData
{
    private static final String[] ITEM_NAMES = new String[]{"planId",
                                                            "peer",
                                                            "receivingSummaries",
                                                            "sendingSummaries",
                                                            "state",
                                                            "receivingFiles",
                                                            "sendingFiles"};
    private static final String[] ITEM_DESCS = new String[]{"Plan ID",
                                                            "Session peer",
                                                            "Summaries of receiving data",
                                                            "Summaries of sending data",
                                                            "Current session state",
                                                            "Receiving files",
                                                            "Sending files"};
    private static final OpenType<?>[] ITEM_TYPES;

    public static final CompositeType COMPOSITE_TYPE;
    static  {
        try
        {
            ITEM_TYPES = new OpenType[]{SimpleType.STRING,
                                        SimpleType.STRING,
                                        ArrayType.getArrayType(StreamSummaryCompositeData.COMPOSITE_TYPE),
                                        ArrayType.getArrayType(StreamSummaryCompositeData.COMPOSITE_TYPE),
                                        SimpleType.STRING,
                                        ArrayType.getArrayType(ProgressInfoCompositeData.COMPOSITE_TYPE),
                                        ArrayType.getArrayType(ProgressInfoCompositeData.COMPOSITE_TYPE)};
            COMPOSITE_TYPE = new CompositeType(SessionInfo.class.getName(),
                                               "SessionInfo",
                                               ITEM_NAMES,
                                               ITEM_DESCS,
                                               ITEM_TYPES);
        }
        catch (OpenDataException e)
        {
            throw Throwables.propagate(e);
        }
    }

    public static CompositeData toCompositeData(final UUID planId, SessionInfo sessionInfo)
    {
        Map<String, Object> valueMap = new HashMap<>();
        valueMap.put(ITEM_NAMES[0], planId.toString());
        valueMap.put(ITEM_NAMES[1], sessionInfo.peer.getHostAddress());
        Function<StreamSummary, CompositeData> fromStreamSummary = new Function<StreamSummary, CompositeData>()
        {
            public CompositeData apply(StreamSummary input)
            {
                return StreamSummaryCompositeData.toCompositeData(input);
            }
        };
        valueMap.put(ITEM_NAMES[2], toArrayOfCompositeData(sessionInfo.receivingSummaries, fromStreamSummary));
        valueMap.put(ITEM_NAMES[3], toArrayOfCompositeData(sessionInfo.sendingSummaries, fromStreamSummary));
        valueMap.put(ITEM_NAMES[4], sessionInfo.state.name());
        Function<ProgressInfo, CompositeData> fromProgressInfo = new Function<ProgressInfo, CompositeData>()
        {
            public CompositeData apply(ProgressInfo input)
            {
                return ProgressInfoCompositeData.toCompositeData(planId, input);
            }
        };
        valueMap.put(ITEM_NAMES[5], toArrayOfCompositeData(sessionInfo.getReceivingFiles(), fromProgressInfo));
        valueMap.put(ITEM_NAMES[6], toArrayOfCompositeData(sessionInfo.getSendingFiles(), fromProgressInfo));
        try
        {
            return new CompositeDataSupport(COMPOSITE_TYPE, valueMap);
        }
        catch (OpenDataException e)
        {
            throw Throwables.propagate(e);
        }
    }

    public static SessionInfo fromCompositeData(CompositeData cd)
    {
        assert cd.getCompositeType().equals(COMPOSITE_TYPE);

        Object[] values = cd.getAll(ITEM_NAMES);
        InetAddress peer;
        try
        {
            peer = InetAddress.getByName((String) values[1]);
        }
        catch (UnknownHostException e)
        {
            throw Throwables.propagate(e);
        }
        Function<CompositeData, StreamSummary> toStreamSummary = new Function<CompositeData, StreamSummary>()
        {
            public StreamSummary apply(CompositeData input)
            {
                return StreamSummaryCompositeData.fromCompositeData(input);
            }
        };
        SessionInfo info = new SessionInfo(peer,
                                           fromArrayOfCompositeData((CompositeData[]) values[2], toStreamSummary),
                                           fromArrayOfCompositeData((CompositeData[]) values[3], toStreamSummary),
                                           StreamSession.State.valueOf((String) values[4]));
        Function<CompositeData, ProgressInfo> toProgressInfo = new Function<CompositeData, ProgressInfo>()
        {
            public ProgressInfo apply(CompositeData input)
            {
                return ProgressInfoCompositeData.fromCompositeData(input);
            }
        };
        for (ProgressInfo progress : fromArrayOfCompositeData((CompositeData[]) values[5], toProgressInfo))
        {
            info.updateProgress(progress);
        }
        for (ProgressInfo progress : fromArrayOfCompositeData((CompositeData[]) values[6], toProgressInfo))
        {
            info.updateProgress(progress);
        }
        return info;
    }

    private static <T> Collection<T> fromArrayOfCompositeData(CompositeData[] cds, Function<CompositeData, T> func)
    {
        return Lists.newArrayList(Iterables.transform(Arrays.asList(cds), func));
    }

    private static <T> CompositeData[] toArrayOfCompositeData(Collection<T> toConvert, Function<T, CompositeData> func)
    {
        CompositeData[] composites = new CompositeData[toConvert.size()];
        return Lists.newArrayList(Iterables.transform(toConvert, func)).toArray(composites);
    }
}
