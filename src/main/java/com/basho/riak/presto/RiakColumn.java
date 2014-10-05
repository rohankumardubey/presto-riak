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

import com.facebook.presto.spi.type.*;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import io.airlift.log.Logger;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;

public final class RiakColumn {
    private static final Logger log = Logger.get(RiakRecordSetProvider.class);
    private final String name;
    private final String type;
    private final Type spiType;
    private boolean index;

    public RiakColumn(String name, Type type, boolean index) {
        this.name = name;
        this.spiType = type;
        this.type = this.spiType.getName();
        this.index = index;
    }

    @JsonCreator
    public RiakColumn(
            @JsonProperty("name") String name,
            @JsonProperty("type") String type,
            @JsonProperty("index") boolean index) {
        checkArgument(!isNullOrEmpty(name), "name is null or is empty");
        this.name = name;
        this.type = checkNotNull(type, "type is null");
        this.index = index;
        this.spiType = toSpiType(type);
    }

    Type toSpiType(String t) {

        if (t.equals("LONG")) {
            return BigintType.BIGINT;
        } else if (t.equals("STRING")) {
            return VarcharType.VARCHAR;
        } else if (t.equals("varchar")) {
            return VarcharType.VARCHAR;
        } else if (t.equals(BigintType.BIGINT.getName())) {
            return BigintType.BIGINT;
        } else if (t.equals(BooleanType.BOOLEAN.getName())) {
            return BooleanType.BOOLEAN;
        } else if (t.equals(DateType.DATE.getName())) {
            return DateType.DATE;
        } else if (t.equals(DoubleType.DOUBLE.getName())) {
            return DoubleType.DOUBLE;
        } else if (t.equals(HyperLogLogType.HYPER_LOG_LOG.getName())) {
            return HyperLogLogType.HYPER_LOG_LOG;
        } else if (t.equals(IntervalDayTimeType.INTERVAL_DAY_TIME.getName())) {
            return IntervalDayTimeType.INTERVAL_DAY_TIME;
        } else if (t.equals(IntervalYearMonthType.INTERVAL_YEAR_MONTH.getName())) {
            return IntervalYearMonthType.INTERVAL_YEAR_MONTH;
        } else if (t.equals(TimestampType.TIMESTAMP.getName())) {
            return TimestampType.TIMESTAMP;
        }
        log.error("unknown type in table schema: %s", t);
        return null;
    }

    @JsonProperty
    public String getName() {
        return name;
    }

    @JsonProperty
    public String getType() {
        return type;
    }

    public Type spiType() {
        return spiType;
    }

    @JsonProperty
    public boolean getIndex() {
        return index;
    }

    public void setIndex(boolean b) {
        this.index = b;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, type);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        RiakColumn other = (RiakColumn) obj;
        return Objects.equal(this.name, other.name) &&
                (this.index == other.index) &&
                Objects.equal(this.type, other.type);
    }

    @Override
    public String toString() {
        return name + ":" + type + "(index=" + index + ")";
    }
}
