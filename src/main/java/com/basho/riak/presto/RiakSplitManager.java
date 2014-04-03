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

import com.ericsson.otp.erlang.OtpAuthException;
import com.ericsson.otp.erlang.OtpErlangDecodeException;
import com.ericsson.otp.erlang.OtpErlangExit;
import com.ericsson.otp.erlang.OtpErlangObject;
import com.facebook.presto.spi.ConnectorSplitManager;
import com.facebook.presto.spi.FixedSplitSource;
import com.facebook.presto.spi.Partition;
import com.facebook.presto.spi.PartitionResult;
import com.facebook.presto.spi.Split;
import com.facebook.presto.spi.SplitSource;
import com.facebook.presto.spi.TableHandle;
import com.facebook.presto.spi.TupleDomain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.airlift.log.Logger;

import javax.inject.Inject;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class RiakSplitManager
        implements ConnectorSplitManager
{
    private final String connectorId;
    private final RiakClient riakClient;
    private final RiakConfig riakConfig;
    private final DirectConnection directConnection;

    private static final Logger log = Logger.get(RiakSplitManager.class);


    @Inject
    public RiakSplitManager(RiakConnectorId connectorId, RiakClient riakClient,
                            RiakConfig config, DirectConnection directConnection)
    {
        this.connectorId = checkNotNull(connectorId, "connectorId is null").toString();
        this.riakClient = checkNotNull(riakClient, "client is null");
        this.riakConfig = checkNotNull(config);
        this.directConnection = checkNotNull(directConnection);
    }

    @Override
    public String getConnectorId()
    {
        return connectorId;
    }

    @Override
    public boolean canHandle(TableHandle tableHandle)
    {
        return tableHandle instanceof RiakTableHandle && ((RiakTableHandle) tableHandle).getConnectorId().equals(connectorId);
    }

    // TODO: get the right partitions right here
    @Override
    public PartitionResult getPartitions(TableHandle tableHandle, TupleDomain tupleDomain)
    {
        log.debug("splitter");
        checkArgument(tableHandle instanceof RiakTableHandle, "tableHandle is not an instance of RiakTableHandle");
        RiakTableHandle RiakTableHandle = (RiakTableHandle) tableHandle;

        log.info("==========================tupleDomain=============================");
        log.info(tupleDomain.toString());

        // Riak connector has only one partition
        List<Partition> partitions = ImmutableList.<Partition>of(new RiakPartition(RiakTableHandle.getSchemaName(), RiakTableHandle.getTableName()));
        // Riak connector does not do any additional processing/filtering with the TupleDomain, so just return the whole TupleDomain
        return new PartitionResult(partitions, tupleDomain);
    }

    // TODO: return correct splits from partitions
    @Override
    public SplitSource getPartitionSplits(TableHandle tableHandle, List<Partition> partitions)
    {
        checkNotNull(partitions, "partitions is null");
        checkArgument(partitions.size() == 1, "Expected one partition but got %s", partitions.size());
        Partition partition = partitions.get(0);

        checkArgument(partition instanceof RiakPartition, "partition is not an instance of RiakPartition");
        RiakPartition RiakPartition = (RiakPartition) partition;

        RiakTableHandle riakTableHandle = (RiakTableHandle) tableHandle;
        RiakTable table = //RiakTable.example(riakTableHandle.getTableName());
                riakClient.getTable(riakTableHandle.getSchemaName(), riakTableHandle.getTableName());
        // this can happen if table is removed during a query
        checkState(table != null, "Table %s.%s no longer exists", riakTableHandle.getSchemaName(), riakTableHandle.getTableName());

        // add all nodes at the cluster here
        List<Split> splits = Lists.newArrayList();
        String hosts = riakClient.getHosts();

        if(riakConfig.getLocalNode() != null)
        {
            // TODO: make coverageSplits here
            //log.debug("connecting to %s from %s", riak, self);
            //try {
                DirectConnection conn = directConnection;
                //conn.connect(riak);
                //conn.ping();
                Coverage coverage = new Coverage(conn);
                coverage.plan();
                List<SplitTask> splitTasks = coverage.getSplits();

                log.debug("print coverage plan==============");
                log.debug(coverage.toString());


                for(SplitTask split : splitTasks)
                {
                    log.debug("============printing split data at "+split.getHost()+"===============");
                    log.debug(((OtpErlangObject)split.getTask()).toString());
                    log.debug(split.toString());

                    //split.fetchAllData(conn, "default", "foobartable");

                    splits.add(new CoverageSplit(connectorId,
                            riakTableHandle.getSchemaName(),
                            riakTableHandle.getTableName(),
                            split.getHost(),
                            split.toString()));
                }
            //}
//            catch (java.io.IOException e){
//                log.error(e);
//            }
//            catch (OtpAuthException e){
//                log.error(e);
//            }
//            catch (OtpErlangExit e)
//            {
//                log.error(e);
//            }
        }
        else
        {
            // TODO: in Riak connector, you only need single access point for each presto worker???
            log.debug(hosts);
            splits.add(new CoverageSplit(connectorId, riakTableHandle.getSchemaName(),
                    riakTableHandle.getTableName(), hosts));

        }
        log.debug("table %s.%s has %d splits.",
                riakTableHandle.getSchemaName(), riakTableHandle.getTableName(),
                splits.size());

        Collections.shuffle(splits);
        return new FixedSplitSource(connectorId, splits);
    }
}
