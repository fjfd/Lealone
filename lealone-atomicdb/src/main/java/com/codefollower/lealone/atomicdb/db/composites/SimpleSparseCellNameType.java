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
package com.codefollower.lealone.atomicdb.db.composites;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import com.codefollower.lealone.atomicdb.cql.CQL3Row;
import com.codefollower.lealone.atomicdb.cql.ColumnIdentifier;
import com.codefollower.lealone.atomicdb.db.marshal.AbstractType;

public class SimpleSparseCellNameType extends AbstractSimpleCellNameType
{
    // Simple sparse means static thrift CF or non-clustered CQL3. This means that cell names will mainly
    // be those that have been declared and we can intern the whole CellName instances.
    private final Map<ByteBuffer, CellName> internedNames;

    public SimpleSparseCellNameType(AbstractType<?> type)
    {
        this(type, new HashMap<ByteBuffer, CellName>());
    }

    private SimpleSparseCellNameType(AbstractType<?> type, Map<ByteBuffer, CellName> internedNames)
    {
        super(type);
        this.internedNames = internedNames;
    }

    public int clusteringPrefixSize()
    {
        return 0;
    }

    public CellNameType setSubtype(int position, AbstractType<?> newType)
    {
        if (position != 0)
            throw new IllegalArgumentException();
        return new SimpleSparseCellNameType(newType, internedNames);
    }

    public CBuilder prefixBuilder()
    {
        return Composites.EMPTY_BUILDER;
    }

    public boolean isDense()
    {
        return false;
    }

    public CellName create(Composite prefix, ColumnIdentifier columnName)
    {
        assert prefix.isEmpty();
        CellName cn = internedNames.get(columnName.bytes);
        return cn == null ? new SimpleSparseCellName(columnName) : cn;
    }

    @Override
    public Composite fromByteBuffer(ByteBuffer bb)
    {
        if (!bb.hasRemaining())
            return Composites.EMPTY;

        CellName cn = internedNames.get(bb);
        return cn == null ? new SimpleSparseCellName(new ColumnIdentifier(bb, type)) : cn;
    }

    public void addCQL3Column(ColumnIdentifier id)
    {
        internedNames.put(id.bytes, new SimpleSparseCellName(id));
    }

    public void removeCQL3Column(ColumnIdentifier id)
    {
        internedNames.remove(id.bytes);
    }

    public CQL3Row.Builder CQL3RowBuilder(long now)
    {
        return makeSparseCQL3RowBuilder(now);
    }
}
