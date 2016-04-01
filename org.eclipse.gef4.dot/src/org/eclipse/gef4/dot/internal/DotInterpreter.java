/*******************************************************************************
 * Copyright (c) 2009, 2016 Fabian Steeg and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Fabian Steeg                - initial API and implementation (bug #277380)                     
 *     Alexander Nyßen (itemis AG) - several refactorings and additions (bugs #487081, #489793)
 *     Tamas Miklossy  (itemis AG) - support for arrowType edge decorations (bug #477980)
 *                                   
 *******************************************************************************/

package org.eclipse.gef4.dot.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.gef4.dot.internal.parser.dot.AttrList;
import org.eclipse.gef4.dot.internal.parser.dot.AttrStmt;
import org.eclipse.gef4.dot.internal.parser.dot.Attribute;
import org.eclipse.gef4.dot.internal.parser.dot.AttributeType;
import org.eclipse.gef4.dot.internal.parser.dot.DotAst;
import org.eclipse.gef4.dot.internal.parser.dot.DotGraph;
import org.eclipse.gef4.dot.internal.parser.dot.EdgeRhsNode;
import org.eclipse.gef4.dot.internal.parser.dot.EdgeStmtNode;
import org.eclipse.gef4.dot.internal.parser.dot.GraphType;
import org.eclipse.gef4.dot.internal.parser.dot.NodeId;
import org.eclipse.gef4.dot.internal.parser.dot.NodeStmt;
import org.eclipse.gef4.dot.internal.parser.dot.Stmt;
import org.eclipse.gef4.dot.internal.parser.dot.Subgraph;
import org.eclipse.gef4.dot.internal.parser.dot.util.DotSwitch;
import org.eclipse.gef4.graph.Edge;
import org.eclipse.gef4.graph.Graph;
import org.eclipse.gef4.graph.Node;

/**
 * Create a {@link Graph} instance from a DOT string by interpreting the AST of
 * the parsed DOT.
 * 
 * @author Fabian Steeg (fsteeg)
 * @author Alexander Nyßen (anyssen)
 */
public final class DotInterpreter extends DotSwitch<Object> {

	private Map<String, String> globalNodeAttributes = new HashMap<>();
	private Map<String, String> globalEdgeAttributes = new HashMap<>();

	private Graph.Builder graph;
	private Map<String, Node> nodes;

	private boolean createEdge;
	private String currentArrowHead;
	private String currentArrowTail;
	private String currentArrowSize;
	private String currentEdgeDirection;
	private String currentEdgeStyle;
	private String currentEdgeLabel;
	private String currentEdgeSourceNodeName;
	private String currentEdgePos;
	private String currentEdgeXLabel;
	private String currentEdgeXlp;
	private String currentEdgeLp;
	private String currentEdgeTailLabel;
	private String currentEdgeHeadLabel;
	private String currentEdgeHeadLp;
	private String currentEdgeTailLp;
	private String currentEdgeId;
	private String currentEdgeOp;

	/**
	 * @param dotAst
	 *            The DOT abstract syntax tree (AST) to interpret
	 * @return A graph instance for the given DOT AST
	 */
	public List<Graph> interpret(DotAst dotAst) {
		List<Graph> graphs = new ArrayList<>();
		for (DotGraph dotGraph : dotAst.getGraphs()) {
			Graph g = interpret(dotGraph, new Graph.Builder());
			if (g != null) {
				graphs.add(g);
			}
		}
		return graphs;
	}

	private Graph interpret(DotGraph dotGraph, Graph.Builder graph) {
		this.graph = graph;
		nodes = new HashMap<>();
		doSwitch(dotGraph);
		TreeIterator<Object> contents = EcoreUtil.getAllProperContents(dotGraph,
				false);
		while (contents.hasNext()) {
			doSwitch((EObject) contents.next());
		}
		return graph.build();
	}

	@Override
	public Object caseDotGraph(DotGraph object) {
		createGraph(object);
		return super.caseDotGraph(object);
	}

