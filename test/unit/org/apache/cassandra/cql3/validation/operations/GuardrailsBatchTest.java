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

package org.apache.cassandra.cql3.validation.operations;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;

import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.cql3.CQLTester;
import org.apache.cassandra.cql3.QueryOptions;
import org.apache.cassandra.exceptions.InvalidRequestException;
import org.apache.cassandra.transport.ProtocolVersion;
import org.apache.cassandra.transport.SimpleClient;
import org.apache.cassandra.transport.messages.QueryMessage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;

public class GuardrailsBatchTest extends CQLTester
{
    @Test
    public void testBatchWithDeletesForPrimaryKeyWithClusteringColumns() throws Throwable
    {
        requireNetwork();

        String table = createTable("CREATE TABLE %s (pk int, value1 text, value2 int, PRIMARY KEY (pk, value1))");

        int keySize = 1024 * 10;
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 3; i++)
        {
            String key = RandomStringUtils.randomAlphanumeric(keySize);
            keys.add(key);
            execute("INSERT INTO %s (pk, value1, value2) VALUES (?, ?, ?) IF NOT EXISTS", i, key, i);
        }

        assertEquals(3, execute("SELECT * FROM %s").size());

        DatabaseDescriptor.setBatchSizeWarnThresholdInKiB(1);
        DatabaseDescriptor.setBatchSizeFailThresholdInKiB(keySize / 1024);

        String batch = "BEGIN BATCH\n" +
                       "DELETE FROM %s WHERE pk = %s AND value1 = '%s';\n" +
                       "DELETE FROM %s WHERE pk = %s AND value1 = '%s';\n" +
                       "DELETE FROM %s WHERE pk = %s AND value1 = '%s';\n" +
                       "APPLY BATCH";

        try (SimpleClient client = new SimpleClient(nativeAddr.getHostAddress(), nativePort, ProtocolVersion.V4))
        {
            client.connect(false);
            String keyspaceTableName = KEYSPACE + '.' + table;
            QueryMessage query = new QueryMessage(String.format(batch,
                                                                keyspaceTableName, "0", keys.get(0),
                                                                keyspaceTableName, "1", keys.get(1),
                                                                keyspaceTableName, "2", keys.get(2)),
                                                  QueryOptions.DEFAULT);

            assertThatThrownBy(() -> client.execute(query))
                               .hasCauseInstanceOf(InvalidRequestException.class)
                               .hasMessageContaining("Batch too large");
        }

        assertEquals(3, execute("SELECT * FROM %s").size());
    }

    @Test
    public void testBatchWithDeletesForSimplePrimaryKeys() throws Throwable
    {
        requireNetwork();

        String table = createTable("CREATE TABLE %s (pk text, value1 int, PRIMARY KEY (pk))");

        int keySize = 1024 * 10;
        List<String> keys = new ArrayList<>();


        for (int i = 0; i < 3; i++)
        {
            String key = RandomStringUtils.randomAlphanumeric(keySize);
            keys.add(key);
            execute("INSERT INTO %s (pk, value1) VALUES (?, ?) IF NOT EXISTS", key, i);
        }

        assertEquals(3, execute("SELECT * FROM %s").size());

        DatabaseDescriptor.setBatchSizeWarnThresholdInKiB(1);
        DatabaseDescriptor.setBatchSizeFailThresholdInKiB(keySize / 1024);

        String batch = "BEGIN BATCH\n" +
                       "DELETE FROM %s WHERE pk = '%s';\n" +
                       "DELETE FROM %s WHERE pk = '%s';\n" +
                       "DELETE FROM %s WHERE pk = '%s';\n" +
                       "APPLY BATCH";

        try (SimpleClient client = new SimpleClient(nativeAddr.getHostAddress(), nativePort, ProtocolVersion.V4))
        {
            client.connect(false);
            String keyspaceTableName = KEYSPACE + '.' + table;
            QueryMessage query = new QueryMessage(String.format(batch,
                                                                keyspaceTableName, keys.get(0),
                                                                keyspaceTableName, keys.get(1),
                                                                keyspaceTableName, keys.get(2)),
                                                  QueryOptions.DEFAULT);

            assertThatThrownBy(() -> client.execute(query))
                               .hasCauseInstanceOf(InvalidRequestException.class)
                               .hasMessageContaining("Batch too large");
        }

        assertEquals(3, execute("SELECT * FROM %s").size());
    }

    @Test
    public void testBatchWithInsertsForSimplePrimaryKeys() throws Throwable
    {
        requireNetwork();

        String table = createTable("CREATE TABLE %s (pk text, value1 int, PRIMARY KEY (pk))");

        int keySize = 1024 * 10;
        List<String> keys = new ArrayList<>();

        for (int i = 0; i < 3; i++)
        {
            String key = RandomStringUtils.randomAlphanumeric(keySize);
            keys.add(key);
        }

        assertEquals(0, execute("SELECT * FROM %s").size());

        DatabaseDescriptor.setBatchSizeWarnThresholdInKiB(1);
        DatabaseDescriptor.setBatchSizeFailThresholdInKiB(keySize);

        String batch = "BEGIN BATCH\n" +
                       "INSERT INTO %s (pk, value1) VALUES ('%s', %s);\n" +
                       "INSERT INTO %s (pk, value1) VALUES ('%s', %s);\n" +
                       "INSERT INTO %s (pk, value1) VALUES ('%s', %s);\n" +
                       "APPLY BATCH";

        try (SimpleClient client = new SimpleClient(nativeAddr.getHostAddress(), nativePort, ProtocolVersion.V4))
        {
            client.connect(false);
            String keyspaceTableName = KEYSPACE + '.' + table;
            String fullBatch = String.format(batch,
                                             keyspaceTableName, keys.get(0), "0",
                                             keyspaceTableName, keys.get(1), "1",
                                             keyspaceTableName, keys.get(2), "2");
            QueryMessage query = new QueryMessage(fullBatch, QueryOptions.DEFAULT);

            assertThatThrownBy(() -> client.execute(query))
                                .hasCauseInstanceOf(InvalidRequestException.class)
                                .hasMessageContaining("Batch too large");
        }

        assertEquals(0, execute("SELECT * FROM %s").size());
    }
}

