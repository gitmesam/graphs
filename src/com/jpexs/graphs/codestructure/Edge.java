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
package com.jpexs.graphs.codestructure;

import com.jpexs.graphs.codestructure.nodes.Node;

/**
 *
 * @author JPEXS
 */
public class Edge<N extends Node> implements Comparable<Edge<N>> {

    public N from;
    public N to;

    public Edge(N from, N to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public int hashCode() {
        return (this.from.getId() + ":" + this.to.getId()).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Edge other = (Edge) obj;
        if (!this.from.getId().equals(other.from.getId())) {
            return false;
        }
        if (!this.to.getId().equals(other.to.getId())) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return from.toString() + "->" + to.toString();
    }

    @Override
    public int compareTo(Edge<N> o) {
        int ret = from.compareTo(o.from);
        if (ret != 0) {
            return ret;
        }
        return to.compareTo(o.to);
    }

}