	@Override
	public Object caseAttrStmt(AttrStmt object) {
		createAttributes(object);
		return super.caseAttrStmt(object);
	}

	@Override
	public Object caseNodeStmt(NodeStmt object) {
		createNode(object);
		return super.caseNodeStmt(object);
	}

	@Override
	public Object caseEdgeStmtNode(EdgeStmtNode object) {
		currentEdgeId = getAttributeValue(object, DotAttributes.ID__GNE);
		currentEdgeLabel = getAttributeValue(object, DotAttributes.LABEL__GNE);
		currentEdgeLp = getAttributeValue(object, DotAttributes.LP__E);
		currentEdgeXLabel = getAttributeValue(object, DotAttributes.XLABEL__NE);
		currentEdgeXlp = getAttributeValue(object, DotAttributes.XLP__NE);
		currentEdgeStyle = getAttributeValue(object, DotAttributes.STYLE__E);
		currentEdgePos = getAttributeValue(object, DotAttributes.POS__NE);
		currentEdgeHeadLabel = getAttributeValue(object,
				DotAttributes.HEADLABEL__E);
		currentEdgeHeadLp = getAttributeValue(object, DotAttributes.HEAD_LP__E);
		currentEdgeTailLabel = getAttributeValue(object,
				DotAttributes.TAILLABEL__E);
		currentEdgeTailLp = getAttributeValue(object, DotAttributes.TAIL_LP__E);
		currentArrowHead = getAttributeValue(object,
				DotAttributes.ARROWHEAD__E);
		currentArrowTail = getAttributeValue(object,
				DotAttributes.ARROWTAIL__E);
		currentArrowSize = getAttributeValue(object,
				DotAttributes.ARROWSIZE__E);
		currentEdgeDirection = getAttributeValue(object, DotAttributes.DIR__E);
		return super.caseEdgeStmtNode(object);
	}

	@Override
	public Object caseNodeId(NodeId object) {
		if (!createEdge) {
			currentEdgeSourceNodeName = escaped(object.getName());
		} else {
			String targetNodeName = escaped(object.getName());
			if (currentEdgeSourceNodeName != null && targetNodeName != null) {
				createEdge(currentEdgeSourceNodeName, currentEdgeOp,
						targetNodeName);
				// current target node may be source for next EdgeRHS
				currentEdgeSourceNodeName = targetNodeName;
			}
			createEdge = false;
		}
		return super.caseNodeId(object);
	}

