/**
 * SqlJetColumnDef.java
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
package org.tmatesoft.sqljet.core.internal.btree.table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.antlr.runtime.tree.CommonTree;
import org.tmatesoft.sqljet.core.internal.table.ISqlJetColumnConstraint;
import org.tmatesoft.sqljet.core.internal.table.ISqlJetColumnDef;
import org.tmatesoft.sqljet.core.internal.table.ISqlJetTypeDef;

/**
 * @author TMate Software Ltd.
 * @author Dmitry Stadnik (dtrace@seznam.cz)
 */
public class SqlJetColumnDef implements ISqlJetColumnDef {

    private final String name;
    private final ISqlJetTypeDef type;
    private final List<ISqlJetColumnConstraint> constraints;

    public SqlJetColumnDef(CommonTree ast) {
        name = ast.getText();
        CommonTree constraintsNode = (CommonTree) ast.getChild(0);
        assert "constraints".equalsIgnoreCase(constraintsNode.getText());
        List<ISqlJetColumnConstraint> constraints = new ArrayList<ISqlJetColumnConstraint>();
        for (int i = 0; i < constraintsNode.getChildCount(); i++) {
            CommonTree constraintRootNode = (CommonTree) constraintsNode.getChild(i);
            assert "column_constraint".equalsIgnoreCase(constraintRootNode.getText());
            CommonTree constraintNode = (CommonTree) constraintRootNode.getChild(0);
            String constraintType = constraintNode.getText();
            String constraintName = constraintRootNode.getChildCount() > 1 ? constraintRootNode.getChild(1).getText()
                    : null;
            if ("primary".equalsIgnoreCase(constraintType)) {
                constraints.add(new SqlJetColumnPrimaryKey(constraintName, constraintNode));
            } else if ("not_null".equalsIgnoreCase(constraintType)) {
                constraints.add(new SqlJetColumnNotNull(constraintName, constraintNode));
            } else if ("unique".equalsIgnoreCase(constraintType)) {
                constraints.add(new SqlJetColumnUnique(constraintName, constraintNode));
            } else if ("check".equalsIgnoreCase(constraintType)) {
                // constraints.add(new SqlJetColumnCheck(constraintName,
                // constraintNode));
            } else if ("default".equalsIgnoreCase(constraintType)) {
                // constraints.add(new SqlJetColumnDefault(constraintName,
                // constraintNode));
            } else if ("collate".equalsIgnoreCase(constraintType)) {
                constraints.add(new SqlJetColumnCollate(constraintName, constraintNode));
            } else if ("references".equalsIgnoreCase(constraintType)) {
                // add fk clause
            } else {
                assert false;
            }
        }
        this.constraints = Collections.unmodifiableList(constraints);
        if (ast.getChildCount() > 1) {
            CommonTree typeNode = (CommonTree) ast.getChild(1);
            assert "type".equalsIgnoreCase(typeNode.getText());
            type = new SqlJetTypeDef(typeNode);
        } else {
            type = null;
        }
    }

    public String getName() {
        return name;
    }

    public ISqlJetTypeDef getType() {
        return type;
    }

    public List<ISqlJetColumnConstraint> getConstraints() {
        return constraints;
    }

    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append(getName());
        if (getType() != null) {
            buffer.append(' ');
            buffer.append(getType());
        }
        for (int i = 0; i < getConstraints().size(); i++) {
            buffer.append(' ');
            buffer.append(getConstraints().get(i));
        }
        return buffer.toString();
    }
}
