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
import java.util.List;

import com.codefollower.lealone.atomicdb.db.Cell;
import com.codefollower.lealone.atomicdb.db.marshal.AbstractType;
import com.codefollower.lealone.atomicdb.db.marshal.ColumnToCollectionType;
import com.codefollower.lealone.atomicdb.db.marshal.CompositeType;
import com.codefollower.lealone.atomicdb.db.marshal.UTF8Type;

public abstract class CellNames
{
    private CellNames() {}

    public static CellNameType fromAbstractType(AbstractType<?> type, boolean isDense)
    {
        if (isDense)
        {
            if (type instanceof CompositeType)
            {
                return new CompoundDenseCellNameType(((CompositeType)type).types);
            }
            else
            {
                return new SimpleDenseCellNameType(type);
            }
        }
        else
        {
            if (type instanceof CompositeType)
            {
                List<AbstractType<?>> types = ((CompositeType)type).types;
                if (types.get(types.size() - 1) instanceof ColumnToCollectionType)
                {
                    assert types.get(types.size() - 2) instanceof UTF8Type;
                    return new CompoundSparseCellNameType.WithCollection(types.subList(0, types.size() - 2), (ColumnToCollectionType)types.get(types.size() - 1));
                }
                else
                {
                    assert types.get(types.size() - 1) instanceof UTF8Type;
                    return new CompoundSparseCellNameType(types.subList(0, types.size() - 1));
                }
            }
            else
            {
                return new SimpleSparseCellNameType(type);
            }
        }
    }

    // Mainly for tests and a few cases where we know what we need and didn't wanted to pass the type around.
    // Avoid in general, prefer the CellNameType methods.
    public static CellName simpleDense(ByteBuffer bb)
    {
        assert bb.hasRemaining();
        return new SimpleDenseCellName(bb);
    }

    // Mainly for tests and a few cases where we know what we need and didn't wanted to pass the type around
    // Avoid in general, prefer the CellNameType methods.
    public static CellName compositeDense(ByteBuffer... bbs)
    {
        return new CompoundDenseCellName(bbs);
    }

    public static String getColumnsString(CellNameType type, Iterable<Cell> columns)
    {
        StringBuilder builder = new StringBuilder();
        for (Cell cell : columns)
            builder.append(cell.getString(type)).append(",");
        return builder.toString();
    }
}
