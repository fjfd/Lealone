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
package com.codefollower.lealone.atomicdb.config;

import java.nio.ByteBuffer;
import java.util.*;

import com.codefollower.lealone.atomicdb.cql.*;
import com.codefollower.lealone.atomicdb.db.*;
import com.codefollower.lealone.atomicdb.db.composites.Composite;
import com.codefollower.lealone.atomicdb.db.marshal.*;
import com.codefollower.lealone.atomicdb.exceptions.*;
import com.codefollower.lealone.atomicdb.serializers.MarshalException;
import com.codefollower.lealone.atomicdb.utils.ByteBufferUtil;
import com.codefollower.lealone.atomicdb.utils.FBUtilities;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.Maps;


import static com.codefollower.lealone.atomicdb.utils.FBUtilities.json;

public class ColumnDefinition extends ColumnSpecification
{
    // system.schema_columns column names
    private static final String COLUMN_NAME = "column_name";
    private static final String TYPE = "validator";
    private static final String INDEX_TYPE = "index_type";
    private static final String INDEX_OPTIONS = "index_options";
    private static final String INDEX_NAME = "index_name";
    private static final String COMPONENT_INDEX = "component_index";
    private static final String KIND = "type";

    /*
     * The type of CQL3 column this definition represents.
     * There is 3 main type of CQL3 columns: those parts of the partition key,
     * those parts of the clustering key and the other, regular ones.
     * But when COMPACT STORAGE is used, there is by design only one regular
     * column, whose name is not stored in the data contrarily to the column of
     * type REGULAR. Hence the COMPACT_VALUE type to distinguish it below.
     *
     * Note that thrift/CQL2 only know about definitions of type REGULAR (and
     * the ones whose componentIndex == null).
     */
    public enum Kind
    {
        PARTITION_KEY,
        CLUSTERING_COLUMN,
        REGULAR,
        COMPACT_VALUE;

        public String serialize()
        {
            // For backward compatibility we need to special case CLUSTERING_COLUMN
            return this == CLUSTERING_COLUMN ? "clustering_key" : this.toString().toLowerCase();
        }

        public static Kind deserialize(String value)
        {
            if (value.equalsIgnoreCase("clustering_key"))
                return CLUSTERING_COLUMN;
            return Enum.valueOf(Kind.class, value.toUpperCase());
        }
    }

    public final Kind kind;

    private String indexName;
    private IndexType indexType;
    private Map<String,String> indexOptions;

    /*
     * If the column comparator is a composite type, indicates to which
     * component this definition refers to. If null, the definition refers to
     * the full column name.
     */
    private final Integer componentIndex;

    public static ColumnDefinition partitionKeyDef(CFMetaData cfm, ByteBuffer name, AbstractType<?> validator, Integer componentIndex)
    {
        return new ColumnDefinition(cfm, name, validator, componentIndex, Kind.PARTITION_KEY);
    }

    public static ColumnDefinition clusteringKeyDef(CFMetaData cfm, ByteBuffer name, AbstractType<?> validator, Integer componentIndex)
    {
        return new ColumnDefinition(cfm, name, validator, componentIndex, Kind.CLUSTERING_COLUMN);
    }

    public static ColumnDefinition regularDef(CFMetaData cfm, ByteBuffer name, AbstractType<?> validator, Integer componentIndex)
    {
        return new ColumnDefinition(cfm, name, validator, componentIndex, Kind.REGULAR);
    }

    public static ColumnDefinition compactValueDef(CFMetaData cfm, ByteBuffer name, AbstractType<?> validator)
    {
        return new ColumnDefinition(cfm, name, validator, null, Kind.COMPACT_VALUE);
    }

    public ColumnDefinition(CFMetaData cfm, ByteBuffer name, AbstractType<?> validator, Integer componentIndex, Kind kind)
    {
        this(cfm.ksName,
             cfm.cfName,
             new ColumnIdentifier(name, cfm.getComponentComparator(componentIndex, kind)),
             validator,
             null,
             null,
             null,
             componentIndex,
             kind);
    }

