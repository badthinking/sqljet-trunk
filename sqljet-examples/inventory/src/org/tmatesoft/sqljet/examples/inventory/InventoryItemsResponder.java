package org.tmatesoft.sqljet.examples.inventory;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;

public class InventoryItemsResponder {

	public void showInventory(StringBuffer buffer, Map<String, String> params) throws SqlJetException {
		long room = -1, shelf = -1;
		String roomStr = params.get("room");
		if (roomStr != null) {
			room = Long.parseLong(roomStr);
		}
		String shelfStr = params.get("shelf");
		if (shelfStr != null) {
			shelf = Long.parseLong(shelfStr);
		}
		Boolean namesAsc = null;
		String namesStr = params.get("names");
		if ("asc".equalsIgnoreCase(namesStr)) {
			namesAsc = true;
		} else if ("desc".equalsIgnoreCase(namesStr)) {
			namesAsc = false;
		}
		buffer.append("<h2>Inventory</h2>");
		InventoryDB db = new InventoryDB();
		try {
			ISqlJetCursor cursor;
			if (room >= 0 && shelf >= 0) {
				cursor = db.getAllItemsInRoomOnShelf(room, shelf);
			} else if (namesAsc != null) {
				cursor = db.getAllItemsSortedByName(namesAsc);
			} else {
				cursor = db.getAllItems();
			}
			try {
				printItems(db, buffer, cursor, namesAsc, room, shelf);
			} finally {
				cursor.close();
			}
		} finally {
			db.close();
		}
		buffer.append("<p><a href='/add_item'>Add Item</a>");
	}

	private void printItems(InventoryDB db, StringBuffer buffer, ISqlJetCursor cursor, Boolean namesAsc, long room, long shelf) throws SqlJetException {
		buffer.append("<table class='items'><tr><th>Article</th><th>Name</th><th>Description</th><th>Room</th><th>Shelf</th>");
		if (db.getVersion() > 1) {
			buffer.append("<th>Borrowed To</th><th>Borrowed From</th>");
		}
		buffer.append("<th colspan='2'>Action</th><tr>");
		InventoryItem item = new InventoryItem();
		while (!cursor.eof()) {
			item.read(cursor);
			buffer.append("<tr>");
			printValue(buffer, item.article);
			printValue(buffer, item.name);
			printValue(buffer, item.description);
			printValue(buffer, item.room);
			printValue(buffer, item.shelf);
			if (db.getVersion() > 1) {
				printValue(buffer, item.borrowedFrom);
				printValue(buffer, item.borrowedTo);
			}
			buffer.append("<td><a href='/edit_item?article=");
			buffer.append(item.article);
			buffer.append("'>Edit</a></td>");
			buffer.append("<td><a href='/remove_item?article=");
			buffer.append(item.article);
			buffer.append("'>Remove</a></td>");
			buffer.append("</tr>");
			cursor.next();
		}
		buffer.append("<tr><form><td colspan='5' class='filter'>Order by names:&nbsp;<select name='names'>");
		buffer.append("<option value=''");
		if (namesAsc == null) {
			buffer.append(" selected");
		}
		buffer.append(">Unordered</option>");
		buffer.append("<option value='asc'");
		if (namesAsc == Boolean.TRUE) {
			buffer.append(" selected");
		}
		buffer.append(">Ascending</option>");
		buffer.append("<option value='desc'");
		if (namesAsc == Boolean.FALSE) {
			buffer.append(" selected");
		}
		buffer.append(">Descending</option>");
		buffer.append("</select></td><td colspan='2' class='filter'><input type='submit' value='Apply'/></td></form></tr>");
		buffer.append("<tr><form><td colspan='3' class='filter'>Find in the room on the shelf:</td><td class='filter'><input type='text' name='room' size='3'");
		if (room >= 0) {
			buffer.append(" value='");
			buffer.append(room);
			buffer.append("'");
		}
		buffer.append("/></td><td class='filter'><input type='text' name='shelf' size='3'");
		if (shelf >= 0) {
			buffer.append(" value='");
			buffer.append(shelf);
			buffer.append("'");
		}
		buffer.append("/></td><td colspan='2' class='filter'><input type='submit' value='Apply'/></td></form></tr>");
		buffer.append("<tr><td colspan='5' class='filter'></td><td colspan='2' class='filter'><a href='/'>Reset</a></td></tr>");
		buffer.append("</table>");
	}

	private void printValue(StringBuffer buffer, Object value) {
		buffer.append("<td");
		if (value instanceof Number) {
			buffer.append(" style='text-align: right;'");
		}
		buffer.append(">");
		if (value != null) {
			buffer.append(value);
		} else {
			buffer.append("&nbsp;");
		}
		buffer.append("</td>");
	}

	private void printReturnToInventory(StringBuffer buffer) {
		buffer.append("<hr><a href='/'>Return to inventory</a>");
	}

	public void showAddForm(StringBuffer buffer) {
		buffer.append("<h2>Add Item</h2><p>");
		buffer.append("<form><table>");
		buffer.append("<tr><td>Name:</td><td><input type='text' name='name'/></td></tr>");
		buffer.append("<tr><td>Description:</td><td><input type='text' name='description'/></td></tr>");
		buffer.append("<tr><td>Room:</td><td><input type='text' name='room' style='text-align: right;'/></td></tr>");
		buffer.append("<tr><td>Shelf:</td><td><input type='text' name='shelf' style='text-align: right;'/></td></tr>");
		buffer.append("</table><input type='submit' value='Submit'></form>");
		printReturnToInventory(buffer);
	}

