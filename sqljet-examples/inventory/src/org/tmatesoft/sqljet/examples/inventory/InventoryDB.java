/**
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
 */
package org.tmatesoft.sqljet.examples.inventory;

import java.io.File;
import java.util.Map;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.ISqlJetTransaction;
import org.tmatesoft.sqljet.core.table.SqlJetDb;

/**
 * @author Dmitry Stadnik (dtrace@seznam.cz)
 */
public class InventoryDB {

	private static final int VERSION = 1;
	private static final String FILE_NAME = "inventory.db";

	private static SqlJetDb db;

	public static void open() throws SqlJetException {
		db = SqlJetDb.open(new File(FILE_NAME), true);
		upgrade(VERSION);
	}

	private static void upgrade(int version) throws SqlJetException {
		if (version < 1) {
			return;
		}
		if (db.getOptions().getUserVersion() < 1) {
			db.runWriteTransaction(new ISqlJetTransaction() {

				public Object run(SqlJetDb db) throws SqlJetException {
					db.getSchema().createTable(
							"create table items (article int primary key, "
									+ "name text not null, description text, "
									+ "image blob, room int, shelf int, "
									+ "borrowed_from text, borrowed_to text)");
					db.getSchema().createIndex(
							"create index items_name on items (name)");
					db.getSchema().createIndex(
							"create index items_location "
									+ "on items (room, shelf)");
					db.getOptions().setUserVersion(1);
					prefillItems();
					return null;
				}
			});
		}
		if (version < 2) {
			return;
		}
		if (db.getOptions().getUserVersion() < 2) {
			db.runWriteTransaction(new ISqlJetTransaction() {

				public Object run(SqlJetDb db) throws SqlJetException {
					db.getSchema().createTable(
							"create table users (name text primary key, "
									+ "info text, rating real)");
					db.getOptions().setUserVersion(2);
					return null;
				}
			});
		}
		if (version > 2) {
			throw new IllegalArgumentException("Unsupported version: "
					+ version);
		}
	}

	public static void close() throws SqlJetException {
		db.close();
		db = null;
	}

	public static ISqlJetCursor getAllItems() throws SqlJetException {
		return db.getTable("items").open();
	}

	public static InventoryItem getItem(long article) throws SqlJetException {
		ISqlJetTable items = db.getTable("items");
		ISqlJetCursor cursor = items.lookup(items.getPrimaryKeyIndexName(),
				article);
		try {
			if (!cursor.eof()) {
				InventoryItem item = new InventoryItem();
				item.read(cursor);
				return item;
			}
		} finally {
			cursor.close();
		}
		return null;
	}

	public static long addItem(final InventoryItem item) throws SqlJetException {
		return item.article = (Long) db
				.runWriteTransaction(new ISqlJetTransaction() {

					public Object run(SqlJetDb db) throws SqlJetException {
						return db.getTable("items").insertAutoId(item.name,
								item.description, item.image, item.room,
								item.shelf, item.borrowedTo, item.borrowedFrom);
					}
				});
	}

	public static void updateItem(final long article,
			final Map<String, Object> values) throws SqlJetException {
		db.runWriteTransaction(new ISqlJetTransaction() {

			public Object run(SqlJetDb db) throws SqlJetException {
				ISqlJetTable items = db.getTable("items");
				ISqlJetCursor cursor = items.lookup(items
						.getPrimaryKeyIndexName(), article);
				try {
					if (!cursor.eof()) {
						cursor.updateByFieldNames(values);
					}
				} finally {
					cursor.close();
				}
				return null;
			}
		});
	}

	public static void removeItem(final long article) throws SqlJetException {
		db.runWriteTransaction(new ISqlJetTransaction() {

			public Object run(SqlJetDb db) throws SqlJetException {
				ISqlJetTable items = db.getTable("items");
				ISqlJetCursor cursor = items.lookup(items
						.getPrimaryKeyIndexName(), article);
				try {
					if (!cursor.eof()) {
						cursor.delete();
					}
				} finally {
					cursor.close();
				}
				return null;
			}
		});
	}

	private static void prefillItems() throws SqlJetException {
		addItem(new InventoryItem(-1, "MacBook", "Unibody 2GHz", null, 7, 23,
				"Dmitry Stadnik", null));
		addItem(new InventoryItem(-1, "iPhone 3G", "8Mb", null, 7, 24,
				"Dmitry Stadnik", null));
		addItem(new InventoryItem(-1, "Cup", "Big & White", null, 3, 1, null,
				"MG"));
	}
}
