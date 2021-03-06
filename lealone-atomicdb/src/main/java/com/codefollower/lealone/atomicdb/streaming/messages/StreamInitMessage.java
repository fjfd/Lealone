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
package com.codefollower.lealone.atomicdb.streaming.messages;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.UUID;

import com.codefollower.lealone.atomicdb.db.TypeSizes;
import com.codefollower.lealone.atomicdb.io.IVersionedSerializer;
import com.codefollower.lealone.atomicdb.io.util.DataOutputBuffer;
import com.codefollower.lealone.atomicdb.net.CompactEndpointSerializationHelper;
import com.codefollower.lealone.atomicdb.net.MessagingService;
import com.codefollower.lealone.atomicdb.utils.UUIDSerializer;

/**
 * StreamInitMessage is first sent from the node where {@link com.codefollower.lealone.atomicdb.streaming.StreamSession} is started,
 * to initiate corresponding {@link com.codefollower.lealone.atomicdb.streaming.StreamSession} on the other side.
 */
public class StreamInitMessage
{
    public static IVersionedSerializer<StreamInitMessage> serializer = new StreamInitMessageSerializer();

    public final InetAddress from;
    public final UUID planId;
    public final String description;

    // true if this init message is to connect for outgoing message on receiving side
    public final boolean isForOutgoing;

    public StreamInitMessage(InetAddress from, UUID planId, String description, boolean isForOutgoing)
    {
        this.from = from;
        this.planId = planId;
        this.description = description;
        this.isForOutgoing = isForOutgoing;
    }

    /**
     * Create serialized message.
     *
     * @param compress true if message is compressed
     * @param version Streaming protocol version
     * @return serialized message in ByteBuffer format
     */
    public ByteBuffer createMessage(boolean compress, int version)
    {
        int header = 0;
        // set compression bit.
        if (compress)
            header |= 4;
        // set streaming bit
        header |= 8;
        // Setting up the version bit
        header |= (version << 8);

        byte[] bytes;
        try
        {
            int size = (int)StreamInitMessage.serializer.serializedSize(this, version);
            DataOutputBuffer buffer = new DataOutputBuffer(size);
            StreamInitMessage.serializer.serialize(this, buffer, version);
            bytes = buffer.getData();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        assert bytes.length > 0;

        ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + bytes.length);
        buffer.putInt(MessagingService.PROTOCOL_MAGIC);
        buffer.putInt(header);
        buffer.put(bytes);
        buffer.flip();
        return buffer;
    }

    private static class StreamInitMessageSerializer implements IVersionedSerializer<StreamInitMessage>
    {
        public void serialize(StreamInitMessage message, DataOutput out, int version) throws IOException
        {
            CompactEndpointSerializationHelper.serialize(message.from, out);
            UUIDSerializer.serializer.serialize(message.planId, out, MessagingService.current_version);
            out.writeUTF(message.description);
            out.writeBoolean(message.isForOutgoing);
        }

        public StreamInitMessage deserialize(DataInput in, int version) throws IOException
        {
            InetAddress from = CompactEndpointSerializationHelper.deserialize(in);
            UUID planId = UUIDSerializer.serializer.deserialize(in, MessagingService.current_version);
            String description = in.readUTF();
            boolean sentByInitiator = in.readBoolean();
            return new StreamInitMessage(from, planId, description, sentByInitiator);
        }

        public long serializedSize(StreamInitMessage message, int version)
        {
            long size = CompactEndpointSerializationHelper.serializedSize(message.from);
            size += UUIDSerializer.serializer.serializedSize(message.planId, MessagingService.current_version);
            size += TypeSizes.NATIVE.sizeof(message.description);
            size += TypeSizes.NATIVE.sizeof(message.isForOutgoing);
            return size;
        }
    }
}