    @VisibleForTesting
    public ColumnDefinition(String ksName,
                            String cfName,
                            ColumnIdentifier name,
                            AbstractType<?> validator,
                            IndexType indexType,
                            Map<String, String> indexOptions,
                            String indexName,
                            Integer componentIndex,
                            Kind kind)
    {
        super(ksName, cfName, name, validator);
        assert name != null && validator != null;
        this.kind = kind;
        this.indexName = indexName;
        this.componentIndex = componentIndex;
        this.setIndexType(indexType, indexOptions);
    }

    public ColumnDefinition copy()
    {
        return new ColumnDefinition(ksName, cfName, name, type, indexType, indexOptions, indexName, componentIndex, kind);
    }

    public ColumnDefinition withNewName(ColumnIdentifier newName)
    {
        return new ColumnDefinition(ksName, cfName, newName, type, indexType, indexOptions, indexName, componentIndex, kind);
    }

    public ColumnDefinition withNewType(AbstractType<?> newType)
    {
        return new ColumnDefinition(ksName, cfName, name, newType, indexType, indexOptions, indexName, componentIndex, kind);
    }

    public boolean isOnAllComponents()
    {
        return componentIndex == null;
    }

    // The componentIndex. This never return null however for convenience sake:
    // if componentIndex == null, this return 0. So caller should first check
    // isOnAllComponents() to distinguish if that's a possibility.
    public int position()
    {
        return componentIndex == null ? 0 : componentIndex;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
            return true;

        if (!(o instanceof ColumnDefinition))
            return false;

        ColumnDefinition cd = (ColumnDefinition) o;

        return Objects.equal(ksName, cd.ksName)
            && Objects.equal(cfName, cd.cfName)
            && Objects.equal(name, cd.name)
            && Objects.equal(type, cd.type)
            && Objects.equal(kind, cd.kind)
            && Objects.equal(componentIndex, cd.componentIndex)
            && Objects.equal(indexName, cd.indexName)
            && Objects.equal(indexType, cd.indexType)
            && Objects.equal(indexOptions, cd.indexOptions);
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(ksName, cfName, name, type, kind, componentIndex, indexName, indexType, indexOptions);
    }

    @Override
    public String toString()
    {
        return Objects.toStringHelper(this)
                      .add("name", name)
                      .add("type", type)
                      .add("kind", kind)
                      .add("componentIndex", componentIndex)
                      .add("indexName", indexName)
                      .add("indexType", indexType)
                      .toString();
    }

    public boolean isThriftCompatible()
    {
        return kind == ColumnDefinition.Kind.REGULAR && componentIndex == null;
    }

    /**
     * Drop specified column from the schema using given mutation.
     *
     * @param mutation  The schema mutation
     * @param timestamp The timestamp to use for column modification
     */
    public void deleteFromSchema(Mutation mutation, long timestamp)
    {
        ColumnFamily cf = mutation.addOrGet(CFMetaData.SchemaColumnsCf);
        int ldt = (int) (System.currentTimeMillis() / 1000);

        // Note: we do want to use name.toString(), not name.bytes directly for backward compatibility (For CQL3, this won't make a difference).
        Composite prefix = CFMetaData.SchemaColumnsCf.comparator.make(cfName, name.toString());
        cf.addAtom(new RangeTombstone(prefix, prefix.end(), timestamp, ldt));
    }

    public void toSchema(Mutation mutation, long timestamp)
    {
        ColumnFamily cf = mutation.addOrGet(CFMetaData.SchemaColumnsCf);
        Composite prefix = CFMetaData.SchemaColumnsCf.comparator.make(cfName, name.toString());
        CFRowAdder adder = new CFRowAdder(cf, prefix, timestamp);

        adder.add(TYPE, type.toString());
        adder.add(INDEX_TYPE, indexType == null ? null : indexType.toString());
        adder.add(INDEX_OPTIONS, json(indexOptions));
        adder.add(INDEX_NAME, indexName);
        adder.add(COMPONENT_INDEX, componentIndex);
        adder.add(KIND, kind.serialize());
    }