	public void processAddForm(StringBuffer buffer, Map<String, String> params) throws SqlJetException {
		try {
			InventoryItem item = new InventoryItem();
			item.name = params.get("name");
			if (item.name == null) {
				throw new IllegalArgumentException("Name is not specified.");
			}
			item.description = params.get("description");
			String room = params.get("room");
			try {
				if (room != null) {
					item.room = Long.parseLong(room);
				}
			} catch (Exception e) {
				throw new IllegalArgumentException("Room number is invalid: " + room);
			}
			String shelf = params.get("shelf");
			try {
				if (shelf != null) {
					item.shelf = Long.parseLong(shelf);
				}
			} catch (Exception e) {
				throw new IllegalArgumentException("Shelf number is invalid: " + shelf);
			}
			InventoryDB db = new InventoryDB();
			try {
				db.addItem(item);
			} finally {
				db.close();
			}
			buffer.append("Item was added.");
		} catch (IllegalArgumentException e) {
			buffer.append("Invalid input! " + e.getMessage());
		}
		printReturnToInventory(buffer);
	}

	private InventoryItem findItem(StringBuffer buffer, Map<String, String> params) throws SqlJetException {
		if (!params.containsKey("article")) {
			buffer.append("Article is not specified.");
			printReturnToInventory(buffer);
			return null;
		}
		long article;
		try {
			article = Long.parseLong(params.get("article"));
		} catch (Exception e) {
			buffer.append("Invalid article: '");
			buffer.append(params.get("article"));
			buffer.append("'");
			printReturnToInventory(buffer);
			return null;
		}
		InventoryItem item;
		InventoryDB db = new InventoryDB();
		try {
			item = db.getItem(article);
		} finally {
			db.close();
		}
		if (item == null) {
			buffer.append("Item with article '");
			buffer.append(article);
			buffer.append("' is not found.");
			printReturnToInventory(buffer);
			return null;
		}
		return item;
	}

	public void editItem(StringBuffer buffer, Map<String, String> params) throws SqlJetException {
		InventoryItem item = findItem(buffer, params);
		if (item != null) {
			if (params.size() == 1) {
				showEditForm(buffer, item);
			} else {
				processEditForm(buffer, item, params);
			}
			printReturnToInventory(buffer);
		}
	}

	public void showEditForm(StringBuffer buffer, InventoryItem item) throws SqlJetException {
		buffer.append("<h2>Edit Item</h2><p>");
		buffer.append("<form><table>");
		buffer.append("<tr><td>Name:</td><td><input type='text' name='name'");
		if (item.name != null) {
			buffer.append(" value='");
			buffer.append(item.name);
			buffer.append("'");
		}
		buffer.append("/></td></tr>");
		buffer.append("<tr><td>Description:</td><td><input type='text' name='description'");
		if (item.description != null) {
			buffer.append(" value='");
			buffer.append(item.description);
			buffer.append("'");
		}
		buffer.append("/></td></tr>");
		buffer.append("<tr><td>Room:</td><td><input type='text' name='room' value='");
		buffer.append(item.room);
		buffer.append("' style='text-align: right;'/></td></tr>");
		buffer.append("<tr><td>Shelf:</td><td><input type='text' name='shelf' value='");
		buffer.append(item.shelf);
		buffer.append("' style='text-align: right;'/></td></tr>");
		buffer.append("</table><input type='submit' value='Submit'/>");
		buffer.append("<input type='hidden' name='article' value='");
		buffer.append(item.article);
		buffer.append("'/></form>");
	}

	public void processEditForm(StringBuffer buffer, InventoryItem item, Map<String, String> params)
			throws SqlJetException {
		try {
			Map<String, Object> values = new HashMap<String, Object>();
			String name = params.get("name");
			if (name == null) {
				throw new IllegalArgumentException("Name is not specified.");
			} else {
				values.put("name", name);
			}
			String description = params.get("description");
			if (description != null) {
				try {
					values.put("description", description.getBytes("UTF-8"));
				} catch (UnsupportedEncodingException e) {
					throw new IllegalArgumentException(e);
				}
			}
			String room = params.get("room");
			if (room != null) {
				try {
					values.put("room", Long.parseLong(room));
				} catch (Exception e) {
					throw new IllegalArgumentException("Room number is invalid: " + room);
				}
			}
			String shelf = params.get("shelf");
			if (shelf != null) {
				try {
					values.put("shelf", Long.parseLong(shelf));
				} catch (Exception e) {
					throw new IllegalArgumentException("Shelf number is invalid: " + shelf);
				}
			}
			InventoryDB db = new InventoryDB();
			try {
				db.updateItem(item.article, values);
			} finally {
				db.close();
			}
			buffer.append("Item was updated.");
		} catch (IllegalArgumentException e) {
			buffer.append("Invalid input! " + e.getMessage());
		}
	}

	public void removeItem(StringBuffer buffer, Map<String, String> params) throws SqlJetException {
		InventoryItem item = findItem(buffer, params);
		if (item != null) {
			InventoryDB db = new InventoryDB();
			try {
				db.removeItem(item.article);
			} finally {
				db.close();
			}
			buffer.append("Item was removed.");
			printReturnToInventory(buffer);
		}
	}
}