	private void createEdge(String sourceNodeName, String edgeOp,
			String targetNodeName) {
		Edge.Builder edgeBuilder = new Edge.Builder(node(sourceNodeName),
				node(targetNodeName));
		Edge edge = edgeBuilder.buildEdge();

		// name (always set)
		DotAttributes.setName(edge, sourceNodeName + edgeOp + targetNodeName);

		// id
		if (currentEdgeId != null) {
			DotAttributes.setId(edge, currentEdgeId);
		}

		// label
		if (currentEdgeLabel != null) {
			DotAttributes.setLabel(edge, currentEdgeLabel);
		} else if (globalEdgeAttributes.containsKey(DotAttributes.LABEL__GNE)) {
			DotAttributes.setLabel(edge,
					globalEdgeAttributes.get(DotAttributes.LABEL__GNE));
		}

		// external label (xlabel)
		if (currentEdgeXLabel != null) {
			DotAttributes.setXLabel(edge, currentEdgeXLabel);
		}

		// head label (headllabel)
		if (currentEdgeHeadLabel != null) {
			DotAttributes.setHeadLabel(edge, currentEdgeHeadLabel);
		}

		// tail label (taillabel)
		if (currentEdgeTailLabel != null) {
			DotAttributes.setTailLabel(edge, currentEdgeTailLabel);
		}

		// style
		if (currentEdgeStyle != null) {
			DotAttributes.setStyle(edge, currentEdgeStyle);
		} else if (globalEdgeAttributes.containsKey(DotAttributes.STYLE__E)) {
			DotAttributes.setArrowTail(edge,
					globalEdgeAttributes.get(DotAttributes.STYLE__E));
		}

		// position (pos)
		if (currentEdgePos != null) {
			DotAttributes.setPos(edge, currentEdgePos);
		}
		// label position (lp)
		if (currentEdgeLp != null) {
			DotAttributes.setLp(edge, currentEdgeLp);
		}

		// external label position (xlp)
		if (currentEdgeXlp != null) {
			DotAttributes.setXlp(edge, currentEdgeXlp);
		}

		// head label position (head_lp)
		if (currentEdgeHeadLp != null) {
			DotAttributes.setHeadLp(edge, currentEdgeHeadLp);
		}

		// tail label position (tail_lp)
		if (currentEdgeTailLp != null) {
			DotAttributes.setTailLp(edge, currentEdgeTailLp);
		}

		// arrow head
		if (currentArrowHead != null) {
			DotAttributes.setArrowHead(edge, currentArrowHead);
		} else if (globalEdgeAttributes
				.containsKey(DotAttributes.ARROWHEAD__E)) {
			DotAttributes.setArrowHead(edge,
					globalEdgeAttributes.get(DotAttributes.ARROWHEAD__E));
		}

		// arrow tail
		if (currentArrowTail != null) {
			DotAttributes.setArrowTail(edge, currentArrowTail);
		} else if (globalEdgeAttributes
				.containsKey(DotAttributes.ARROWTAIL__E)) {
			DotAttributes.setArrowTail(edge,
					globalEdgeAttributes.get(DotAttributes.ARROWTAIL__E));
		}

		// arrow size
		if (currentArrowSize != null) {
			DotAttributes.setArrowSize(edge, currentArrowSize);
		} else if (globalEdgeAttributes
				.containsKey(DotAttributes.ARROWSIZE__E)) {
			DotAttributes.setArrowSize(edge,
					globalEdgeAttributes.get(DotAttributes.ARROWSIZE__E));
		}

		// direction
		if (currentEdgeDirection != null) {
			DotAttributes.setDir(edge, currentEdgeDirection);
		} else if (globalEdgeAttributes.containsKey(DotAttributes.DIR__E)) {
			DotAttributes.setDir(edge,
					globalEdgeAttributes.get(DotAttributes.DIR__E));
		}

		graph.edges(edge);
	}

	private boolean supported(String value, Set<String> vals) {
		if (value == null) {
			return false;
		}
		return vals.contains(value);
	}

	@Override
	public Object caseEdgeRhsNode(EdgeRhsNode object) {
		// Set the flag for the node_id case handled above
		createEdge = true;
		currentEdgeOp = object.getOp().getLiteral();
		return super.caseEdgeRhsNode(object);
	}

	@Override
	public Object caseSubgraph(Subgraph object) {
		return super.caseSubgraph(object);
	}

	private void createGraph(DotGraph dotGraph) {
		// name (meta-attribute)
		String name = escaped(dotGraph.getName());
		if (name != null) {
			graph.attr(DotAttributes._NAME__GNE, name);
		}

		// type (meta-attribute)
		GraphType graphType = dotGraph.getType();
		graph.attr(DotAttributes._TYPE__G,
				GraphType.GRAPH.equals(graphType)
						? DotAttributes._TYPE__G__GRAPH
						: DotAttributes._TYPE__G__DIGRAPH);

		// layout
		String layout = getAttributeValue(dotGraph, DotAttributes.LAYOUT__G);
		if (layout != null) {
			graph.attr(DotAttributes.LAYOUT__G, layout);
		}

		String rankdir = getAttributeValue(dotGraph, DotAttributes.RANKDIR__G);
		if (rankdir != null) {
			graph.attr(DotAttributes.RANKDIR__G, rankdir);
		}
	}

