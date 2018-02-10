/*
 * Copyright (C) 2018 JPEXS, All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */
package com.jpexs.graphs.graphviz.graph;

/**
 *
 * @author JPEXS
 */
public class NodeIdToAttributes {

    public NodeId nodeId;
    public AttributesMap attributes;

    public NodeIdToAttributes(NodeId nodeId, AttributesMap attributes) {
        this.nodeId = nodeId;
        this.attributes = attributes;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(nodeId.toString());
        sb.append(attributes.toString());
        return sb.toString();
    }
}