    public ColumnDefinition apply(ColumnDefinition def)  throws ConfigurationException
    {
        assert kind == def.kind && Objects.equal(componentIndex, def.componentIndex);

        if (getIndexType() != null && def.getIndexType() != null)
        {
            // If an index is set (and not drop by this update), the validator shouldn't be change to a non-compatible one
            // (and we want true comparator compatibility, not just value one, since the validator is used by LocalPartitioner to order index rows)
            if (!def.type.isCompatibleWith(type))
                throw new ConfigurationException(String.format("Cannot modify validator to a non-order-compatible one for column %s since an index is set", name));

            assert getIndexName() != null;
            if (!getIndexName().equals(def.getIndexName()))
                throw new ConfigurationException("Cannot modify index name");
        }

        return new ColumnDefinition(ksName,
                                    cfName,
                                    name,
                                    def.type,
                                    def.getIndexType(),
                                    def.getIndexOptions(),
                                    def.getIndexName(),
                                    componentIndex,
                                    kind);
    }

    public static UntypedResultSet resultify(Row serializedColumns)
    {
        String query = String.format("SELECT * FROM %s.%s", Keyspace.SYSTEM_KS, SystemKeyspace.SCHEMA_COLUMNS_CF);
        return QueryProcessor.resultify(query, serializedColumns);
    }

    /**
     * Deserialize columns from storage-level representation
     *
     * @param serializedColumns storage-level partition containing the column definitions
     * @return the list of processed ColumnDefinitions
     */
    public static List<ColumnDefinition> fromSchema(UntypedResultSet serializedColumns, String ksName, String cfName, AbstractType<?> rawComparator, boolean isSuper)
    {
        List<ColumnDefinition> cds = new ArrayList<>();
        for (UntypedResultSet.Row row : serializedColumns)
        {
            Kind kind = row.has(KIND)
                      ? Kind.deserialize(row.getString(KIND))
                      : Kind.REGULAR;

            Integer componentIndex = null;
            if (row.has(COMPONENT_INDEX))
                componentIndex = row.getInt(COMPONENT_INDEX);
            else if (kind == Kind.CLUSTERING_COLUMN && isSuper)
                componentIndex = 1; // A ColumnDefinition for super columns applies to the column component

            // Note: we save the column name as string, but we should not assume that it is an UTF8 name, we
            // we need to use the comparator fromString method
            AbstractType<?> comparator = getComponentComparator(rawComparator, componentIndex, kind);
            ColumnIdentifier name = new ColumnIdentifier(comparator.fromString(row.getString(COLUMN_NAME)), comparator);

            AbstractType<?> validator;
            try
            {
                validator = TypeParser.parse(row.getString(TYPE));
            }
            catch (RequestValidationException e)
            {
                throw new RuntimeException(e);
            }

            IndexType indexType = null;
            if (row.has(INDEX_TYPE))
                indexType = IndexType.valueOf(row.getString(INDEX_TYPE));

            Map<String, String> indexOptions = null;
            if (row.has(INDEX_OPTIONS))
                indexOptions = FBUtilities.fromJsonMap(row.getString(INDEX_OPTIONS));

            String indexName = null;
            if (row.has(INDEX_NAME))
                indexName = row.getString(INDEX_NAME);

            cds.add(new ColumnDefinition(ksName, cfName, name, validator, indexType, indexOptions, indexName, componentIndex, kind));
        }

        return cds;
    }

    public static AbstractType<?> getComponentComparator(AbstractType<?> rawComparator, Integer componentIndex, ColumnDefinition.Kind kind)
    {
        switch (kind)
        {
            case REGULAR:
                if (componentIndex == null || (componentIndex == 0 && !(rawComparator instanceof CompositeType)))
                    return rawComparator;

                return ((CompositeType)rawComparator).types.get(componentIndex);
            default:
                // CQL3 column names are UTF8
                return UTF8Type.instance;
        }
    }

    public String getIndexName()
    {
        return indexName;
    }

    public ColumnDefinition setIndexName(String indexName)
    {
        this.indexName = indexName;
        return this;
    }

    public ColumnDefinition setIndexType(IndexType indexType, Map<String,String> indexOptions)
    {
        this.indexType = indexType;
        this.indexOptions = indexOptions;
        return this;
    }

    public ColumnDefinition setIndex(String indexName, IndexType indexType, Map<String,String> indexOptions)
    {
        return setIndexName(indexName).setIndexType(indexType, indexOptions);
    }

    public boolean isIndexed()
    {
        return indexType != null;
    }

    public IndexType getIndexType()
    {
        return indexType;
    }

    public Map<String,String> getIndexOptions()
    {
        return indexOptions;
    }
}
