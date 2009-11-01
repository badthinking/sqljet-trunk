/**
 * ConcurrenySchemaModification.java
 * Copyright (C) 2009 TMate Software Ltd
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * For information on how to redistribute this software under
 * the terms of a license other than GNU General Public License
 * contact TMate Software at support@sqljet.com
 */
package org.tmatesoft.sqljet.core.schema;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.junit.Test;
import org.tmatesoft.sqljet.core.AbstractNewDbTest;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.table.ISqlJetTransaction;
import org.tmatesoft.sqljet.core.table.SqlJetDb;

/**
 * @author TMate Software Ltd.
 * @author Sergey Scherbina (sergey.scherbina@gmail.com)
 * 
 */
public class ConcurrentSchemaModification extends AbstractNewDbTest {

    private ExecutorService threadPool;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        threadPool = Executors.newCachedThreadPool();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.AbstractNewDbTest#tearDown()
     */
    @Override
    public void tearDown() throws Exception {
        try {
            threadPool.awaitTermination(10, TimeUnit.SECONDS);
        } finally {
            super.tearDown();
        }
    }

    private class TransactionTask implements Runnable {

        protected String taskName;
        protected boolean run = true;

        public TransactionTask(String taskName) {
            this.taskName = taskName;
        }

        public void run() {
            int i = 0;
            final Thread currentThread = Thread.currentThread();
            final String threadName = currentThread.getName();
            try {
                currentThread.setName(taskName);
                while (run) {
                    try {
                        SqlJetDb db = null;
                        try {
                            final int n = i++;
                            db = SqlJetDb.open(file, true);
                            db.runWriteTransaction(new ISqlJetTransaction() {
                                public Object run(SqlJetDb db) throws SqlJetException {
                                    return workInTransaction(db, n);
                                }
                            });
                        } finally {
                            if (db != null) {
                                db.close();
                            }
                        }
                    } catch (SqlJetException e) {
                        logger.log(Level.INFO, taskName, e);
                    }
                }
            } catch (Exception e) {
                logger.log(Level.INFO, taskName, e);
            } finally {
                currentThread.setName(threadName);
            }
        }

        protected Object workInTransaction(SqlJetDb db, int n) throws SqlJetException {
            return null;
        }

        public void kill() {
            run = false;
        }                
        
    }

    private class SleepTask extends TransactionTask {

        public SleepTask(String taskName) {
            super(taskName);
        }

        @Override
        protected Object workInTransaction(SqlJetDb db, int n) throws SqlJetException {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                logger.log(Level.INFO, taskName, e);
            }
            return null;
        }

    }

    private class SchemaModificationTask extends TransactionTask {

        private static final String CREATE = "create table %s%d(a int);";

        public SchemaModificationTask(String taskName) {
            super(taskName);
        }

        @Override
        protected Object workInTransaction(SqlJetDb db, int n) throws SqlJetException {
            db.createTable(String.format(CREATE, taskName, n));
            return null;
        }

    }

    @Test
    public void concurrentTransactions() throws Exception {
        final TransactionTask task1 = new TransactionTask("thread1");
        final TransactionTask task2 = new TransactionTask("thread2");
        final TransactionTask task3 = new TransactionTask("thread3");
        try {
            threadPool.submit(task1);
            threadPool.submit(task2);
            threadPool.submit(task3);
            Thread.sleep(10000);
        } finally {
            task1.kill();
            task2.kill();
            task3.kill();
        }
    }

    @Test
    public void concurrentSleep() throws Exception {
        final TransactionTask task1 = new SleepTask("thread1");
        final TransactionTask task2 = new SleepTask("thread2");
        final TransactionTask task3 = new SleepTask("thread3");
        try {
            threadPool.submit(task1);
            threadPool.submit(task2);
            threadPool.submit(task3);
            Thread.sleep(10000);
        } finally {
            task1.kill();
            task2.kill();
            task3.kill();
        }
    }

    @Test
    public void concurrentSchemaModification() throws Exception {
        final TransactionTask task1 = new SchemaModificationTask("thread1");
        final TransactionTask task2 = new SchemaModificationTask("thread2");
        final TransactionTask task3 = new SchemaModificationTask("thread3");
        try {
            threadPool.submit(task1);
            threadPool.submit(task2);
            threadPool.submit(task3);
            Thread.sleep(10000);
        } finally {
            task1.kill();
            task2.kill();
            task3.kill();
        }
    }
}
