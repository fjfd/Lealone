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
package com.codefollower.lealone.atomicdb.db.marshal;

import java.nio.ByteBuffer;

import com.codefollower.lealone.atomicdb.cql.CQL3Type;
import com.codefollower.lealone.atomicdb.serializers.FloatSerializer;
import com.codefollower.lealone.atomicdb.serializers.MarshalException;
import com.codefollower.lealone.atomicdb.serializers.TypeSerializer;
import com.codefollower.lealone.atomicdb.utils.ByteBufferUtil;


public class FloatType extends AbstractType<Float>
{
    public static final FloatType instance = new FloatType();

    FloatType() {} // singleton

    public int compare(ByteBuffer o1, ByteBuffer o2)
    {
        if (o1.remaining() == 0)
        {
            return o2.remaining() == 0 ? 0 : -1;
        }
        if (o2.remaining() == 0)
        {
            return 1;
        }

        return compose(o1).compareTo(compose(o2));
    }

    public ByteBuffer fromString(String source) throws MarshalException
    {
      // Return an empty ByteBuffer for an empty string.
      if (source.isEmpty())
          return ByteBufferUtil.EMPTY_BYTE_BUFFER;

      try
      {
          float f = Float.parseFloat(source);
          return ByteBufferUtil.bytes(f);
      }
      catch (NumberFormatException e1)
      {
          throw new MarshalException(String.format("unable to coerce '%s' to a float", source), e1);
      }
    }

    public CQL3Type asCQL3Type()
    {
        return CQL3Type.Native.FLOAT;
    }

    public TypeSerializer<Float> getSerializer()
    {
        return FloatSerializer.instance;
    }
}
