/**
 * ISqlJetTableDef.java
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
package org.tmatesoft.sqljet.core.schema;

import java.util.List;

/**
 * Table Definition.
 * 
 * @author TMate Software Ltd.
 * @author Dmitry Stadnik (dtrace@seznam.cz)
 */
public interface ISqlJetTableDef {

    public String getName();

    public boolean isTemporary();

    public List<ISqlJetColumnDef> getColumns();

    public ISqlJetColumnDef getColumn(String name);
    
    public int getColumnNumber(String name);    

    public List<ISqlJetTableConstraint> getConstraints();
}
