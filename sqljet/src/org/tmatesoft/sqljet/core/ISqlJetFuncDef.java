/**
 * ISqlJetFuncDef.java
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
package org.tmatesoft.sqljet.core;

import java.util.EnumSet;

/**
 * Each SQL function is defined by an instance of the following
 * structure.  A pointer to this structure is stored in the sqlite.aFunc
 * hash table.  When multiple functions have the same name, the hash table
 * points to a linked list of these structures.
 * 
 * 
 * @author TMate Software Ltd.
 * @author Sergey Scherbina (sergey.scherbina@gmail.com)
 *
 */
public interface ISqlJetFuncDef {
    
}
