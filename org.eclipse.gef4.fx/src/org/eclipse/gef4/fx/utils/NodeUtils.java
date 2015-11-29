/*******************************************************************************
 * Copyright (c) 2014, 2015 itemis AG and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Matthias Wienand (itemis AG) - initial API and implementation
 *
 *******************************************************************************/
package org.eclipse.gef4.fx.utils;

import java.awt.geom.NoninvertibleTransformException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.gef4.geometry.convert.fx.JavaFX2Geometry;
import org.eclipse.gef4.geometry.planar.AffineTransform;
import org.eclipse.gef4.geometry.planar.IGeometry;
import org.eclipse.gef4.geometry.planar.Point;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;

/**
 * The {@link NodeUtils} class contains utility methods for working with JavaFX:
 * <ul>
 * <li>transforming {@link IGeometry}s from/to different JavaFX coordinate
 * systems ({@link #localToParent(Node, IGeometry)},
 * {@link #localToScene(Node, IGeometry)}, {@link #localToScene(Node, Point)},
 * {@link #parentToLocal(Node, IGeometry)},
 * {@link #sceneToLocal(Node, IGeometry)})</li>
 * <li>determining the actual local-to-scene or scene-to-local transform for a
 * JavaFX {@link Node} ({@link #getLocalToSceneTx(Node)},
 * {@link #getSceneToLocalTx(Node)})</li>
 * <li>perform picking of {@link Node}s at a specific position within the JavaFX
 * scene graph ({@link #getNodesAt(Node, double, double)})</li>
 * </ul>
 *
 * @author mwienand
 * @author anyssen
 *
 */
public class NodeUtils {

	/**
	 * Returns an {@link AffineTransform} which represents the transformation
	 * matrix to transform geometries from the local coordinate system of the
	 * given {@link Node} into the coordinate system of the {@link Scene}.
	 * <p>
	 * JavaFX {@link Node} provides a (lazily computed) local-to-scene-transform
	 * property which we could access to get that transform. Unfortunately, this
	 * property is not updated correctly, i.e. its value can differ from the
	 * actual local-to-scene-transform. Therefore, we compute the
	 * local-to-scene-transform for the given node here by concatenating the
	 * local-to-parent-transforms along the hierarchy.
	 * <p>
	 * Note that in situations where you do not need the actual transform, but
	 * instead perform a transformation, you can use the
	 * {@link Node#localToScene(Point2D) Node#localToScene(...)} methods on the
	 * <i>node</i> directly, because it does not make use of the
	 * local-to-scene-transform property, but uses localToParent() internally.
	 *
	 * @param node
	 *            The JavaFX {@link Node} for which the local-to-scene
	 *            transformation matrix is to be computed.
	 * @return An {@link AffineTransform} representing the local-to-scene
	 *         transformation matrix for the given {@link Node}.
	 */
	public static AffineTransform getLocalToSceneTx(Node node) {
		AffineTransform tx = JavaFX2Geometry
				.toAffineTransform(node.getLocalToParentTransform());
		Node tmp = node;
		while (tmp.getParent() != null) {
			tmp = tmp.getParent();
			tx = JavaFX2Geometry
					.toAffineTransform(tmp.getLocalToParentTransform())
					.concatenate(tx);
		}
		return tx;
	}

	/**
	 * Performs picking on the scene graph beginning at the specified root node
	 * and processing its transitive children.
	 *
	 * @param sceneX
	 *            The x-coordinate of the position to pick nodes at, interpreted
	 *            in scene coordinate space.
	 * @param sceneY
	 *            The y-coordinate of the position to pick nodes at, interpreted
	 *            in scene coordinate space.
	 * @param root
	 *            The root node at which to start with picking
	 * @return A list of {@link Node}s which contain the the given coordinate.
	 */
	public static List<Node> getNodesAt(Node root, double sceneX,
			double sceneY) {
		List<Node> picked = new ArrayList<>();

		// start with given root node
		List<Node> nodes = new ArrayList<>();
		nodes.add(root);

		while (!nodes.isEmpty()) {
			Node current = nodes.remove(0);
			// transform to local coordinates
			Point2D pLocal = current.sceneToLocal(sceneX, sceneY);
			// check if bounds contains (necessary to find children in mouse
			// transparent regions)
			if (!current.isMouseTransparent()
					&& current.getBoundsInLocal().contains(pLocal)) {
				// check precisely
				if (current.contains(pLocal)) {
					picked.add(0, current);
				}
				// test all children, too
				if (current instanceof Parent) {
					nodes.addAll(0,
							((Parent) current).getChildrenUnmodifiable());
				}
			}
		}
		return picked;
	}