	private void createAttributes(final AttrStmt attrStmt) {
		// TODO: Verify that the global values are retrieved from edge/node
		// attributes. Maybe they are retrieved from graph attributes, and it
		// should really be GRAPH_EDGE_STYLE.
		AttributeType type = attrStmt.getType();
		switch (type) {
		case EDGE: {
			// label
			String globalEdgeLabel = getAttributeValue(attrStmt,
					DotAttributes.LABEL__GNE);
			if (globalEdgeLabel != null) {
				globalEdgeAttributes.put(DotAttributes.LABEL__GNE,
						globalEdgeLabel);
			}
			// arrowhead
			String globalArrowHead = getAttributeValue(attrStmt,
					DotAttributes.ARROWHEAD__E);
			if (globalArrowHead != null) {
				globalEdgeAttributes.put(DotAttributes.ARROWHEAD__E,
						globalArrowHead);
			}
			// arrowtail
			String globalArrowTail = getAttributeValue(attrStmt,
					DotAttributes.ARROWTAIL__E);
			if (globalArrowTail != null) {
				globalEdgeAttributes.put(DotAttributes.ARROWTAIL__E,
						globalArrowTail);
			}
			// arrowsize
			String globalArrowSize = getAttributeValue(attrStmt,
					DotAttributes.ARROWSIZE__E);
			if (globalArrowSize != null) {
				globalEdgeAttributes.put(DotAttributes.ARROWSIZE__E,
						globalArrowSize);
			}
			// dir
			String globalDir = getAttributeValue(attrStmt,
					DotAttributes.DIR__E);
			if (globalDir != null) {
				globalEdgeAttributes.put(DotAttributes.DIR__E, globalDir);
			}
			// style
			String globalEdgeStyle = getAttributeValue(attrStmt,
					DotAttributes.STYLE__E);
			if (globalEdgeStyle != null) {
				globalEdgeAttributes.put(DotAttributes.STYLE__E,
						globalEdgeStyle);
			}
			break;
		}
		case NODE: {
			String globalNodeLabel = getAttributeValue(attrStmt,
					DotAttributes.LABEL__GNE);
			if (globalNodeLabel != null) {
				globalNodeAttributes.put(DotAttributes.LABEL__GNE,
						globalNodeLabel);
			}
			break;
		}
		case GRAPH: {
			for (AttrList al : attrStmt.getAttrLists()) {
				for (Attribute a : al.getAttributes()) {
					graph.attr(a.getName(), a.getValue());
				}
			}
			String graphLayout = getAttributeValue(attrStmt,
					DotAttributes.LAYOUT__G);
			if (graphLayout != null) {
				String graphLayoutLc = new String(graphLayout).toLowerCase();
				if (!supported(graphLayoutLc,
						DotAttributes.LAYOUT__G__VALUES)) {
					throw new IllegalArgumentException(
							"Unknown layout algorithm <" + graphLayoutLc
									+ ">.");
				}
				graph.attr(DotAttributes.LAYOUT__G, graphLayoutLc);
			}
			break;
		}
		}
	}

	private void createNode(final NodeStmt nodeStatement) {
		// name (from grammar definition, not attribute)
		String nodeName = escaped(nodeStatement.getNode().getName());
		Node node;
		if (nodes.containsKey(nodeName)) {
			node = nodes.get(nodeName);
		} else {
			node = new Node.Builder().attr(DotAttributes._NAME__GNE, nodeName)
					.buildNode();
		}

		// id
		String id = getAttributeValue(nodeStatement, DotAttributes.ID__GNE);
		if (id != null) {
			DotAttributes.setId(node, id);
		}

		// label
		String label = getAttributeValue(nodeStatement,
				DotAttributes.LABEL__GNE);
		if (label != null) {
			DotAttributes.setLabel(node, label);
		} else if (globalNodeAttributes.containsKey(DotAttributes.LABEL__GNE)) {
			DotAttributes.setLabel(node,
					globalNodeAttributes.get(DotAttributes.LABEL__GNE));
		}

		// xlabel
		String xLabel = getAttributeValue(nodeStatement,
				DotAttributes.XLABEL__NE);
		if (xLabel != null) {
			DotAttributes.setXLabel(node, xLabel);
		}

		// pos
		String pos = getAttributeValue(nodeStatement, DotAttributes.POS__NE);
		if (pos != null) {
			DotAttributes.setPos(node, pos);
		}

		// xlp
		String xlp = getAttributeValue(nodeStatement, DotAttributes.XLP__NE);
		if (xlp != null) {
			DotAttributes.setXlp(node, xlp);
		}

		// width
		String width = getAttributeValue(nodeStatement, DotAttributes.WIDTH__N);
		if (width != null) {
			DotAttributes.setWidth(node, width);
		}

		// height
		String height = getAttributeValue(nodeStatement,
				DotAttributes.HEIGHT__N);
		if (height != null) {
			DotAttributes.setHeight(node, height);
		}

		// TODO: do we have to perform containment check here??
		if (!nodes.containsKey(nodeName)) {
			nodes.put(nodeName, node);
			graph = graph.nodes(node);
		}
	}

