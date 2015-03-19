/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.basho.riak.presto;

import com.facebook.presto.spi.ColumnHandle;
import com.facebook.presto.spi.ColumnMetadata;
import com.facebook.presto.spi.type.BigintType;
import com.facebook.presto.spi.type.VarcharType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.log.Logger;

import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;

// Presto-Riak style table, stored in Riak and also exchanged between presto nodes
public class PRTable {
    private static final Logger log = Logger.get(PRTable.class);
    private final String name;
    private final List<RiakColumn> columns;
    private final Optional<String> comments;
    private final Optional<List<PRSubTable>> subtables;
    private String pkey;

    @JsonCreator
    public PRTable(
            @JsonProperty(value = "name", required = true) String name,
            @JsonProperty(value = "columns", required = true) List<RiakColumn> columns,
            @JsonProperty(value = "comments", required = false) String comments,
            @JsonProperty(value = "subtables") List<PRSubTable> subtables) {
        checkArgument(!isNullOrEmpty(name), "name is null or is empty");
        this.name = checkNotNull(name, "name is null");
        this.columns = ImmutableList.copyOf(checkNotNull(columns, "columns is null"));
        this.comments = Optional.ofNullable(comments);
        this.subtables = Optional.ofNullable(ImmutableList.copyOf(subtables));

        for (RiakColumn column : this.columns) {
            System.out.println(">"+column.getPkey() + " " + column.getType() + " " + pkey);
            if (column.getPkey() &&
                    column.getType() == VarcharType.VARCHAR &&
                    this.pkey == null)
            {
                this.pkey = column.getName();
            }
        }
        if (pkey == null) {
            log.warn("Primary Key is not defined in columns effectively. Some queries become slow.");
        }

        // TODO: verify all subtable definitions here
    }

    public static Function<PRTable, String> nameGetter() {
        return new Function<PRTable, String>() {
            @Override
            public String apply(PRTable table) {
                return table.getName();
            }
        };
    }

    public static PRTable example(String tableName) {
        List<RiakColumn> cols = Arrays.asList(
                new RiakColumn("col1", VarcharType.VARCHAR, "d1vv", false, true),
                new RiakColumn("col2", VarcharType.VARCHAR, "d2", true, false),
                new RiakColumn("poopie", BigintType.BIGINT, "d3", true, false));
        return new PRTable(tableName, cols, "coment", new ArrayList<>());

    }

    @JsonProperty
    public String getName() {
        return name;
    }

    @JsonProperty
    public List<RiakColumn> getColumns() {
        return columns;
    }

    @JsonProperty
    public List<PRSubTable> getSubtables() { return subtables.get(); }

    @JsonProperty
    public String getComments() { return comments.get(); }

    public List<ColumnMetadata> getColumnsMetadata(String connectorId){
        Map<String, ColumnHandle> columnHandles = getColumnHandles(connectorId);
        ImmutableList.Builder<ColumnMetadata> builder = ImmutableList.builder();
        for (ColumnHandle columnHandle : columnHandles.values()){
            RiakColumnHandle handle = (RiakColumnHandle) columnHandle;
            boolean hidden = false;
            if ( handle.getOrdinalPosition() < 2 )
                hidden = true;
            builder.add(new ColumnMetadata(handle.getColumn().getName(),
                    handle.getColumn().getType(),
                    handle.getOrdinalPosition(), handle.getColumn().getIndex(),
                    handle.getColumn().getComment(), hidden));

        }
        return builder.build();
    }

    public Map<String, ColumnHandle> getColumnHandles(String connectorId) {
        //TODO: For now we assume keys are all in UTF-8
        ImmutableMap.Builder<String, ColumnHandle> columnHandles = ImmutableMap.builder();

        columnHandles.put(RiakColumnHandle.PKEY_COLUMN_NAME,
                new RiakColumnHandle(connectorId,
                        new RiakColumn(RiakColumnHandle.PKEY_COLUMN_NAME,
                                VarcharType.VARCHAR, pkey, true, true),
                        0));
        //columnsMetadata.add(new ColumnMetadata(RiakColumnHandle.PKEY_COLUMN_NAME, VarcharType.VARCHAR, 0, true, pkey, true));
        columnHandles.put(RiakColumnHandle.VTAG_COLUMN_NAME,
                new RiakColumnHandle(connectorId,
                        new RiakColumn(RiakColumnHandle.VTAG_COLUMN_NAME,
                                VarcharType.VARCHAR, "vtag", false, false),
                        1));
        //columnsMetadata.add(new ColumnMetadata(RiakColumnHandle.VTAG_COLUMN_NAME, VarcharType.VARCHAR, 1, false, "vtag", true));
        int index = 2;

        for (RiakColumn column : this.columns) {
            // Column metadata should remember whichi is primary key, but for now it's
            // impossible, as Presto database has no concept of primary key like this.
            columnHandles.put(column.getName(),
                    new RiakColumnHandle(connectorId, column, index));
            //columnsMetadata.add(new ColumnMetadata(column.getName(), column.getType(),
            //        index, column.getIndex(), column.getComment(), false));
            index++;
        }
        return columnHandles.build();
    }
}
