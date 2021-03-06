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

import com.codefollower.lealone.atomicdb.cql.ColumnIdentifier;
import com.codefollower.lealone.atomicdb.utils.Allocator;
import com.codefollower.lealone.atomicdb.utils.ObjectSizes;

public class SimpleDenseCellName extends SimpleComposite implements CellName
{
    // Not meant to be used directly, you should use the CellNameType method instead
    SimpleDenseCellName(ByteBuffer element)
    {
        super(element);
    }

    public int clusteringSize()
    {
        return 1;
    }

    public ColumnIdentifier cql3ColumnName()
    {
        return null;
    }

    public ByteBuffer collectionElement()
    {
        return null;
    }

    public boolean isCollectionCell()
    {
        return false;
    }

    public boolean isSameCQL3RowAs(CellName other)
    {
        // Dense cell imply one cell by CQL row so no other cell will be the same row.
        return equals(other);
    }

    @Override
    public long memorySize()
    {
        return ObjectSizes.getSuperClassFieldSize(super.memorySize());
    }

    // If cellnames were sharing some prefix components, this will break it, so
    // we might want to try to do better.
    @Override
    public CellName copy(Allocator allocator)
    {
        return new SimpleDenseCellName(allocator.clone(element));
    }
}