	private Node node(String nodeName) {
		if (!nodes.containsKey(nodeName)) {
			Node node = new Node.Builder()
					.attr(DotAttributes._NAME__GNE, nodeName).buildNode();
			nodes.put(nodeName, node);
			graph = graph.nodes(node);
		}
		return nodes.get(nodeName);
	}

	private String getAttributeValue(final DotGraph graph, final String name) {
		for (Stmt stmt : graph.getStmts()) {
			String value = null;
			if (stmt instanceof AttrStmt) {
				value = getAttributeValue((AttrStmt) stmt, name);
			} else if (stmt instanceof Attribute) {
				value = getAttributeValue((Attribute) stmt, name);
			}
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	/**
	 * @param stmt
	 *            The {@link Stmt} object, e.g. the object corresponding to
	 *            "node[label="hi"]"
	 * @param name
	 *            The name of the attribute to get the value for, e.g. "label"
	 * @return The value of the given attribute, e.g. "hi"
	 */
	private String getAttributeValue(final NodeStmt stmt, final String name) {
		return getAttributeValue(stmt.getAttrLists(), name);
	}

	/**
	 * Returns the value of the first attribute with the give name or
	 * <code>null</code> if no attribute could be found.
	 * 
	 * @param attrLists
	 *            The {@link AttrList}s to search.
	 * @param name
	 *            The name of the attribute whose value is to be retrieved.
	 * @return The attribute value or <code>null</code> in case the attribute
	 *         could not be found.
	 */
	private String getAttributeValue(List<AttrList> attrLists,
			final String name) {
		for (AttrList attrList : attrLists) {
			String value = getAttributeValue(attrList, name);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	private String getAttributeValue(AttrStmt attrStmt, String name) {
		return getAttributeValue(attrStmt.getAttrLists(), name);
	}

	private String getAttributeValue(EdgeStmtNode edgeStmtNode, String name) {
		return getAttributeValue(edgeStmtNode.getAttrLists(), name);
	}

	private String getAttributeValue(AttrList attrList, final String name) {
		Iterator<EObject> attributeContents = attrList.eContents().iterator();
		while (attributeContents.hasNext()) {
			EObject next = attributeContents.next();
			if (next instanceof Attribute) {
				String value = getAttributeValue((Attribute) next, name);
				if (value != null) {
					return value;
				}
			}
		}
		return null;
	}

	private String getAttributeValue(Attribute attribute, final String name) {
		if (attribute.getName().equals(name)) {
			return escaped(attribute.getValue());
		}
		return null;
	}

	private String escaped(String id) {
		if (id == null) {
			return null;
		}
		return id
				/* In DOT, an ID can be quoted... */
				.replaceAll("^\"|\"$", "") //$NON-NLS-1$//$NON-NLS-2$
				/*
				 * ...and may contain escaped quotes, see footnote on
				 * http://www.graphviz.org/doc/info/lang.html
				 */
				.replaceAll("\\\\\"", "\""); //$NON-NLS-1$//$NON-NLS-2$
	}
}