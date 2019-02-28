/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.shardingjdbc.jdbc.core.statement;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.shardingsphere.core.optimizer.OptimizeEngineFactory;
import org.apache.shardingsphere.core.parsing.parser.sql.SQLStatement;
import org.apache.shardingsphere.core.rewrite.EncryptSQLRewriteEngine;
import org.apache.shardingsphere.core.rewrite.SQLBuilder;
import org.apache.shardingsphere.core.routing.SQLUnit;
import org.apache.shardingsphere.shardingjdbc.jdbc.adapter.AbstractShardingPreparedStatementAdapter;
import org.apache.shardingsphere.shardingjdbc.jdbc.core.connection.EncryptConnection;
import org.apache.shardingsphere.shardingjdbc.jdbc.core.resultset.EncryptResultSet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.LinkedList;

/**
 * Encrypt prepared statement.
 *
 * @author panjuan
 */
public final class EncryptPreparedStatement extends AbstractShardingPreparedStatementAdapter {
    
    private final String sql;
    
    private final EncryptPreparedStatementGenerator preparedStatementGenerator;
    
    private final Collection<SQLUnit> sqlUnits = new LinkedList<>();
    
    private PreparedStatement originalPreparedStatement;
    
    private EncryptResultSet resultSet;
    
    @SneakyThrows
    public EncryptPreparedStatement(final EncryptConnection connection, final String sql) {
        this.sql = sql;
        preparedStatementGenerator = new EncryptPreparedStatementGenerator(connection);
    }
    
    @SneakyThrows
    public EncryptPreparedStatement(final EncryptConnection connection, final String sql, final int resultSetType, final int resultSetConcurrency) {
        this.sql = sql;
        preparedStatementGenerator = new EncryptPreparedStatementGenerator(connection, resultSetType, resultSetConcurrency);
    }
    
    @SneakyThrows
    public EncryptPreparedStatement(final EncryptConnection connection, final String sql, final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability) {
        this.sql = sql;
        preparedStatementGenerator = new EncryptPreparedStatementGenerator(connection, resultSetType, resultSetConcurrency, resultSetHoldability);
    }
    
    @SneakyThrows
    public EncryptPreparedStatement(final EncryptConnection connection, final String sql, final int autoGeneratedKeys) {
        this.sql = sql;
        preparedStatementGenerator = new EncryptPreparedStatementGenerator(connection, autoGeneratedKeys);
    }
    
    @SneakyThrows
    public EncryptPreparedStatement(final EncryptConnection connection, final String sql, final int[] columnIndexes) {
        this.sql = sql;
        preparedStatementGenerator = new EncryptPreparedStatementGenerator(connection, columnIndexes);
    }
    
    @SneakyThrows
    public EncryptPreparedStatement(final EncryptConnection connection, final String sql, final String[] columnNames) {
        this.sql = sql;
        preparedStatementGenerator = new EncryptPreparedStatementGenerator(connection, columnNames);
    }
    
    @Override
    public ResultSet executeQuery() {
        try {
            SQLUnit sqlUnit = getSQLUnit(sql);
            originalPreparedStatement = preparedStatementGenerator.createPreparedStatement(sqlUnit.getSql());
            replaySetParameter(originalPreparedStatement, sqlUnit.getParameterSets().get(0));
            this.resultSet = new EncryptResultSet(this, resultSet, preparedStatementGenerator.connection.getEncryptRule());
            return resultSet;
        } finally {
            clearParameters();
        }
    }
    
    private SQLUnit getSQLUnit(final String sql) {
        EncryptConnection connection = preparedStatementGenerator.connection;
        SQLStatement sqlStatement = connection.getEncryptSQLParsingEngine().parse(false, sql);
        OptimizeEngineFactory.newInstance(connection.getEncryptRule(), sqlStatement, getParameters()).optimize();
        SQLBuilder sqlBuilder = new EncryptSQLRewriteEngine(connection.getEncryptRule(), sql, connection.getDatabaseType(), sqlStatement, getParameters()).rewrite();
        return sqlBuilder.toSQL();
    }
    
