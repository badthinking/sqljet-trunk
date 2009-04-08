/*
 * sqlite_test.h
 *
 *  Created on: 06.04.2009
 *      Author: TMate Software Ltd.
 *      Author: Sergey Scherbina (sergey.scherbina@gmail.com)
 */

#ifndef SQLITE_TEST_H_
#define SQLITE_TEST_H_

#include <stdio.h>
#include <stdlib.h>

#include "./tsrc/sqlite3.h"
#include "./tsrc/sqliteInt.h"
#include "./tsrc/btree.h"
#include "./tsrc/btreeInt.h"
#include "./tsrc/pager.h"


#define SQLJET_ROOT "/home/sergey/work2/sqljet/workspace/org.tmatesoft.sqljet.trunk/"

#define TEST_DB SQLJET_ROOT "sqljet-test/db/testdb.sqlite"

#define WRITE_FILE "/tmp/write.native"
#define DELETE_FILE "/tmp/delete.native"


void testRead(void);
void testWrite(void);
void testDelete(void);

#endif /* SQLITE_TEST_H_ */
