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
package com.jpexs.graphs.structure.factories.operations;

import com.jpexs.graphs.structure.factories.BasicEditableEndIfFactory;
import com.jpexs.graphs.structure.nodes.Node;
import java.util.ArrayList;
import java.util.List;
import com.jpexs.graphs.structure.nodes.EditableNode;
import com.jpexs.graphs.structure.nodes.EditableEndIfNode;
import com.jpexs.graphs.structure.factories.EditableEndIfFactory;

/**
 *
 * @author JPEXS
 */
public class EndIfNodeInjector<T extends EditableNode> {

    private EditableEndIfFactory<EditableNode> endIfFactory = new BasicEditableEndIfFactory();

    public void setEndIfFactory(EditableEndIfFactory endIfFactory) {
        this.endIfFactory = endIfFactory;
    }

    public EditableEndIfNode injectEndIf(T decisionNode, List<T> endBranchNodes, T afterNode) {
        int afterNodePrevIndex = Integer.MAX_VALUE;
        for (Node prev : endBranchNodes) {
            int index = afterNode.getPrev().indexOf(prev);
            if (index < afterNodePrevIndex) {
                afterNodePrevIndex = index;
            }
        }

        EditableEndIfNode endIfNode = endIfFactory.makeEndIfNode(decisionNode);

        //remove connection prev->after
        for (int i = 0; i < endBranchNodes.size(); i++) {
            EditableNode prev = endBranchNodes.get(i);
            prev.removeNext(afterNode);
        }
        for (EditableNode prev : endBranchNodes) {
            afterNode.removePrev(prev);
        }

        //add connection prev->endif 
        for (int i = 0; i < endBranchNodes.size(); i++) {
            EditableNode prev = endBranchNodes.get(i);
            prev.addNext(endIfNode);
            endIfNode.addPrev(prev);
        }
        //add connection endif->after
        endIfNode.addNext(afterNode);
        afterNode.addPrev(afterNodePrevIndex, endIfNode); //add to correct index

        fireEndIfAdded(endIfNode);
        return endIfNode;
    }

    private List<EnfIfNodeInjectorProgressListener> listeners = new ArrayList<>();

    public void addListener(EnfIfNodeInjectorProgressListener l) {
        listeners.add(l);
    }

    public void removeListener(EnfIfNodeInjectorProgressListener l) {
        listeners.remove(l);
    }

    private void fireEndIfAdded(EditableEndIfNode node) {
        for (EnfIfNodeInjectorProgressListener l : listeners) {
            l.endIfAdded(node);
        }
    }

}