    @Override
    public ResultSet getResultSet() {
        return resultSet;
    }
    
    @Override
    public int executeUpdate() throws SQLException {
        return 0;
    }
    
    @Override
    public boolean execute() throws SQLException {
        return false;
    }
    
    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        return originalPreparedStatement.getGeneratedKeys();
    }
    
    @Override
    public void addBatch() {
        sqlUnits.add(getSQLUnit(sql));
        clearParameters();
    }
    
    @Override
    public int[] executeBatch() throws SQLException {
        try {
            originalPreparedStatement = preparedStatementGenerator.createPreparedStatement(sqlUnits.iterator().next().getSql());
            replayBatchPreparedStatement();
            return originalPreparedStatement.executeBatch();
        } finally {
            clearBatch();
        }
    }
    
    private void replayBatchPreparedStatement() throws SQLException {
        for (SQLUnit each : sqlUnits) {
            replaySetParameter(originalPreparedStatement, each.getParameterSets().get(0));
            originalPreparedStatement.addBatch();
        }
    }
    
    @Override
    public void clearBatch() {
        resultSet = null;
        clearParameters();
    }
    
    @Override
    public Connection getConnection() {
        return preparedStatementGenerator.connection;
    }
    
    @Override
    public int getResultSetConcurrency() {
        return preparedStatementGenerator.resultSetConcurrency;
    }
    
    @Override
    public int getResultSetType() {
        return preparedStatementGenerator.resultSetType;
    }
    
    @Override
    public int getResultSetHoldability() {
        return preparedStatementGenerator.resultSetHoldability;
    }
    
    @Override
    protected boolean isAccumulate() {
        return false;
    }
    
    @Override
    protected Collection<? extends Statement> getRoutedStatements() {
        return null;
    }
    
    @RequiredArgsConstructor
    private final class EncryptPreparedStatementGenerator {
    
        private final EncryptConnection connection;
    
        private final int resultSetType;
    
        private final int resultSetConcurrency;
    
        private final int resultSetHoldability;
    
        private final int autoGeneratedKeys;
    
        private final int[] columnIndexes;
    
        private final String[] columnNames;
        
        private EncryptPreparedStatementGenerator(final EncryptConnection connection) {
            this(connection, -1, -1, -1, -1, null, null);
        }
        
        private EncryptPreparedStatementGenerator(final EncryptConnection connection, final int resultSetType, final int resultSetConcurrency) {
            this(connection, resultSetType, resultSetConcurrency, -1, -1, null, null);
        }
    
        private EncryptPreparedStatementGenerator(final EncryptConnection connection, final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability) {
            this(connection, resultSetType, resultSetConcurrency, resultSetHoldability, -1, null, null);
        }
    
        private EncryptPreparedStatementGenerator(final EncryptConnection connection, final int autoGeneratedKeys) {
            this(connection, -1, -1, -1, autoGeneratedKeys, null, null);
        }
    
        private EncryptPreparedStatementGenerator(final EncryptConnection connection, final int[] columnIndexes) {
            this(connection, -1, -1, -1, -1, columnIndexes, null);
        }
    
        private EncryptPreparedStatementGenerator(final EncryptConnection connection, final String[] columnNames) {
            this(connection, -1, -1, -1, -1, null, columnNames);
        }
        
        private PreparedStatement createPreparedStatement(final String sql) {
            if (-1 != resultSetType && -1 != resultSetConcurrency && -1 != resultSetHoldability) {
                return connection.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
            }
            if (-1 != resultSetType && -1 != resultSetConcurrency) {
                return connection.prepareStatement(sql, resultSetType, resultSetConcurrency);
            }
            if (-1 != autoGeneratedKeys) {
                return connection.prepareStatement(sql, autoGeneratedKeys);
            }
            if (null != columnIndexes) {
                return connection.prepareStatement(sql, columnIndexes);
            }
            if (null != columnNames) {
                return connection.prepareStatement(sql, columnNames);
            }
            return connection.prepareStatement(sql);
        }
    }
}
