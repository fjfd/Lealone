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
package com.codefollower.lealone.atomicdb.io.sstable;

import java.io.File;
import java.io.IOException;

import com.codefollower.lealone.atomicdb.db.DecoratedKey;
import com.codefollower.lealone.atomicdb.db.RowIndexEntry;
import com.codefollower.lealone.atomicdb.io.util.RandomAccessReader;
import com.codefollower.lealone.atomicdb.service.StorageService;
import com.codefollower.lealone.atomicdb.utils.ByteBufferUtil;
import com.codefollower.lealone.atomicdb.utils.CloseableIterator;
import com.google.common.collect.AbstractIterator;


public class KeyIterator extends AbstractIterator<DecoratedKey> implements CloseableIterator<DecoratedKey>
{
    private final RandomAccessReader in;

    public KeyIterator(Descriptor desc)
    {
        File path = new File(desc.filenameFor(Component.PRIMARY_INDEX));
        in = RandomAccessReader.open(path);
    }

    protected DecoratedKey computeNext()
    {
        try
        {
            if (in.isEOF())
                return endOfData();
            DecoratedKey key = StorageService.getPartitioner().decorateKey(ByteBufferUtil.readWithShortLength(in));
            RowIndexEntry.Serializer.skip(in); // skip remainder of the entry
            return key;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void close()
    {
        in.close();
    }

    public long getBytesRead()
    {
        return in.getFilePointer();
    }

    public long getTotalBytes()
    {
        return in.length();
    }
}