	/**
	 * Returns the scene-to-local transform for the given {@link Node}.
	 *
	 * @param node
	 *            The {@link Node} for which the scene-to-local transform is
	 *            returned.
	 * @return The scene-to-local transform for the given {@link Node}.
	 */
	public static AffineTransform getSceneToLocalTx(Node node) {
		try {
			// IMPORTANT: we make use of getLocalToSceneTx(Node) here to
			// compensate that the Transform provided by FX is updated lazily.
			// See getLocalToSceneTx(Node) for details.
			return getLocalToSceneTx(node).invert();
		} catch (NoninvertibleTransformException e) {
			throw new IllegalArgumentException(e);
		}
	}

	/**
	 * Transforms the given {@link IGeometry} from the local coordinate system
	 * of the given {@link Node} into the coordinate system of the {@link Node}
	 * 's parent.
	 *
	 * @param n
	 *            The {@link Node} used to determine the transformation matrix.
	 * @param g
	 *            The {@link IGeometry} to transform.
	 * @return The new, transformed {@link IGeometry}.
	 */
	public static IGeometry localToParent(Node n, IGeometry g) {
		AffineTransform localToParentTx = JavaFX2Geometry
				.toAffineTransform(n.getLocalToParentTransform());
		return g.getTransformed(localToParentTx);
	}

	/**
	 * Transforms the given {@link IGeometry} from the local coordinate system
	 * of the given {@link Node} into scene coordinates.
	 *
	 * @param n
	 *            The {@link Node} used to determine the transformation matrix.
	 * @param g
	 *            The {@link IGeometry} to transform.
	 * @return The new, transformed {@link IGeometry}.
	 */
	public static IGeometry localToScene(Node n, IGeometry g) {
		AffineTransform localToSceneTx = getLocalToSceneTx(n);
		return g.getTransformed(localToSceneTx);
	}

	/**
	 * Transforms the given {@link Point} from the local coordinate system of
	 * the given {@link Node} into scene coordinates.
	 *
	 * @param n
	 *            The {@link Node} used to determine the transformation matrix.
	 * @param p
	 *            The {@link IGeometry} to transform.
	 * @return The new, transformed {@link Point}.
	 */
	public static Point localToScene(Node n, Point p) {
		AffineTransform localToSceneTx = getLocalToSceneTx(n);
		return localToSceneTx.getTransformed(p);
	}

	/**
	 * Transforms the given {@link IGeometry} from the parent coordinate system
	 * of the given {@link Node} into the local coordinate system of the
	 * {@link Node}.
	 *
	 * @param n
	 *            The {@link Node} used to determine the transformation matrix.
	 * @param g
	 *            The {@link IGeometry} to transform.
	 * @return The new, transformed {@link IGeometry}.
	 */
	public static IGeometry parentToLocal(Node n, IGeometry g) {
		// retrieve transform from scene to target parent, by inverting target
		// parent to scene
		AffineTransform localToParentTx = JavaFX2Geometry
				.toAffineTransform(n.getLocalToParentTransform());
		AffineTransform parentToLocalTx = null;
		try {
			parentToLocalTx = localToParentTx.getCopy().invert();
		} catch (NoninvertibleTransformException e) {
			// TODO: How do we recover from this?!
			throw new IllegalStateException(e);
		}
		return g.getTransformed(parentToLocalTx);
	}

	/**
	 * Transforms the given {@link IGeometry} from scene coordinates to the
	 * local coordinate system of the given {@link Node}.
	 *
	 * @param n
	 *            The {@link Node} used to determine the transformation matrix.
	 * @param g
	 *            The {@link IGeometry} to transform.
	 * @return The new, transformed {@link IGeometry}.
	 */
	public static IGeometry sceneToLocal(Node n, IGeometry g) {
		// retrieve transform from scene to target parent, by inverting target
		// parent to scene
		AffineTransform sceneToLocalTx = getSceneToLocalTx(n);
		return g.getTransformed(sceneToLocalTx);
	}

}
