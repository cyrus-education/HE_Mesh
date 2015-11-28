/*
 *
 */
package wblut.processing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PImage;
import processing.core.PMatrix3D;
import processing.core.PShape;
import processing.opengl.PGraphicsOpenGL;
import wblut.core.WB_ProgressCounter;
import wblut.core.WB_ProgressTracker;
import wblut.geom.WB_AABB;
import wblut.geom.WB_AABBTree;
import wblut.geom.WB_AABBTree.WB_AABBNode;
import wblut.geom.WB_Circle;
import wblut.geom.WB_Classification;
import wblut.geom.WB_Coord;
import wblut.geom.WB_Curve;
import wblut.geom.WB_FaceListMesh;
import wblut.geom.WB_Frame;
import wblut.geom.WB_FrameNode;
import wblut.geom.WB_FrameStrut;
import wblut.geom.WB_GeometryFactory;
import wblut.geom.WB_GeometryOp;
import wblut.geom.WB_Line;
import wblut.geom.WB_Map;
import wblut.geom.WB_Map2D;
import wblut.geom.WB_Mesh;
import wblut.geom.WB_Plane;
import wblut.geom.WB_Point;
import wblut.geom.WB_PolyLine;
import wblut.geom.WB_Polygon;
import wblut.geom.WB_Ray;
import wblut.geom.WB_Ring;
import wblut.geom.WB_Segment;
import wblut.geom.WB_Transform;
import wblut.geom.WB_Triangle;
import wblut.geom.WB_Triangulation2D;
import wblut.geom.WB_Triangulation3D;
import wblut.geom.WB_Vector;
import wblut.hemesh.HE_EdgeIterator;
import wblut.hemesh.HE_Face;
import wblut.hemesh.HE_FaceEdgeCirculator;
import wblut.hemesh.HE_FaceHalfedgeInnerCirculator;
import wblut.hemesh.HE_FaceIntersection;
import wblut.hemesh.HE_FaceIterator;
import wblut.hemesh.HE_FaceVertexCirculator;
import wblut.hemesh.HE_Halfedge;
import wblut.hemesh.HE_Intersection;
import wblut.hemesh.HE_Mesh;
import wblut.hemesh.HE_MeshCollection;
import wblut.hemesh.HE_MeshIterator;
import wblut.hemesh.HE_MeshStructure;
import wblut.hemesh.HE_Path;
import wblut.hemesh.HE_Selection;
import wblut.hemesh.HE_TextureCoordinate;
import wblut.hemesh.HE_Vertex;
import wblut.hemesh.HE_VertexIterator;

/**
 *
 */
public class WB_Render3D extends WB_Render2D {
	// -----------------------------------------------------------------------Ray
	// Picker
	// Code written by Alberto Massa
	// Adapted by Frederik Vanhoutte
	// -----------------------------------------------------------------------
	// Comparator
	/**
	 *
	 */
	class EyeProximityComparator implements Comparator<HE_Face> {
		/*
		 * (non-Javadoc)
		 *
		 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
		 */
		@Override
		public int compare(final HE_Face o1, final HE_Face o2) {
			final WB_Segment s1 = new WB_Segment(unproject.ptStartPos, o1.getFaceCenter());
			final WB_Segment s2 = new WB_Segment(unproject.ptStartPos, o2.getFaceCenter());
			final double l1 = s1.getLength();
			final double l2 = s2.getLength();
			if (l1 < l2) {
				return -1;
			}
			if (l1 > l2) {
				return 1;
			}
			return 0;
		}
	}

	// -----------------------------------------------------------------------
	// Unproject
	/**
	 *
	 */
	private class Unproject {
		/**
		 *
		 */
		private boolean m_bValid = false;
		/**
		 *
		 */
		private final PMatrix3D m_pMatrix = new PMatrix3D();
		/**
		 *
		 */
		private final int[] m_aiViewport = new int[4];
		// Store the near and far ray positions.
		/**
		 *
		 */
		public WB_Point ptStartPos = new WB_Point();
		/**
		 *
		 */
		public WB_Point ptEndPos = new WB_Point();

		/**
		 *
		 *
		 * @param x
		 * @param y
		 * @param height
		 * @return
		 */
		public boolean calculatePickPoints(final double x, final double y, final int height) {
			// Calculate positions on the near and far 3D
			// frustum planes.
			m_bValid = true; // Have to do both in order to reset PVector on
			// error.
			if (!gluUnProject(x, height - y, 0.0, ptStartPos)) {
				m_bValid = false;
			}
			if (!gluUnProject(x, height - y, 1.0, ptEndPos)) {
				m_bValid = false;
			}
			return m_bValid;
		}

		/**
		 *
		 *
		 * @param g3d
		 */
		public void captureViewMatrix(final PGraphicsOpenGL g3d) {
			// Call this to capture the selection matrix after
			// you have called perspective() or ortho() and applied
			// your pan, zoom and camera angles - but before
			// you start drawing or playing with the
			// matrices any further.
			if (g3d != null) { // Check for a valid 3D canvas.
				// Capture current projection matrix.
				m_pMatrix.set(g3d.projection);
				// Multiply by current modelview matrix.
				m_pMatrix.apply(g3d.modelview);
				// Invert the resultant matrix.
				m_pMatrix.invert();
				// Store the viewport.
				m_aiViewport[0] = 0;
				m_aiViewport[1] = 0;
				m_aiViewport[2] = g3d.width;
				m_aiViewport[3] = g3d.height;
			}
		}

		// -------------------------
		/**
		 *
		 *
		 * @param winx
		 * @param winy
		 * @param winz
		 * @param result
		 * @return
		 */
		public boolean gluUnProject(final double winx, final double winy, final double winz, final WB_Point result) {
			final double[] in = new double[4];
			final double[] out = new double[4];
			// Transform to normalized screen coordinates (-1 to 1).
			in[0] = (((winx - m_aiViewport[0]) / m_aiViewport[2]) * 2.0) - 1.0;
			in[1] = (((winy - m_aiViewport[1]) / m_aiViewport[3]) * 2.0) - 1.0;
			in[2] = (((winz > 1) ? 1.0 : ((winz < 0) ? 0.0 : winz)) * 2.0) - 1.0;
			in[3] = 1.0;
			// Calculate homogeneous coordinates.
			out[0] = (m_pMatrix.m00 * in[0]) + (m_pMatrix.m01 * in[1]) + (m_pMatrix.m02 * in[2])
					+ (m_pMatrix.m03 * in[3]);
			out[1] = (m_pMatrix.m10 * in[0]) + (m_pMatrix.m11 * in[1]) + (m_pMatrix.m12 * in[2])
					+ (m_pMatrix.m13 * in[3]);
			out[2] = (m_pMatrix.m20 * in[0]) + (m_pMatrix.m21 * in[1]) + (m_pMatrix.m22 * in[2])
					+ (m_pMatrix.m23 * in[3]);
			out[3] = (m_pMatrix.m30 * in[0]) + (m_pMatrix.m31 * in[1]) + (m_pMatrix.m32 * in[2])
					+ (m_pMatrix.m33 * in[3]);
			if (out[3] == 0.0) { // Check for an invalid result.
				result.set(0, 0, 0);
				return false;
			}
			// Scale to world coordinates.
			out[3] = 1.0 / out[3];
			result.set(out[0] * out[3], out[1] * out[3], out[2] * out[3]);
			return true;
		}
	}

	/**
	 *
	 */
	public static final WB_GeometryFactory geometryfactory = WB_GeometryFactory.instance();

	public static final WB_ProgressTracker tracker = WB_ProgressTracker.instance();

	public static int color(final int r, final int g, final int b) {
		return (255 << 24) | (r << 16) | (g << 8) | b;
	}

	/**
	 *
	 */
	protected final PGraphicsOpenGL home;

	/**
	 *
	 */
	private final Unproject unproject = new Unproject();

	/**
	 *
	 *
	 * @param home
	 */
	public WB_Render3D(final PApplet home) {
		super(home);
		if (home.g == null) {
			throw new IllegalArgumentException("WB_Render3D can only be used after size()");
		}
		if (!(home.g instanceof PGraphicsOpenGL)) {
			throw new IllegalArgumentException(
					"WB_Render3D can only be used with P3D, OPENGL or derived ProcessingPGraphics object");
		}
		this.home = (PGraphicsOpenGL) home.g;
	}

	/**
	 *
	 *
	 * @param home
	 */
	public WB_Render3D(final PGraphicsOpenGL home) {
		super(home);
		this.home = home;
	}

	/**
	 *
	 *
	 * @param AABB
	 */
	public void drawAABB(final WB_AABB AABB) {
		home.pushMatrix();
		translate(AABB.getCenter());
		home.box((float) AABB.getWidth(), (float) AABB.getHeight(), (float) AABB.getDepth());
		home.popMatrix();
	}

	/**
	 *
	 *
	 * @param mesh
	 */
	public void drawBezierEdges(final HE_MeshStructure mesh) {
		HE_Halfedge he;
		WB_Coord p0;
		WB_Coord p1;
		WB_Coord p2;
		WB_Coord p3;
		HE_Face f;
		final Iterator<HE_Face> fItr = mesh.fItr();
		while (fItr.hasNext()) {
			f = fItr.next();
			home.beginShape();
			he = f.getHalfedge();
			p0 = he.getPrevInFace().getHalfedgeCenter();
			vertex(p0);
			do {
				p1 = he.getVertex();
				p2 = he.getVertex();
				p3 = he.getHalfedgeCenter();
				home.bezierVertex(p1.xf(), p1.yf(), p1.zf(), p2.xf(), p2.yf(), p2.zf(), p3.xf(), p3.yf(), p3.zf());
				he = he.getNextInFace();
			} while (he != f.getHalfedge());
			home.endShape();
		}
	}

	/**
	 *
	 *
	 * @param mesh
	 */
	public void drawBoundaryEdges(final HE_MeshStructure mesh) {
		HE_Halfedge he;
		final Iterator<HE_Halfedge> heItr = mesh.heItr();
		while (heItr.hasNext()) {
			he = heItr.next();
			if (he.getFace() == null) {
				line(he.getVertex(), he.getNextInFace().getVertex());
			}
		}
	}

	/**
	 *
	 *
	 * @param mesh
	 */
	public void drawBoundaryHalfedges(final HE_MeshStructure mesh) {
		HE_Halfedge he;
		final Iterator<HE_Halfedge> heItr = mesh.heItr();
		home.pushStyle();
		while (heItr.hasNext()) {
			he = heItr.next();
			if (he.getPair().getFace() == null) {
				home.stroke(255, 0, 0);
				line(he.getVertex(), he.getNextInFace().getVertex());
			}
		}
		home.popStyle();
	}

	/**
	 *
	 *
	 * @param C
	 */

	public void drawCircle(final WB_Circle C) {
		home.pushMatrix();
		translate(C.getCenter());
		final WB_Transform T = new WB_Transform(geometryfactory.Z(), C.getNormal());
		final WB_Vector angles = T.getEulerAnglesXYZ();
		home.rotateZ(angles.zf());
		home.rotateY(angles.yf());
		home.rotateX(angles.xf());
		home.ellipse(0, 0, 2 * (float) C.getRadius(), 2 * (float) C.getRadius());
		home.popMatrix();
	}

	/**
	 *
	 *
	 * @param circles
	 */
	public void drawCircles(final Collection<WB_Circle> circles) {
		final Iterator<WB_Circle> citr = circles.iterator();
		while (citr.hasNext()) {
			drawCircle(citr.next());
		}
	}

	/**
	 *
	 *
	 * @param curves
	 * @param steps
	 */
	public void drawCurve(final Collection<WB_Curve> curves, final int steps) {
		final Iterator<WB_Curve> citr = curves.iterator();
		while (citr.hasNext()) {
			drawCurve(citr.next(), steps);
		}
	}

	public void drawCurve(final WB_Curve C, final double minU, final double maxU, final int steps) {
		final int n = Math.max(1, steps);
		WB_Point p0 = C.curvePoint(minU);
		WB_Point p1;
		final double du = (maxU - minU) / n;
		for (int i = 0; i < n; i++) {
			p1 = C.curvePoint(minU + (i + 1) * du);
			line(p0, p1);
			p0 = p1;
		}
	}

	/**
	 *
	 *
	 * @param C
	 * @param steps
	 */
	public void drawCurve(final WB_Curve C, final int steps) {
		final int n = Math.max(1, steps);
		WB_Point p0 = C.curvePoint(0);
		WB_Point p1;
		final double du = 1.0 / n;
		for (int i = 0; i < n; i++) {
			p1 = C.curvePoint((i + 1) * du);
			line(p0, p1);
			p0 = p1;
		}
	}

	/**
	 *
	 *
	 * @param curves
	 * @param steps
	 * @deprecated Use {@link #drawCurve(Collection<WB_Curve>,int)} instead
	 */
	@Deprecated
	public void drawCurves(final Collection<WB_Curve> curves, final int steps) {
		drawCurve(curves, steps);
	}

	/**
	 * Draw one edge.
	 *
	 * @param e
	 *            edge
	 */
	public void drawEdge(final HE_Halfedge e) {
		line(e.getStartVertex(), e.getEndVertex());
	}

	/**
	 *
	 *
	 * @param key
	 * @param mesh
	 */
	public void drawEdge(final long key, final HE_Mesh mesh) {
		final HE_Halfedge e = mesh.getHalfedgeWithKey(key);
		if (e != null) {
			drawEdge(e);
		}
	}

	/**
	 * Draw edges.
	 *
	 * @param meshes
	 *            the meshes
	 */
	public void drawEdges(final Collection<? extends HE_MeshStructure> meshes) {
		final Iterator<? extends HE_MeshStructure> mItr = meshes.iterator();
		while (mItr.hasNext()) {
			drawEdges(mItr.next());
		}
	}

	public void drawEdges(final HE_Face f) {
		HE_Halfedge e = f.getHalfedge();
		do {
			line(e.getVertex(), e.getEndVertex());
			e = e.getNextInFace();
		} while (e != f.getHalfedge());
	}

	/**
	 * Draw mesh edges.
	 *
	 * @param mesh
	 *            the mesh
	 */
	public void drawEdges(final HE_MeshStructure mesh) {
		final Iterator<HE_Halfedge> eItr = mesh.eItr();
		HE_Halfedge e;
		while (eItr.hasNext()) {
			e = eItr.next();
			line(e.getVertex(), e.getEndVertex());
		}
	}

	/**
	 * Draw edges of selection.
	 *
	 * @param selection
	 *            selection to draw
	 */
	public void drawEdges(final HE_Selection selection) {
		final Iterator<HE_Face> fItr = selection.fItr();
		HE_Halfedge e;
		HE_Face f;
		while (fItr.hasNext()) {
			f = fItr.next();
			e = f.getHalfedge();
			do {
				if (e.isEdge() || e.isInnerBoundary() || !selection.contains(e.getPair().getFace())) {
					line(e.getVertex(), e.getEndVertex());
				}
				e = e.getNextInFace();
			} while (e != f.getHalfedge());
		}
	}

	/**
	 *
	 *
	 * @param label
	 * @param mesh
	 */
	public void drawEdgesWithInternalLabel(final int label, final HE_MeshStructure mesh) {
		final Iterator<HE_Halfedge> eItr = mesh.eItr();
		HE_Halfedge e;
		while (eItr.hasNext()) {
			e = eItr.next();
			if (e.getInternalLabel() == label) {
				line(e.getVertex(), e.getEndVertex());
			}
		}
	}

	/**
	 * Draw mesh edges.
	 *
	 * @param label
	 *            the label
	 * @param mesh
	 *            the mesh
	 */
	public void drawEdgesWithLabel(final int label, final HE_MeshStructure mesh) {
		final Iterator<HE_Halfedge> eItr = mesh.eItr();
		HE_Halfedge e;
		while (eItr.hasNext()) {
			e = eItr.next();
			if (e.getLabel() == label) {
				line(e.getVertex(), e.getEndVertex());
			}
		}
	}

	/**
	 *
	 *
	 * @param f
	 */
	public void drawFace(final HE_Face f) {
		drawFace(f, false);
	}

	/**
	 *
	 *
	 * @param f
	 * @param smooth
	 */
	public void drawFace(final HE_Face f, final boolean smooth) {
		final int fo = f.getFaceOrder();
		final List<HE_Vertex> vertices = f.getFaceVertices();
		if ((fo < 3) || (vertices.size() < 3)) {
		} else if (fo == 3) {
			final int[] tri = new int[] { 0, 1, 2 };
			HE_Vertex v0, v1, v2;
			WB_Coord n0, n1, n2;
			if (smooth) {
				home.beginShape(PConstants.TRIANGLES);
				v0 = vertices.get(tri[0]);
				n0 = v0.getVertexNormal();
				v1 = vertices.get(tri[1]);
				n1 = v1.getVertexNormal();
				v2 = vertices.get(tri[2]);
				n2 = v2.getVertexNormal();
				normal(n0);
				vertex(v0);
				normal(n1);
				vertex(v1);
				normal(n2);
				vertex(v2);
				home.endShape();
			} else {
				home.beginShape(PConstants.TRIANGLES);
				v0 = vertices.get(tri[0]);
				v1 = vertices.get(tri[1]);
				v2 = vertices.get(tri[2]);
				vertex(v0);
				vertex(v1);
				vertex(v2);
				home.endShape();
			}
		} else {
			final int[] tris = f.getTriangles();
			HE_Vertex v0, v1, v2;
			WB_Coord n0, n1, n2;
			if (smooth) {
				for (int i = 0; i < tris.length; i += 3) {
					home.beginShape(PConstants.TRIANGLES);
					v0 = vertices.get(tris[i]);
					n0 = v0.getVertexNormal();
					v1 = vertices.get(tris[i + 1]);
					n1 = v1.getVertexNormal();
					v2 = vertices.get(tris[i + 2]);
					n2 = v2.getVertexNormal();
					normal(n0);
					vertex(v0);
					normal(n1);
					vertex(v1);
					normal(n2);
					vertex(v2);
					home.endShape();
				}
			} else {
				for (int i = 0; i < tris.length; i += 3) {
					;
					home.beginShape(PConstants.TRIANGLES);
					v0 = vertices.get(tris[i]);
					v1 = vertices.get(tris[i + 1]);
					v2 = vertices.get(tris[i + 2]);
					vertex(v0);
					vertex(v1);
					vertex(v2);
					home.endShape();
				}
			}
		}
	}

	public void drawFace(final HE_Face f, final PImage texture) {
		drawFace(f, texture, false);
	}

	public void drawFace(final HE_Face f, final PImage texture, final boolean smooth) {
		final int fo = f.getFaceOrder();
		final List<HE_Vertex> vertices = f.getFaceVertices();
		if ((fo < 3) || (vertices.size() < 3)) {
		} else if (fo == 3) {
			final int[] tri = new int[] { 0, 1, 2 };
			HE_Vertex v0, v1, v2;
			WB_Coord n0, n1, n2;
			if (smooth) {
				home.beginShape(PConstants.TRIANGLES);
				home.texture(texture);
				v0 = vertices.get(tri[0]);
				n0 = v0.getVertexNormal();
				v1 = vertices.get(tri[1]);
				n1 = v1.getVertexNormal();
				v2 = vertices.get(tri[2]);
				n2 = v2.getVertexNormal();
				normal(n0);
				home.vertex(v0.xf(), v0.yf(), v0.zf(), v0.getUVW(f).uf(), v0.getUVW(f).vf());
				normal(n1);
				home.vertex(v1.xf(), v1.yf(), v1.zf(), v1.getUVW(f).uf(), v1.getUVW(f).vf());
				normal(n2);
				home.vertex(v2.xf(), v2.yf(), v2.zf(), v2.getUVW(f).uf(), v2.getUVW(f).vf());
				home.endShape();
			} else {
				home.beginShape(PConstants.TRIANGLES);
				home.texture(texture);
				v0 = vertices.get(tri[0]);
				v1 = vertices.get(tri[1]);
				v2 = vertices.get(tri[2]);
				home.vertex(v0.xf(), v0.yf(), v0.zf(), v0.getUVW(f).uf(), v0.getUVW(f).vf());
				home.vertex(v1.xf(), v1.yf(), v1.zf(), v1.getUVW(f).uf(), v1.getUVW(f).vf());
				home.vertex(v2.xf(), v2.yf(), v2.zf(), v2.getUVW(f).uf(), v2.getUVW(f).vf());
				home.endShape();
			}
		} else {
			final int[] tris = f.getTriangles();
			HE_Vertex v0, v1, v2;
			WB_Coord n0, n1, n2;
			if (smooth) {
				for (int i = 0; i < tris.length; i += 3) {
					home.beginShape(PConstants.TRIANGLES);
					home.texture(texture);
					v0 = vertices.get(tris[i]);
					n0 = v0.getVertexNormal();
					v1 = vertices.get(tris[i + 1]);
					n1 = v1.getVertexNormal();
					v2 = vertices.get(tris[i + 2]);
					n2 = v2.getVertexNormal();
					normal(n0);
					home.vertex(v0.xf(), v0.yf(), v0.zf(), v0.getUVW(f).uf(), v0.getUVW(f).vf());
					normal(n1);
					home.vertex(v1.xf(), v1.yf(), v1.zf(), v1.getUVW(f).uf(), v1.getUVW(f).vf());
					normal(n2);
					home.vertex(v2.xf(), v2.yf(), v2.zf(), v2.getUVW(f).uf(), v2.getUVW(f).vf());
					home.endShape();
				}
			} else {
				for (int i = 0; i < tris.length; i += 3) {
					home.beginShape(PConstants.TRIANGLES);
					home.texture(texture);
					v0 = vertices.get(tris[i]);
					v1 = vertices.get(tris[i + 1]);
					v2 = vertices.get(tris[i + 2]);
					home.vertex(v0.xf(), v0.yf(), v0.zf(), v0.getUVW(f).uf(), v0.getUVW(f).vf());
					home.vertex(v1.xf(), v1.yf(), v1.zf(), v1.getUVW(f).uf(), v1.getUVW(f).vf());
					home.vertex(v2.xf(), v2.yf(), v2.zf(), v2.getUVW(f).uf(), v2.getUVW(f).vf());
					home.endShape();
				}
			}
		}
	}

	public void drawFace(final HE_Face f, final PImage[] textures) {
		drawFace(f, textures, false);
	}

	public void drawFace(final HE_Face f, final PImage[] textures, final boolean smooth) {
		final int fo = f.getFaceOrder();
		final int fti = f.getTextureId();
		final List<HE_Vertex> vertices = f.getFaceVertices();
		if ((fo < 3) || (vertices.size() < 3)) {
		} else if (fo == 3) {
			final int[] tri = new int[] { 0, 1, 2 };
			HE_Vertex v0, v1, v2;
			WB_Coord n0, n1, n2;
			if (smooth) {
				home.beginShape(PConstants.TRIANGLES);
				home.texture(textures[fti]);
				v0 = vertices.get(tri[0]);
				n0 = v0.getVertexNormal();
				v1 = vertices.get(tri[1]);
				n1 = v1.getVertexNormal();
				v2 = vertices.get(tri[2]);
				n2 = v2.getVertexNormal();
				normal(n0);
				home.vertex(v0.xf(), v0.yf(), v0.zf(), v0.getUVW(f).uf(), v0.getUVW(f).vf());
				normal(n1);
				home.vertex(v1.xf(), v1.yf(), v1.zf(), v1.getUVW(f).uf(), v1.getUVW(f).vf());
				normal(n2);
				home.vertex(v2.xf(), v2.yf(), v2.zf(), v2.getUVW(f).uf(), v2.getUVW(f).vf());
				home.endShape();
			} else {
				home.beginShape(PConstants.TRIANGLES);
				home.texture(textures[fti]);
				v0 = vertices.get(tri[0]);
				v1 = vertices.get(tri[1]);
				v2 = vertices.get(tri[2]);
				home.vertex(v0.xf(), v0.yf(), v0.zf(), v0.getUVW(f).uf(), v0.getUVW(f).vf());
				home.vertex(v1.xf(), v1.yf(), v1.zf(), v1.getUVW(f).uf(), v1.getUVW(f).vf());
				home.vertex(v2.xf(), v2.yf(), v2.zf(), v2.getUVW(f).uf(), v2.getUVW(f).vf());
				home.endShape();
			}
		} else {
			final int[] tris = f.getTriangles();
			HE_Vertex v0, v1, v2;
			WB_Coord n0, n1, n2;
			if (smooth) {
				for (int i = 0; i < tris.length; i += 3) {
					home.beginShape(PConstants.TRIANGLES);
					home.texture(textures[fti]);
					v0 = vertices.get(tris[i]);
					n0 = v0.getVertexNormal();
					v1 = vertices.get(tris[i + 1]);
					n1 = v1.getVertexNormal();
					v2 = vertices.get(tris[i + 2]);
					n2 = v2.getVertexNormal();
					normal(n0);
					home.vertex(v0.xf(), v0.yf(), v0.zf(), v0.getUVW(f).uf(), v0.getUVW(f).vf());
					normal(n1);
					home.vertex(v1.xf(), v1.yf(), v1.zf(), v1.getUVW(f).uf(), v1.getUVW(f).vf());
					normal(n2);
					home.vertex(v2.xf(), v2.yf(), v2.zf(), v2.getUVW(f).uf(), v2.getUVW(f).vf());
					home.endShape();
				}
			} else {
				for (int i = 0; i < tris.length; i += 3) {
					home.beginShape(PConstants.TRIANGLES);
					home.texture(textures[fti]);
					v0 = vertices.get(tris[i]);
					v1 = vertices.get(tris[i + 1]);
					v2 = vertices.get(tris[i + 2]);
					home.vertex(v0.xf(), v0.yf(), v0.zf(), v0.getUVW(f).uf(), v0.getUVW(f).vf());
					home.vertex(v1.xf(), v1.yf(), v1.zf(), v1.getUVW(f).uf(), v1.getUVW(f).vf());
					home.vertex(v2.xf(), v2.yf(), v2.zf(), v2.getUVW(f).uf(), v2.getUVW(f).vf());
					home.endShape();
				}
			}
		}
	}

	/**
	 *
	 *
	 * @param key
	 * @param smooth
	 * @param mesh
	 */
	public void drawFace(final Long key, final boolean smooth, final HE_MeshStructure mesh) {
		final HE_Face f = mesh.getFaceWithKey(key);
		if (f != null) {
			drawFace(f, smooth);
		}
	}

	/**
	 *
	 *
	 * @param key
	 * @param mesh
	 */
	public void drawFace(final Long key, final HE_MeshStructure mesh) {
		final HE_Face f = mesh.getFaceWithKey(key);
		if (f != null) {
			drawFace(f, false);
		}
	}

	/**
	 *
	 *
	 * @param f
	 */
	public void drawFaceFC(final HE_Face f) {
		drawFaceFC(f, false);
	}

	/**
	 *
	 *
	 * @param f
	 * @param smooth
	 */
	public void drawFaceFC(final HE_Face f, final boolean smooth) {
		if (f.getFaceOrder() > 2) {
			home.pushStyle();
			home.fill(f.getColor());
			final int[] tris = f.getTriangles();
			final List<HE_Vertex> vertices = f.getFaceVertices();
			HE_Vertex v0, v1, v2;
			WB_Coord n0, n1, n2;
			if (smooth) {
				for (int i = 0; i < tris.length; i += 3) {
					home.beginShape(PConstants.TRIANGLES);
					v0 = vertices.get(tris[i]);
					n0 = v0.getVertexNormal();
					v1 = vertices.get(tris[i + 1]);
					n1 = v1.getVertexNormal();
					v2 = vertices.get(tris[i + 2]);
					n2 = v2.getVertexNormal();
					normal(n0);
					vertex(v0);
					normal(n1);
					vertex(v1);
					normal(n2);
					vertex(v2);
					home.endShape();
				}
			} else {
				for (int i = 0; i < tris.length; i += 3) {
					home.beginShape(PConstants.TRIANGLES);
					v0 = vertices.get(tris[i]);
					v1 = vertices.get(tris[i + 1]);
					v2 = vertices.get(tris[i + 2]);
					vertex(v0);
					vertex(v1);
					vertex(v2);
					home.endShape();
				}
			}
			home.popStyle();
		}
	}

	/**
	 *
	 *
	 * @param f
	 */
	public void drawFaceHC(final HE_Face f) {
		drawFaceHC(f, false);
	}

	/**
	 *
	 *
	 * @param f
	 * @param smooth
	 */
	public void drawFaceHC(final HE_Face f, final boolean smooth) {
		if (f.getFaceOrder() > 2) {
			final int[] tris = f.getTriangles();
			final List<HE_Vertex> vertices = f.getFaceVertices();
			final List<HE_Halfedge> halfedges = f.getFaceHalfedges();
			HE_Vertex v0, v1, v2;
			WB_Coord n0, n1, n2;
			if (smooth) {
				for (int i = 0; i < tris.length; i += 3) {
					home.beginShape(PConstants.TRIANGLES);
					v0 = vertices.get(tris[i]);
					n0 = v0.getVertexNormal();
					v1 = vertices.get(tris[i + 1]);
					n1 = v1.getVertexNormal();
					v2 = vertices.get(tris[i + 2]);
					n2 = v2.getVertexNormal();
					home.fill(halfedges.get(tris[i]).getColor());
					normal(n0);
					vertex(v0);
					home.fill(halfedges.get(tris[i + 1]).getColor());
					normal(n1);
					vertex(v1);
					home.fill(halfedges.get(tris[i + 2]).getColor());
					normal(n2);
					vertex(v2);
					home.endShape();
				}
			} else {
				for (int i = 0; i < tris.length; i += 3) {
					home.beginShape(PConstants.TRIANGLES);
					v0 = vertices.get(tris[i]);
					v1 = vertices.get(tris[i + 1]);
					v2 = vertices.get(tris[i + 2]);
					home.fill(halfedges.get(tris[i]).getColor());
					vertex(v0);
					home.fill(halfedges.get(tris[i + 1]).getColor());
					vertex(v1);
					home.fill(halfedges.get(tris[i + 2]).getColor());
					vertex(v2);
					home.endShape();
				}
			}
		}
	}

	/**
	 *
	 *
	 * @param f
	 * @param d
	 */
	public void drawFaceNormal(final HE_Face f, final double d) {
		final WB_Coord p1 = f.getFaceCenter();
		final WB_Point p2 = WB_Point.mul(f.getFaceNormal(), d).addSelf(p1);
		line(p1, p2);
	}

	/**
	 *
	 *
	 * @param d
	 * @param mesh
	 * @deprecated Use {@link #drawFaceNormals(HE_MeshStructure,double)} instead
	 */
	@Deprecated
	public void drawFaceNormals(final double d, final HE_MeshStructure mesh) {
		drawFaceNormals(mesh, d);
	}

	/**
	 *
	 *
	 * @param mesh
	 * @param d
	 */
	public void drawFaceNormals(final HE_MeshStructure mesh, final double d) {
		final Iterator<HE_Face> fItr = mesh.fItr();
		WB_Coord fc;
		WB_Coord fn;
		HE_Face f;
		while (fItr.hasNext()) {
			f = fItr.next();
			fc = f.getFaceCenter();
			fn = f.getFaceNormal();
			line(fc, WB_Point.addMul(fc, d, fn));
		}
	}

	public void drawFaceOffset(final HE_Face f, final double d) {
		final int fo = f.getFaceOrder();
		final List<HE_Vertex> vertices = f.getFaceVertices();
		if (fo < 3 || vertices.size() < 3) {
		} else if (fo == 3) {
			final WB_Coord fn = f.getFaceNormal();
			final int[] tri = new int[] { 0, 1, 2 };
			HE_Vertex v0, v1, v2;
			final float df = (float) d;
			home.beginShape(PConstants.TRIANGLES);
			v0 = vertices.get(tri[0]);
			v1 = vertices.get(tri[1]);
			v2 = vertices.get(tri[2]);
			home.vertex(v0.xf() + df * fn.xf(), v0.yf() + df * fn.yf(), v0.zf() + df * fn.zf());
			home.vertex(v1.xf() + df * fn.xf(), v1.yf() + df * fn.yf(), v1.zf() + df * fn.zf());
			home.vertex(v2.xf() + df * fn.xf(), v2.yf() + df * fn.yf(), v2.zf() + df * fn.zf());
			home.endShape();
		} else {
			final int[] tris = f.getTriangles();
			final WB_Coord fn = f.getFaceNormal();
			HE_Vertex v0, v1, v2;
			final float df = (float) d;
			for (int i = 0; i < tris.length; i += 3) {
				home.beginShape(PConstants.TRIANGLES);
				v0 = vertices.get(tris[i]);
				v1 = vertices.get(tris[i + 1]);
				v2 = vertices.get(tris[i + 2]);
				home.vertex(v0.xf() + df * fn.xf(), v0.yf() + df * fn.yf(), v0.zf() + df * fn.zf());
				home.vertex(v1.xf() + df * fn.xf(), v1.yf() + df * fn.yf(), v1.zf() + df * fn.zf());
				home.vertex(v2.xf() + df * fn.xf(), v2.yf() + df * fn.yf(), v2.zf() + df * fn.zf());
				home.endShape();
			}
		}
	}

	/**
	 *
	 *
	 * @param meshes
	 */
	public void drawFaces(final Collection<? extends HE_MeshStructure> meshes) {
		final Iterator<? extends HE_MeshStructure> mItr = meshes.iterator();
		while (mItr.hasNext()) {
			drawFaces(mItr.next());
		}
	}

	public void drawFaces(final HE_MeshCollection meshes) {
		final HE_MeshIterator mItr = meshes.mItr();
		while (mItr.hasNext()) {
			drawFaces(mItr.next());
		}
	}

	/**
	 * Draw mesh faces. Typically used with noStroke();
	 *
	 * @param mesh
	 *            the mesh
	 */
	public void drawFaces(final HE_MeshStructure mesh) {
		final Iterator<HE_Face> fItr = mesh.fItr();
		while (fItr.hasNext()) {
			drawFace(fItr.next());
		}
	}

	public void drawFaces(final HE_MeshStructure mesh, final PImage texture) {
		final Iterator<HE_Face> fItr = mesh.fItr();
		while (fItr.hasNext()) {
			drawFace(fItr.next(), texture);
		}
	}

	public void drawFaces(final HE_MeshStructure mesh, final PImage[] textures) {
		final Iterator<HE_Face> fItr = mesh.fItr();
		while (fItr.hasNext()) {
			drawFace(fItr.next(), textures);
		}
	}

	/**
	 *
	 *
	 * @param mesh
	 */
	public void drawFacesFC(final HE_MeshStructure mesh) {
		final Iterator<HE_Face> fItr = mesh.fItr();
		while (fItr.hasNext()) {
			drawFaceFC(fItr.next());
		}
	}

	/**
	 *
	 *
	 * @param mesh
	 */
	public void drawFacesHC(final HE_MeshStructure mesh) {
		final Iterator<HE_Face> fItr = mesh.fItr();
		while (fItr.hasNext()) {
			drawFaceHC(fItr.next());
		}
	}

	/**
	 *
	 *
	 * @param f
	 */
	public void drawFaceSmooth(final HE_Face f) {
		new ArrayList<HE_Vertex>();
		drawFace(f, true);
	}

	/**
	 * Draw one face using vertex normals.
	 *
	 * @param key
	 *            key of face
	 * @param mesh
	 *            the mesh
	 */
	public void drawFaceSmooth(final Long key, final HE_MeshStructure mesh) {
		new ArrayList<HE_Vertex>();
		final HE_Face f = mesh.getFaceWithKey(key);
		if (f != null) {
			drawFace(f, true);
		}
	}

	/**
	 *
	 *
	 * @param f
	 */
	public void drawFaceSmoothFC(final HE_Face f) {
		new ArrayList<HE_Vertex>();
		drawFaceFC(f, true);
	}

	/**
	 *
	 *
	 * @param key
	 * @param mesh
	 */
	public void drawFaceSmoothFC(final Long key, final HE_MeshStructure mesh) {
		new ArrayList<HE_Vertex>();
		final HE_Face f = mesh.getFaceWithKey(key);
		if (f != null) {
			drawFaceFC(f, true);
		}
	}

	/**
	 *
	 *
	 * @param f
	 */
	public void drawFaceSmoothHC(final HE_Face f) {
		new ArrayList<HE_Vertex>();
		drawFaceHC(f, true);
	}

	/**
	 *
	 *
	 * @param key
	 * @param mesh
	 */
	public void drawFaceSmoothHC(final Long key, final HE_MeshStructure mesh) {
		new ArrayList<HE_Vertex>();
		final HE_Face f = mesh.getFaceWithKey(key);
		if (f != null) {
			drawFaceHC(f, true);
		}
	}

	/**
	 *
	 *
	 * @param f
	 */
	public void drawFaceSmoothVC(final HE_Face f) {
		new ArrayList<HE_Vertex>();
		drawFaceVC(f, true);
	}

	/**
	 *
	 *
	 * @param key
	 * @param mesh
	 */
	public void drawFaceSmoothVC(final Long key, final HE_MeshStructure mesh) {
		new ArrayList<HE_Vertex>();
		final HE_Face f = mesh.getFaceWithKey(key);
		if (f != null) {
			drawFaceVC(f, true);
		}
	}

	/**
	 * Draw mesh faces using vertex normals. Typically used with noStroke().
	 *
	 * @param mesh
	 *            the mesh
	 */
	public void drawFacesSmooth(final HE_MeshStructure mesh) {
		new ArrayList<HE_Vertex>();
		final Iterator<HE_Face> fItr = mesh.fItr();
		while (fItr.hasNext()) {
			drawFace(fItr.next(), true);
		}
	}

	public void drawFacesSmooth(final HE_MeshStructure mesh, final PImage texture) {
		new ArrayList<HE_Vertex>();
		final Iterator<HE_Face> fItr = mesh.fItr();
		while (fItr.hasNext()) {
			drawFace(fItr.next(), texture, true);
		}
	}

	public void drawFacesSmooth(final HE_MeshStructure mesh, final PImage[] textures) {
		new ArrayList<HE_Vertex>();
		final Iterator<HE_Face> fItr = mesh.fItr();
		while (fItr.hasNext()) {
			drawFace(fItr.next(), textures, true);
		}
	}

	/**
	 *
	 *
	 * @param mesh
	 */
	public void drawFacesSmoothFC(final HE_MeshStructure mesh) {
		final Iterator<HE_Face> fItr = mesh.fItr();
		while (fItr.hasNext()) {
			drawFaceFC(fItr.next(), true);
		}
	}

	/**
	 *
	 *
	 * @param mesh
	 */
	public void drawFacesSmoothHC(final HE_MeshStructure mesh) {
		final Iterator<HE_Face> fItr = mesh.fItr();
		while (fItr.hasNext()) {
			drawFaceHC(fItr.next(), true);
		}
	}

	/**
	 *
	 *
	 * @param mesh
	 */
	public void drawFacesSmoothVC(final HE_MeshStructure mesh) {
		final Iterator<HE_Face> fItr = mesh.fItr();
		while (fItr.hasNext()) {
			drawFaceVC(fItr.next(), true);
		}
	}

	/**
	 *
	 *
	 * @param mesh
	 */
	public void drawFacesVC(final HE_MeshStructure mesh) {
		final Iterator<HE_Face> fItr = mesh.fItr();
		while (fItr.hasNext()) {
			drawFaceVC(fItr.next());
		}
	}

	/**
	 *
	 *
	 * @param label
	 * @param mesh
	 */
	public void drawFacesWithInternalLabel(final int label, final HE_MeshStructure mesh) {
		final Iterator<HE_Face> fItr = mesh.fItr();
		HE_Face f;
		while (fItr.hasNext()) {
			f = fItr.next();
			if (f.getInternalLabel() == label) {
				drawFace(f);
			}
		}
	}

	/**
	 * Draw mesh faces matching label. Typically used with noStroke();
	 *
	 * @param label
	 *            the label
	 * @param mesh
	 *            the mesh
	 */
	public void drawFacesWithLabel(final int label, final HE_MeshStructure mesh) {
		final Iterator<HE_Face> fItr = mesh.fItr();
		HE_Face f;
		while (fItr.hasNext()) {
			f = fItr.next();
			if (f.getLabel() == label) {
				drawFace(f);
			}
		}
	}

	/**
	 *
	 *
	 * @param f
	 */
	public void drawFaceVC(final HE_Face f) {
		drawFaceVC(f, false);
	}

	/**
	 *
	 *
	 * @param f
	 * @param smooth
	 */
	public void drawFaceVC(final HE_Face f, final boolean smooth) {
		if (f.getFaceOrder() > 2) {
			final int[] tris = f.getTriangles();
			final List<HE_Vertex> vertices = f.getFaceVertices();
			HE_Vertex v0, v1, v2;
			WB_Coord n0, n1, n2;
			if (smooth) {
				for (int i = 0; i < tris.length; i += 3) {
					home.beginShape(PConstants.TRIANGLES);
					v0 = vertices.get(tris[i]);
					n0 = v0.getVertexNormal();
					v1 = vertices.get(tris[i + 1]);
					n1 = v1.getVertexNormal();
					v2 = vertices.get(tris[i + 2]);
					n2 = v2.getVertexNormal();
					home.fill(v0.getColor());
					normal(n0);
					vertex(v0);
					home.fill(v1.getColor());
					normal(n1);
					vertex(v1);
					home.fill(v2.getColor());
					normal(n2);
					vertex(v2);
					home.endShape();
				}
			} else {
				for (int i = 0; i < tris.length; i += 3) {
					home.beginShape(PConstants.TRIANGLES);
					v0 = vertices.get(tris[i]);
					v1 = vertices.get(tris[i + 1]);
					v2 = vertices.get(tris[i + 2]);
					home.fill(v0.getColor());
					vertex(v0);
					home.fill(v1.getColor());
					vertex(v1);
					home.fill(v2.getColor());
					vertex(v2);
					home.endShape();
				}
			}
		}
	}

	/**
	 *
	 *
	 * @param frame
	 */
	public void drawFrame(final WB_Frame frame) {
		final ArrayList<WB_FrameStrut> struts = frame.getStruts();
		for (int i = 0; i < frame.getNumberOfStruts(); i++) {
			drawFrameStrut(struts.get(i));
		}
	}

	/**
	 *
	 *
	 * @param node
	 * @param s
	 */
	public void drawFrameNode(final WB_FrameNode node, final double s) {
		home.pushMatrix();
		translate(node);
		home.box((float) s);
		home.popMatrix();
	}

	/**
	 *
	 *
	 * @param strut
	 */
	public void drawFrameStrut(final WB_FrameStrut strut) {
		line(strut.start(), strut.end());
	}

	/**
	 *
	 *
	 * @param he
	 * @param d
	 * @param s
	 */
	public void drawHalfedge(final HE_Halfedge he, final double d, final double s) {
		final WB_Point c = new WB_Point(he.getHalfedgeCenter());
		c.addMulSelf(d, he.getHalfedgeNormal());
		home.stroke(255, 0, 0);
		line(he.getVertex(), c);
		if (he.getHalfedgeType() == WB_Classification.CONVEX) {
			home.stroke(0, 255, 0);
		} else if (he.getHalfedgeType() == WB_Classification.CONCAVE) {
			home.stroke(255, 0, 0);
		} else {
			home.stroke(0, 0, 255);
		}
		home.pushMatrix();
		translate(c);
		home.box((float) s);
		home.popMatrix();
	}

	/**
	 *
	 *
	 * @param he
	 * @param d
	 * @param s
	 * @param f
	 */
	public void drawHalfedge(final HE_Halfedge he, final double d, final double s, final double f) {
		final WB_Point c = geometryfactory.createInterpolatedPoint(he.getVertex(), he.getEndVertex(), f);
		c.addMulSelf(d, he.getHalfedgeNormal());
		line(he.getVertex(), c);
		if (he.getHalfedgeType() == WB_Classification.CONVEX) {
			home.stroke(0, 255, 0);
		} else if (he.getHalfedgeType() == WB_Classification.CONCAVE) {
			home.stroke(255, 0, 0);
		} else {
			home.stroke(0, 0, 255);
		}
		home.pushMatrix();
		translate(c);
		home.box((float) s);
		home.popMatrix();
	}

	/**
	 *
	 *
	 * @param key
	 * @param d
	 * @param s
	 * @param mesh
	 */
	public void drawHalfedge(final Long key, final double d, final double s, final HE_MeshStructure mesh) {
		final HE_Halfedge he = mesh.getHalfedgeWithKey(key);
		drawHalfedge(he, d, s);
	}

	/**
	 *
	 *
	 * @param d
	 * @param f
	 * @param mesh
	 * @deprecated Use {@link #drawHalfedges(HE_MeshStructure,double,double)}
	 *             instead
	 */
	@Deprecated
	public void drawHalfedges(final double d, final double f, final HE_MeshStructure mesh) {
		drawHalfedges(mesh, d, f);
	}

	/**
	 *
	 *
	 * @param d
	 * @param mesh
	 * @deprecated Use {@link #drawHalfedges(HE_MeshStructure,double)} instead
	 */
	@Deprecated
	public void drawHalfedges(final double d, final HE_MeshStructure mesh) {
		drawHalfedges(mesh, d);
	}

	/**
	 *
	 *
	 * @param mesh
	 * @param d
	 */
	public void drawHalfedges(final HE_MeshStructure mesh, final double d) {
		WB_Point c;
		HE_Halfedge he;
		final Iterator<HE_Halfedge> heItr = mesh.heItr();
		home.pushStyle();
		while (heItr.hasNext()) {
			he = heItr.next();
			if (he.getFace() != null) {
				c = new WB_Point(he.getHalfedgeCenter());
				c.addMulSelf(d, he.getHalfedgeNormal());
				home.stroke(255, 0, 0);
				line(he.getVertex(), c);
				if (he.getHalfedgeType() == WB_Classification.CONVEX) {
					home.stroke(0, 255, 0);
					home.fill(0, 255, 0);
				} else if (he.getHalfedgeType() == WB_Classification.CONCAVE) {
					home.stroke(255, 0, 0);
					home.fill(255, 0, 0);
				} else {
					home.stroke(0, 0, 255);
					home.fill(0, 0, 255);
				}
				home.pushMatrix();
				translate(c);
				home.box((float) d);
				home.popMatrix();
			} else {
				c = new WB_Point(he.getHalfedgeCenter());
				c.addMulSelf(-d, he.getPair().getHalfedgeNormal());
				home.stroke(255, 0, 0);
				line(he.getVertex(), c);
				home.stroke(0, 255, 255);
				home.pushMatrix();
				translate(c);
				home.box((float) d);
				home.popMatrix();
			}
		}
		home.popStyle();
	}

	/**
	 *
	 *
	 * @param mesh
	 * @param d
	 * @param f
	 */
	public void drawHalfedges(final HE_MeshStructure mesh, final double d, final double f) {
		WB_Point c;
		HE_Halfedge he;
		final Iterator<HE_Halfedge> heItr = mesh.heItr();
		home.pushStyle();
		while (heItr.hasNext()) {
			he = heItr.next();
			if (he.getFace() != null) {
				c = geometryfactory.createInterpolatedPoint(he.getVertex(), he.getEndVertex(), f);
				c.addMulSelf(d, he.getHalfedgeNormal());
				home.stroke(255, 0, 0);
				line(he.getVertex(), c);
				if (he.getHalfedgeType() == WB_Classification.CONVEX) {
					home.stroke(0, 255, 0);
					home.fill(0, 255, 0);
				} else if (he.getHalfedgeType() == WB_Classification.CONCAVE) {
					home.stroke(255, 0, 0);
					home.fill(255, 0, 0);
				} else {
					home.stroke(0, 0, 255);
					home.fill(0, 0, 255);
				}
				home.pushMatrix();
				translate(c);
				home.box((float) d);
				home.popMatrix();
			} else {
				c = geometryfactory.createInterpolatedPoint(he.getVertex(), he.getEndVertex(), f);
				c.addMulSelf(-d, he.getPair().getHalfedgeNormal());
				home.stroke(255, 0, 0);
				line(he.getVertex(), c);
				home.stroke(0, 255, 255);
				home.pushMatrix();
				translate(c);
				home.box((float) d);
				home.popMatrix();
			}
		}
		home.popStyle();
	}

	/**
	 *
	 *
	 * @param he
	 * @param d
	 * @param s
	 */
	public void drawHalfedgeSimple(final HE_Halfedge he, final double d, final double s) {
		final WB_Point c = new WB_Point(he.getHalfedgeCenter());
		c.addMulSelf(d, he.getHalfedgeNormal());
		line(he.getVertex(), c);
		home.pushMatrix();
		translate(c);
		home.box((float) s);
		home.popMatrix();
	}

	/**
	 * Draw leaf node.
	 *
	 * @param node
	 *            the node
	 */
	private void drawLeafNode(final WB_AABBNode node) {
		if (node.isLeaf()) {
			drawAABB(node.getAABB());
		} else {
			if (node.getPosChild() != null) {
				drawLeafNode(node.getPosChild());
			}
			if (node.getNegChild() != null) {
				drawLeafNode(node.getNegChild());
			}
			if (node.getMidChild() != null) {
				drawLeafNode(node.getMidChild());
			}
		}
	}

	/**
	 * Draw leafs.
	 *
	 * @param tree
	 *            the tree
	 */
	public void drawLeafs(final WB_AABBTree tree) {
		drawLeafNode(tree.getRoot());
	}

	/**
	 *
	 *
	 * @param L
	 * @param d
	 */

	public void drawLine(final WB_Line L, final double d) {
		home.line((float) (L.getOrigin().xd() - (d * L.getDirection().xd())),
				(float) (L.getOrigin().yd() - (d * L.getDirection().yd())),
				(float) (L.getOrigin().zd() - (d * L.getDirection().zd())),
				(float) (L.getOrigin().xd() + (d * L.getDirection().xd())),
				(float) (L.getOrigin().yd() + (d * L.getDirection().yd())),
				(float) (L.getOrigin().zd() + (d * L.getDirection().zd())));
	}

	/**
	 *
	 *
	 * @param L
	 * @param d
	 */
	public void drawLineEmbedded2D(final WB_Line L, final double d, WB_Map2D map) {
		drawSegmentEmbedded2D(WB_Point.addMul(L.getOrigin(), -d, L.getDirection()),
				WB_Point.addMul(L.getOrigin(), d, L.getDirection()), map);
	}

	/**
	 *
	 *
	 * @param L
	 * @param d
	 */
	public void drawLineMapped(final WB_Line L, final double d, WB_Map map) {
		drawSegmentMapped(WB_Point.addMul(L.getOrigin(), -d, L.getDirection()),
				WB_Point.addMul(L.getOrigin(), d, L.getDirection()), map);
	}

	/**
	 *
	 *
	 * @param L
	 * @param d
	 */
	public void drawLineUnmapped(final WB_Line L, final double d, WB_Map map) {
		drawSegmentUnmapped(WB_Point.addMul(L.getOrigin(), -d, L.getDirection()),
				WB_Point.addMul(L.getOrigin(), d, L.getDirection()), map);
	}

	/**
	 *
	 *
	 * @param mesh
	 */
	public void drawMesh(final WB_Mesh mesh) {
		if (mesh == null) {
			return;
		}
		for (final int[] face : mesh.getFacesAsInt()) {
			drawPolygon(face, mesh.getPoints());
		}
	}

	public void drawMeshEdges(final WB_Mesh mesh) {
		if (mesh == null) {
			return;
		}
		for (final int[] face : mesh.getFacesAsInt()) {
			drawPolygonEdges(face, mesh.getPoints());
		}
	}

	public void drawMeshFaces(final WB_Mesh mesh) {
		if (mesh == null) {
			return;
		}
		for (final int[] face : mesh.getFacesAsInt()) {
			drawPolygon(face, mesh.getPoints());
		}
	}

	/**
	 * Draw node.
	 *
	 * @param node
	 *            the node
	 */
	public void drawNode(final WB_AABBNode node) {
		drawAABB(node.getAABB());
		if (node.getPosChild() != null) {
			drawNode(node.getPosChild());
		}
		if (node.getNegChild() != null) {
			drawNode(node.getNegChild());
		}
		if (node.getMidChild() != null) {
			drawNode(node.getMidChild());
		}
	}

	/**
	 * Draw node.
	 *
	 * @param node
	 *            the node
	 * @param level
	 *            the level
	 */
	private void drawNode(final WB_AABBNode node, final int level) {
		if (node.getLevel() == level) {
			drawAABB(node.getAABB());
		}
		if (node.getLevel() < level) {
			if (node.getPosChild() != null) {
				drawNode(node.getPosChild(), level);
			}
			if (node.getNegChild() != null) {
				drawNode(node.getNegChild(), level);
			}
			if (node.getMidChild() != null) {
				drawNode(node.getMidChild(), level);
			}
		}
	}

	/**
	 * Draw nodes.
	 *
	 * @param frame
	 *            the frame
	 * @param s
	 *            the s
	 */
	public void drawNodes(final WB_Frame frame, final double s) {
		final ArrayList<WB_FrameNode> nodes = frame.getNodes();
		for (int i = 0; i < frame.getNumberOfNodes(); i++) {
			drawFrameNode(nodes.get(i), s);
		}
	}

	/**
	 *
	 *
	 * @param path
	 */
	public void drawPath(final HE_Path path) {
		home.beginShape();
		for (final HE_Vertex v : path.getPathVertices()) {
			home.vertex(v.xf(), v.yf(), v.zf());
		}
		if (path.isLoop()) {
			home.endShape(PConstants.CLOSE);
		} else {
			home.endShape(PConstants.OPEN);
		}
	}

	/**
	 *
	 *
	 * @param P
	 * @param d
	 */
	public void drawPlane(final WB_Plane P, final double d) {
		home.beginShape(PConstants.QUAD);
		home.vertex((float) (P.getOrigin().xd() - (d * P.getU().xd()) - (d * P.getV().xd())),
				(float) (P.getOrigin().yd() - (d * P.getU().yd()) - (d * P.getV().yd())),
				(float) (P.getOrigin().zd() - (d * P.getU().zd()) - (d * P.getV().zd())));
		home.vertex((float) ((P.getOrigin().xd() - (d * P.getU().xd())) + (d * P.getV().xd())),
				(float) ((P.getOrigin().yd() - (d * P.getU().yd())) + (d * P.getV().yd())),
				(float) ((P.getOrigin().zd() - (d * P.getU().zd())) + (d * P.getV().zd())));
		home.vertex((float) (P.getOrigin().xd() + (d * P.getU().xd()) + (d * P.getV().xd())),
				(float) (P.getOrigin().yd() + (d * P.getU().yd()) + (d * P.getV().yd())),
				(float) (P.getOrigin().zd() + (d * P.getU().zd()) + (d * P.getV().zd())));
		home.vertex((float) ((P.getOrigin().xd() + (d * P.getU().xd())) - (d * P.getV().xd())),
				(float) ((P.getOrigin().yd() + (d * P.getU().yd())) - (d * P.getV().yd())),
				(float) ((P.getOrigin().zd() + (d * P.getU().zd())) - (d * P.getV().zd())));
		home.endShape();
	}

	/**
	 *
	 *
	 * @param points
	 * @param d
	 */

	public void drawPoint(final Collection<? extends WB_Coord> points, final double d) {
		for (final WB_Coord v : points) {
			drawPoint(v, d);
		}
	}

	/**
	 *
	 *
	 * @param p
	 */

	public void drawPoint(final WB_Coord p) {
		home.point(p.xf(), p.yf(), p.zf());
	}

	/**
	 *
	 *
	 * @param p
	 * @param r
	 */

	public void drawPoint(final WB_Coord p, final double r) {
		home.pushMatrix();
		translate(p);
		home.box((float) r);
		home.popMatrix();
	}

	/**
	 *
	 *
	 * @param points
	 * @param d
	 */

	public void drawPoint(final WB_Coord[] points, final double d) {
		for (final WB_Coord v : points) {
			home.pushMatrix();
			translate(v);
			home.box((float) d);
			home.popMatrix();
		}
	}

	public void drawPointEmbedded2D(final Collection<? extends WB_Coord> points, final double r, WB_Map2D map) {
		for (final WB_Coord p : points) {
			drawPointEmbedded2D(p, r, map);
		}
	}

	public void drawPointEmbedded2D(final Collection<? extends WB_Coord> points, WB_Map2D map) {
		for (final WB_Coord p : points) {
			drawPointEmbedded2D(p, map);
		}
	}

	/**
	 *
	 *
	 * @param p
	 * @param r
	 */

	public void drawPointEmbedded2D(final WB_Coord p, final double r, WB_Map2D map) {
		WB_Point q = new WB_Point();
		map.mapPoint3D(p, q);
		map.unmapPoint2D(q.xd(), q.yd(), q);
		drawPoint(q, r);
	}

	/**
	 *
	 *
	 * @param p
	 */

	public void drawPointEmbedded2D(final WB_Coord p, WB_Map2D map) {
		WB_Point q = new WB_Point();
		map.mapPoint3D(p, q);
		map.unmapPoint2D(q.xd(), q.yd(), q);
		drawPoint(q);
	}

	public void drawPointEmbedded2D(final WB_Coord[] points, final double r, WB_Map2D map) {
		for (final WB_Coord p : points) {
			drawPointEmbedded2D(p, r, map);
		}
	}

	public void drawPointEmbedded2D(final WB_Coord[] points, WB_Map2D map) {
		for (final WB_Coord p : points) {
			drawPointEmbedded2D(p, map);
		}
	}

	public void drawPointMapped(final Collection<? extends WB_Coord> points, final double r, WB_Map map) {
		for (final WB_Coord p : points) {
			drawPointMapped(p, r, map);
		}
	}

	public void drawPointMapped(final Collection<? extends WB_Coord> points, WB_Map map) {
		for (final WB_Coord p : points) {
			drawPointMapped(p, map);
		}
	}

	/**
	 *
	 *
	 * @param p
	 * @param r
	 */

	public void drawPointMapped(final WB_Coord p, final double r, WB_Map map) {
		WB_Point q = new WB_Point();
		map.mapPoint3D(p, q);
		drawPoint(q, r);
	}

	/**
	 *
	 *
	 * @param p
	 */

	public void drawPointMapped(final WB_Coord p, WB_Map map) {
		WB_Point q = new WB_Point();
		map.mapPoint3D(p, q);
		drawPoint(q);
	}

	public void drawPointMapped(final WB_Coord[] points, final double r, WB_Map map) {
		for (final WB_Coord p : points) {
			drawPointMapped(p, r, map);
		}
	}

	public void drawPointMapped(final WB_Coord[] points, WB_Map map) {
		for (final WB_Coord p : points) {
			drawPointMapped(p, map);
		}
	}

	/**
	 *
	 *
	 * @param points
	 * @param d
	 * @deprecated Use {@link #drawPoint(Collection<? extends WB_Coord>,double)}
	 *             instead
	 */

	@Deprecated
	public void drawPoints(final Collection<? extends WB_Coord> points, final double d) {
		drawPoint(points, d);
	}

	/**
	 *
	 *
	 * @param points
	 * @param d
	 * @deprecated Use {@link #drawPoint(WB_Coord[],double)} instead
	 */

	@Deprecated
	public void drawPoints(final WB_Coord[] points, final double d) {
		drawPoint(points, d);
	}

	public void drawPointUnmapped(final Collection<? extends WB_Coord> points, final double r, WB_Map map) {
		for (final WB_Coord p : points) {
			drawPointUnmapped(p, r, map);
		}
	}

	public void drawPointUnmapped(final Collection<? extends WB_Coord> points, WB_Map map) {
		for (final WB_Coord p : points) {
			drawPointUnmapped(p, map);
		}
	}

	/**
	 *
	 *
	 * @param p
	 * @param r
	 */

	public void drawPointUnmapped(final WB_Coord p, final double r, WB_Map map) {
		WB_Point q = new WB_Point();
		map.mapPoint3D(p, q);
		drawPoint(q, r);
	}

	/**
	 *
	 *
	 * @param p
	 */

	public void drawPointUnmapped(final WB_Coord p, WB_Map map) {
		WB_Point q = new WB_Point();
		map.mapPoint3D(p, q);
		drawPoint(q);
	}

	public void drawPointUnmapped(final WB_Coord[] points, final double r, WB_Map map) {
		for (final WB_Coord p : points) {
			drawPointUnmapped(p, r, map);
		}
	}

	public void drawPointUnmapped(final WB_Coord[] points, WB_Map map) {
		for (final WB_Coord p : points) {
			drawPointUnmapped(p, map);
		}
	}

	/**
	 *
	 *
	 * @param polygons
	 */
	public void drawPolygon(final Collection<? extends WB_Polygon> polygons) {
		final Iterator<? extends WB_Polygon> polyItr = polygons.iterator();
		while (polyItr.hasNext()) {
			drawPolygon(polyItr.next());
		}
	}

	/**
	 *
	 *
	 * @param indices
	 * @param points
	 */
	private void drawPolygon(final int[] indices, final List<? extends WB_Coord> points) {
		if ((points != null) && (indices != null)) {
			home.beginShape(PConstants.POLYGON);
			for (final int indice : indices) {
				home.vertex(points.get(indice).xf(), points.get(indice).yf(), points.get(indice).zf());
			}
			home.endShape(PConstants.CLOSE);
		}
	}

	/**
	 *
	 *
	 * @param P
	 */

	public void drawPolygon(final WB_Polygon P) {
		final int[] tris = P.getTriangles();
		for (int i = 0; i < tris.length; i += 3) {
			drawTriangle(P.getPoint(tris[i]), P.getPoint(tris[i + 1]), P.getPoint(tris[i + 2]));
		}
	}

	/**
	 *
	 *
	 * @param polygons
	 */
	public void drawPolygonEdges(final Collection<? extends WB_Polygon> polygons) {
		final Iterator<? extends WB_Polygon> polyItr = polygons.iterator();
		while (polyItr.hasNext()) {
			drawPolygonEdges(polyItr.next());
		}
	}

	private void drawPolygonEdges(final int[] indices, final List<? extends WB_Coord> points) {
		if ((points != null) && (indices != null)) {
			home.beginShape();
			for (final int indice : indices) {
				home.vertex(points.get(indice).xf(), points.get(indice).yf(), points.get(indice).zf());
			}
			home.endShape(PConstants.CLOSE);
		}
	}

	/**
	 *
	 *
	 * @param P
	 */

	public void drawPolygonEdges(final WB_Polygon P) {
		final int[] npc = P.getNumberOfPointsPerContour();
		int index = 0;
		for (int i = 0; i < P.getNumberOfContours(); i++) {
			home.beginShape();
			for (int j = 0; j < npc[i]; j++) {
				vertex(P.getPoint(index++));
			}
			home.endShape(PConstants.CLOSE);
		}
	}

	/**
	 *
	 *
	 * @param P
	 */
	public void drawPolygonEdgesEmbedded2D(final WB_Polygon P, WB_Map2D map) {
		final int[] npc = P.getNumberOfPointsPerContour();
		int index = 0;
		for (int i = 0; i < P.getNumberOfContours(); i++) {
			home.beginShape();
			for (int j = 0; j < npc[i]; j++) {
				vertexEmbedded2D(P.getPoint(index++), map);
			}
			home.endShape(PConstants.CLOSE);
		}
	}

	/**
	 *
	 *
	 * @param P
	 */
	public void drawPolygonEdgesMapped(final WB_Polygon P, WB_Map map) {
		final int[] npc = P.getNumberOfPointsPerContour();
		int index = 0;
		for (int i = 0; i < P.getNumberOfContours(); i++) {
			home.beginShape();
			for (int j = 0; j < npc[i]; j++) {
				vertexMapped(P.getPoint(index++), map);
			}
			home.endShape(PConstants.CLOSE);
		}
	}

	/**
	 *
	 *
	 * @param P
	 */
	public void drawPolygonEdgesUnmapped(final WB_Polygon P, WB_Map map) {
		final int[] npc = P.getNumberOfPointsPerContour();
		int index = 0;
		for (int i = 0; i < P.getNumberOfContours(); i++) {
			home.beginShape();
			for (int j = 0; j < npc[i]; j++) {
				vertexUnmapped(P.getPoint(index++), map);
			}
			home.endShape(PConstants.CLOSE);
		}
	}

	/**
	 *
	 *
	 * @param P
	 */
	public void drawPolygonEmbedded2D(final WB_Polygon P, WB_Map2D map) {
		final int[] tris = P.getTriangles();
		for (int i = 0; i < tris.length; i += 3) {
			drawTriangleEmbedded2D(P.getPoint(tris[i]), P.getPoint(tris[i + 1]), P.getPoint(tris[i + 2]), map);
		}
	}

	/**
	 *
	 *
	 * @param P
	 */
	public void drawPolygonMapped(final WB_Polygon P, WB_Map map) {
		final int[] tris = P.getTriangles();
		for (int i = 0; i < tris.length; i += 3) {
			drawTriangleMapped(P.getPoint(tris[i]), P.getPoint(tris[i + 1]), P.getPoint(tris[i + 2]), map);
		}
	}

	/**
	 *
	 *
	 * @param P
	 */
	public void drawPolygonUnmapped(final WB_Polygon P, WB_Map map) {
		final int[] tris = P.getTriangles();
		for (int i = 0; i < tris.length; i += 3) {
			drawTriangleUnmapped(P.getPoint(tris[i]), P.getPoint(tris[i + 1]), P.getPoint(tris[i + 2]), map);
		}
	}

	/**
	 *
	 *
	 * @param polygons
	 * @param d
	 */
	public void drawPolygonVertices(final Collection<WB_Polygon> polygons, final double d) {
		final Iterator<WB_Polygon> polyItr = polygons.iterator();
		while (polyItr.hasNext()) {
			drawPolygonVertices(polyItr.next(), d);
		}
	}

	/**
	 *
	 *
	 * @param polygon
	 * @param d
	 */
	public void drawPolygonVertices(final WB_Polygon polygon, final double d) {
		WB_Coord v1;
		final int n = polygon.getNumberOfPoints();
		for (int i = 0; i < n; i++) {
			v1 = polygon.getPoint(i);
			home.pushMatrix();
			translate(v1);
			home.box((float) d);
			home.popMatrix();
		}
	}

	/**
	 *
	 *
	 * @param P
	 */

	public void drawPolyLine(final WB_PolyLine P) {
		for (int i = 0; i < (P.getNumberOfPoints() - 1); i++) {
			line(P.getPoint(i), P.getPoint(i + 1));
		}
	}

	/**
	 *
	 *
	 * @param polylines
	 */
	public void drawPolylineEdges(final Collection<WB_PolyLine> polylines) {
		final Iterator<WB_PolyLine> polyItr = polylines.iterator();
		while (polyItr.hasNext()) {
			drawPolylineEdges(polyItr.next());
		}
	}

	/**
	 *
	 *
	 * @param P
	 */
	public void drawPolylineEdges(final WB_PolyLine P) {
		for (int i = 0; i < (P.getNumberOfPoints() - 1); i++) {
			line(P.getPoint(i), P.getPoint(i + 1));
		}
	}

	/**
	 *
	 *
	 * @param P
	 */
	public void drawPolyLineEmbedded2D(final WB_PolyLine P, WB_Map2D map) {
		for (int i = 0; i < (P.getNumberOfPoints() - 1); i++) {
			drawSegmentEmbedded2D(P.getPoint(i), P.getPoint(i + 1), map);
		}
	}

	/**
	 *
	 *
	 * @param P
	 */
	public void drawPolyLineMapped(final WB_PolyLine P, WB_Map map) {
		for (int i = 0; i < (P.getNumberOfPoints() - 1); i++) {
			drawSegmentMapped(P.getPoint(i), P.getPoint(i + 1), map);
		}
	}

	/**
	 *
	 *
	 * @param P
	 */
	public void drawPolyLineUnmapped(final WB_PolyLine P, WB_Map map) {
		for (int i = 0; i < (P.getNumberOfPoints() - 1); i++) {
			drawSegmentUnmapped(P.getPoint(i), P.getPoint(i + 1), map);
		}
	}

	/**
	 *
	 *
	 * @param polylines
	 * @param d
	 */
	public void drawPolylineVertices(final Collection<WB_PolyLine> polylines, final double d) {
		final Iterator<WB_PolyLine> polyItr = polylines.iterator();
		while (polyItr.hasNext()) {
			drawPolylineVertices(polyItr.next(), d);
		}
	}

	/**
	 *
	 *
	 * @param P
	 * @param d
	 */
	public void drawPolylineVertices(final WB_PolyLine P, final double d) {
		WB_Point v1;
		for (int i = 0; i < P.getNumberOfPoints(); i++) {
			v1 = P.getPoint(i);
			home.pushMatrix();
			translate(v1);
			home.box((float) d);
			home.popMatrix();
		}
	}

	/**
	 *
	 *
	 * @param R
	 * @param d
	 */

	public void drawRay(final WB_Ray R, final double d) {
		home.line((float) (R.getOrigin().xd()), (float) (R.getOrigin().yd()), (float) (R.getOrigin().zd()),
				(float) (R.getOrigin().xd() + (d * R.getDirection().xd())),
				(float) (R.getOrigin().yd() + (d * R.getDirection().yd())),
				(float) (R.getOrigin().zd() + (d * R.getDirection().zd())));
	}

	/**
	 *
	 *
	 * @param R
	 * @param d
	 */
	public void drawRayEmbedded2D(final WB_Ray R, final double d, WB_Map2D map) {
		drawSegmentEmbedded2D(R.getOrigin(), WB_Point.addMul(R.getOrigin(), d, R.getDirection()), map);
	}

	/**
	 *
	 *
	 * @param R
	 * @param d
	 */
	public void drawRayMapped(final WB_Ray R, final double d, WB_Map map) {
		drawSegmentMapped(R.getOrigin(), WB_Point.addMul(R.getOrigin(), d, R.getDirection()), map);
	}

	/**
	 *
	 *
	 * @param R
	 * @param d
	 */
	public void drawRayUnmapped(final WB_Ray R, final double d, WB_Map map) {
		drawSegmentUnmapped(R.getOrigin(), WB_Point.addMul(R.getOrigin(), d, R.getDirection()), map);
	}

	/**
	 *
	 *
	 * @param P
	 */

	public void drawRing(final WB_Ring P) {
		for (int i = 0, j = P.getNumberOfPoints() - 1; i < P.getNumberOfPoints(); j = i++) {
			line(P.getPoint(j), P.getPoint(i));
		}
	}

	/**
	 *
	 *
	 * @param P
	 */
	public void drawRingEmbedded2D(final WB_Ring P, WB_Map2D map) {
		for (int i = 0, j = P.getNumberOfPoints() - 1; i < P.getNumberOfPoints(); j = i++) {
			drawSegmentEmbedded2D(P.getPoint(j), P.getPoint(i), map);
		}
	}

	/**
	 *
	 *
	 * @param P
	 */
	public void drawRingMapped(final WB_Ring P, WB_Map map) {
		for (int i = 0, j = P.getNumberOfPoints() - 1; i < P.getNumberOfPoints(); j = i++) {
			drawSegmentMapped(P.getPoint(j), P.getPoint(i), map);
		}
	}

	/**
	 *
	 *
	 * @param P
	 */
	public void drawRingUnmapped(final WB_Ring P, WB_Map map) {
		for (int i = 0, j = P.getNumberOfPoints() - 1; i < P.getNumberOfPoints(); j = i++) {
			drawSegmentUnmapped(P.getPoint(j), P.getPoint(i), map);
		}
	}

	/**
	 *
	 *
	 * @param segments
	 */

	public void drawSegment(final Collection<? extends WB_Segment> segments) {
		final Iterator<? extends WB_Segment> segItr = segments.iterator();
		while (segItr.hasNext()) {
			drawSegment(segItr.next());
		}
	}

	/**
	 *
	 *
	 * @param p
	 * @param q
	 */

	public void drawSegment(final WB_Coord p, final WB_Coord q) {
		line(p, q);
	}

	/**
	 *
	 *
	 * @param S
	 */

	public void drawSegment(final WB_Segment S) {
		line(S.getOrigin(), S.getEndpoint());
	}

	/**
	 *
	 *
	 * @param segments
	 */
	public void drawSegmentEmbedded2D(final Collection<? extends WB_Segment> segments, WB_Map2D map) {
		final Iterator<? extends WB_Segment> segItr = segments.iterator();
		while (segItr.hasNext()) {
			drawSegmentEmbedded2D(segItr.next(), map);
		}
	}

	/**
	 *
	 *
	 * @param p
	 * @param q
	 */
	public void drawSegmentEmbedded2D(final WB_Coord p, final WB_Coord q, WB_Map2D map) {
		home.beginShape();
		vertexEmbedded2D(p, map);
		vertexEmbedded2D(q, map);
		home.endShape();
	}

	/**
	 *
	 *
	 * @param segment
	 */
	public void drawSegmentEmbedded2D(final WB_Segment segment, WB_Map2D map) {
		drawSegmentEmbedded2D(segment.getOrigin(), segment.getEndpoint(), map);
	}

	/**
	 *
	 *
	 * @param segments
	 */
	public void drawSegmentEmbedded2D(final WB_Segment[] segments, WB_Map2D map) {
		for (final WB_Segment segment : segments) {
			drawSegmentEmbedded2D(segment, map);
		}
	}

	/**
	 *
	 *
	 * @param segments
	 */
	public void drawSegmentMapped(final Collection<? extends WB_Segment> segments, WB_Map map) {
		final Iterator<? extends WB_Segment> segItr = segments.iterator();
		while (segItr.hasNext()) {
			drawSegmentMapped(segItr.next(), map);
		}
	}

	/**
	 *
	 *
	 * @param p
	 * @param q
	 */
	public void drawSegmentMapped(final WB_Coord p, final WB_Coord q, WB_Map map) {
		home.beginShape();
		vertexMapped(p, map);
		vertexMapped(q, map);
		home.endShape();
	}

	/**
	 *
	 *
	 * @param segment
	 */
	public void drawSegmentMapped(final WB_Segment segment, WB_Map map) {
		drawSegmentMapped(segment.getOrigin(), segment.getEndpoint(), map);
	}

	/**
	 *
	 *
	 * @param segments
	 */
	public void drawSegmentMapped(final WB_Segment[] segments, WB_Map map) {
		for (final WB_Segment segment : segments) {
			drawSegmentMapped(segment, map);
		}
	}

	/**
	 *
	 *
	 * @param segments
	 */
	public void drawSegmentUnmapped(final Collection<? extends WB_Segment> segments, WB_Map map) {
		final Iterator<? extends WB_Segment> segItr = segments.iterator();
		while (segItr.hasNext()) {
			drawSegmentUnmapped(segItr.next(), map);
		}
	}

	/**
	 *
	 *
	 * @param p
	 * @param q
	 */
	public void drawSegmentUnmapped(final WB_Coord p, final WB_Coord q, WB_Map map) {
		home.beginShape();
		vertexUnmapped(p, map);
		vertexUnmapped(q, map);
		home.endShape();
	}

	/**
	 *
	 *
	 * @param segment
	 */
	public void drawSegmentUnmapped(final WB_Segment segment, WB_Map map) {
		drawSegmentUnmapped(segment.getOrigin(), segment.getEndpoint(), map);
	}

	/**
	 *
	 *
	 * @param segments
	 */
	public void drawSegmentUnmapped(final WB_Segment[] segments, WB_Map map) {
		for (final WB_Segment segment : segments) {
			drawSegmentUnmapped(segment, map);
		}
	}

	/**
	 *
	 *
	 * @param P
	 */
	public void drawSimplePolygon(final WB_Polygon P) {
		{
			home.beginShape(PConstants.POLYGON);
			for (int i = 0; i < P.getNumberOfPoints(); i++) {
				vertex(P.getPoint(i));
			}
		}
		home.endShape();
	}

	/**
	 *
	 *
	 * @param indices
	 * @param points
	 */
	public void drawTetrahedron(final int[] indices, final List<? extends WB_Coord> points) {
		if ((points != null) && (indices != null)) {
			for (int i = 0; i < indices.length; i += 4) {
				drawTetrahedron(points.get(indices[i]), points.get(indices[i + 1]), points.get(indices[i + 2]),
						points.get(indices[i + 3]));
			}
		}
	}

	/**
	 *
	 *
	 * @param indices
	 * @param points
	 */
	public void drawTetrahedron(final int[] indices, final WB_Coord[] points) {
		if ((points != null) && (indices != null)) {
			for (int i = 0; i < indices.length; i += 4) {
				drawTetrahedron(points[indices[i]], points[indices[i + 1]], points[indices[i + 2]],
						points[indices[i + 3]]);
			}
		}
	}

	/**
	 *
	 *
	 * @param p0
	 * @param p1
	 * @param p2
	 * @param p3
	 */
	public void drawTetrahedron(final WB_Coord p0, final WB_Coord p1, final WB_Coord p2, final WB_Coord p3) {
		home.beginShape(PConstants.TRIANGLES);
		vertex(p0);
		vertex(p1);
		vertex(p2);
		vertex(p1);
		vertex(p0);
		vertex(p3);
		vertex(p2);
		vertex(p1);
		vertex(p3);
		vertex(p0);
		vertex(p2);
		vertex(p3);
		home.endShape();
	}

	/**
	 *
	 *
	 * @param tree
	 */
	public void drawTree(final WB_AABBTree tree) {
		drawNode(tree.getRoot());
	}

	/**
	 *
	 *
	 * @param tree
	 * @param level
	 */
	public void drawTree(final WB_AABBTree tree, final int level) {
		drawNode(tree.getRoot(), level);
	}

	/**
	 *
	 *
	 * @param triangles
	 */
	public void drawTriangle(final Collection<? extends WB_Triangle> triangles) {
		final Iterator<? extends WB_Triangle> triItr = triangles.iterator();
		while (triItr.hasNext()) {
			drawTriangle(triItr.next());
		}
	}

	/**
	 *
	 *
	 * @param tri
	 * @param points
	 */

	public void drawTriangle(final int[] tri, final List<? extends WB_Coord> points) {
		for (int i = 0; i < tri.length; i += 3) {
			home.beginShape(PConstants.TRIANGLES);
			vertex(points.get(tri[i]));
			vertex(points.get(tri[i + 1]));
			vertex(points.get(tri[i + 2]));
			home.endShape();
		}
	}

	/**
	 *
	 *
	 * @param tri
	 * @param points
	 */

	public void drawTriangle(final int[] tri, final WB_Coord[] points) {
		for (int i = 0; i < tri.length; i += 3) {
			home.beginShape(PConstants.TRIANGLES);
			vertex(points[tri[i]]);
			vertex(points[tri[i + 1]]);
			vertex(points[tri[i + 2]]);
			home.endShape();
		}
	}

	/**
	 *
	 *
	 * @param p1
	 * @param p2
	 * @param p3
	 */

	public void drawTriangle(final WB_Coord p1, final WB_Coord p2, final WB_Coord p3) {
		home.beginShape(PConstants.TRIANGLES);
		vertex(p1);
		vertex(p2);
		vertex(p3);
		home.endShape();
	}

	/**
	 *
	 *
	 * @param triangle
	 */

	public void drawTriangle(final WB_Triangle triangle) {
		home.beginShape();
		home.vertex(triangle.p1().xf(), triangle.p1().yf(), triangle.p1().zf());
		home.vertex(triangle.p2().xf(), triangle.p2().yf(), triangle.p2().zf());
		home.vertex(triangle.p3().xf(), triangle.p3().yf(), triangle.p3().zf());
		home.endShape(PConstants.CLOSE);
	}

	/**
	 *
	 *
	 * @param triangles
	 */

	public void drawTriangleEdges(final Collection<? extends WB_Triangle> triangles) {
		final Iterator<? extends WB_Triangle> triItr = triangles.iterator();
		while (triItr.hasNext()) {
			drawTriangleEdges(triItr.next());
		}
	}

	/**
	 *
	 *
	 * @param triangle
	 */

	public void drawTriangleEdges(final WB_Triangle triangle) {
		line(triangle.p1(), triangle.p2());
		line(triangle.p3(), triangle.p2());
		line(triangle.p1(), triangle.p3());
	}

	/**
	 *
	 *
	 * @param triangles
	 */
	public void drawTriangleEdgesEmbedded2D(final Collection<? extends WB_Triangle> triangles, WB_Map2D map) {
		final Iterator<? extends WB_Triangle> triItr = triangles.iterator();
		while (triItr.hasNext()) {
			drawTriangleEdgesEmbedded2D(triItr.next(), map);
		}
	}

	/**
	 *
	 *
	 * @param triangle
	 */
	public void drawTriangleEdgesEmbedded2D(final WB_Triangle triangle, WB_Map2D map) {
		drawSegmentEmbedded2D(triangle.p1(), triangle.p2(), map);
		drawSegmentEmbedded2D(triangle.p2(), triangle.p3(), map);
		drawSegmentEmbedded2D(triangle.p3(), triangle.p1(), map);
	}

	/**
	 *
	 *
	 * @param triangles
	 */
	public void drawTriangleEdgesEmbedded2D(final WB_Triangle[] triangles, WB_Map2D map) {
		for (final WB_Triangle triangle : triangles) {
			drawTriangleEdgesEmbedded2D(triangle, map);
		}
	}

	/**
	 *
	 *
	 * @param triangles
	 */
	public void drawTriangleEdgesMapped(final Collection<? extends WB_Triangle> triangles, WB_Map map) {
		final Iterator<? extends WB_Triangle> triItr = triangles.iterator();
		while (triItr.hasNext()) {
			drawTriangleEdgesMapped(triItr.next(), map);
		}
	}

	/**
	 *
	 *
	 * @param triangle
	 */
	public void drawTriangleEdgesMapped(final WB_Triangle triangle, WB_Map map) {
		drawSegmentMapped(triangle.p1(), triangle.p2(), map);
		drawSegmentMapped(triangle.p2(), triangle.p3(), map);
		drawSegmentMapped(triangle.p3(), triangle.p1(), map);
	}

	/**
	 *
	 *
	 * @param triangles
	 */
	public void drawTriangleEdgesMapped(final WB_Triangle[] triangles, WB_Map map) {
		for (final WB_Triangle triangle : triangles) {
			drawTriangleEdgesMapped(triangle, map);
		}
	}

	/**
	 *
	 *
	 * @param triangles
	 */
	public void drawTriangleEdgesUnmapped(final Collection<? extends WB_Triangle> triangles, WB_Map map) {
		final Iterator<? extends WB_Triangle> triItr = triangles.iterator();
		while (triItr.hasNext()) {
			drawTriangleEdgesUnmapped(triItr.next(), map);
		}
	}

	/**
	 *
	 *
	 * @param triangle
	 */
	public void drawTriangleEdgesUnmapped(final WB_Triangle triangle, WB_Map map) {
		drawSegmentUnmapped(triangle.p1(), triangle.p2(), map);
		drawSegmentUnmapped(triangle.p2(), triangle.p3(), map);
		drawSegmentUnmapped(triangle.p3(), triangle.p1(), map);
	}

	/**
	 *
	 *
	 * @param triangles
	 */
	public void drawTriangleEdgesUnmapped(final WB_Triangle[] triangles, WB_Map map) {
		for (final WB_Triangle triangle : triangles) {
			drawTriangleEdgesUnmapped(triangle, map);
		}
	}

	/**
	 *
	 *
	 * @param triangles
	 */
	public void drawTriangleEmbedded2D(final Collection<? extends WB_Triangle> triangles, WB_Map2D map) {
		final Iterator<? extends WB_Triangle> triItr = triangles.iterator();
		while (triItr.hasNext()) {
			drawTriangleEmbedded2D(triItr.next(), map);
		}
	}

	/**
	 *
	 *
	 * @param tri
	 * @param points
	 */
	public void drawTriangleEmbedded2D(final int[] tri, final List<? extends WB_Coord> points, WB_Map2D map) {
		for (int i = 0; i < tri.length; i += 3) {
			home.beginShape(PConstants.TRIANGLES);
			vertexEmbedded2D(points.get(tri[i]), map);
			vertexEmbedded2D(points.get(tri[i + 1]), map);
			vertexEmbedded2D(points.get(tri[i + 2]), map);
			home.endShape();
		}
	}

	/**
	 *
	 *
	 * @param tri
	 * @param points
	 */
	public void drawTriangleEmbedded2D(final int[] tri, final WB_Coord[] points, WB_Map2D map) {
		for (int i = 0; i < tri.length; i += 3) {
			home.beginShape(PConstants.TRIANGLES);
			vertexEmbedded2D(points[tri[i]], map);
			vertexEmbedded2D(points[tri[i + 1]], map);
			vertexEmbedded2D(points[tri[i + 2]], map);
			home.endShape();
		}
	}

	/**
	 *
	 *
	 * @param p1
	 * @param p2
	 * @param p3
	 */
	public void drawTriangleEmbedded2D(final WB_Coord p1, final WB_Coord p2, final WB_Coord p3, WB_Map2D map) {
		home.beginShape(PConstants.TRIANGLES);
		vertexEmbedded2D(p1, map);
		vertexEmbedded2D(p2, map);
		vertexEmbedded2D(p3, map);
		home.endShape();
	}

	/**
	 *
	 *
	 * @param T
	 */
	public void drawTriangleEmbedded2D(final WB_Triangle T, WB_Map2D map) {
		home.beginShape(PConstants.TRIANGLES);
		vertexEmbedded2D(T.p1(), map);
		vertexEmbedded2D(T.p2(), map);
		vertexEmbedded2D(T.p3(), map);
		home.endShape();
	}

	/**
	 *
	 *
	 * @param triangles
	 */
	public void drawTriangleEmbedded2D(final WB_Triangle[] triangles, WB_Map2D map) {
		for (final WB_Triangle triangle : triangles) {
			drawTriangleEmbedded2D(triangle, map);
		}
	}

	/**
	 *
	 *
	 * @param triangles
	 */
	public void drawTriangleMapped(final Collection<? extends WB_Triangle> triangles, WB_Map map) {
		final Iterator<? extends WB_Triangle> triItr = triangles.iterator();
		while (triItr.hasNext()) {
			drawTriangleMapped(triItr.next(), map);
		}
	}

	/**
	 *
	 *
	 * @param tri
	 * @param points
	 */
	public void drawTriangleMapped(final int[] tri, final List<? extends WB_Coord> points, WB_Map map) {
		for (int i = 0; i < tri.length; i += 3) {
			home.beginShape(PConstants.TRIANGLES);
			vertexMapped(points.get(tri[i]), map);
			vertexMapped(points.get(tri[i + 1]), map);
			vertexMapped(points.get(tri[i + 2]), map);
			home.endShape();
		}
	}

	/**
	 *
	 *
	 * @param tri
	 * @param points
	 */
	public void drawTriangleMapped(final int[] tri, final WB_Coord[] points, WB_Map map) {
		for (int i = 0; i < tri.length; i += 3) {
			home.beginShape(PConstants.TRIANGLES);
			vertexMapped(points[tri[i]], map);
			vertexMapped(points[tri[i + 1]], map);
			vertexMapped(points[tri[i + 2]], map);
			home.endShape();
		}
	}

	/**
	 *
	 *
	 * @param p1
	 * @param p2
	 * @param p3
	 */
	public void drawTriangleMapped(final WB_Coord p1, final WB_Coord p2, final WB_Coord p3, WB_Map map) {
		home.beginShape(PConstants.TRIANGLES);
		vertexMapped(p1, map);
		vertexMapped(p2, map);
		vertexMapped(p3, map);
		home.endShape();
	}

	/**
	 *
	 *
	 * @param T
	 */
	public void drawTriangleMapped(final WB_Triangle T, WB_Map map) {
		home.beginShape(PConstants.TRIANGLES);
		vertexMapped(T.p1(), map);
		vertexMapped(T.p2(), map);
		vertexMapped(T.p3(), map);
		home.endShape();
	}

	/**
	 *
	 *
	 * @param triangles
	 */
	public void drawTriangleMapped(final WB_Triangle[] triangles, WB_Map map) {
		for (final WB_Triangle triangle : triangles) {
			drawTriangleMapped(triangle, map);
		}
	}

	/**
	 *
	 *
	 * @param triangles
	 */
	public void drawTriangleUnmapped(final Collection<? extends WB_Triangle> triangles, WB_Map map) {
		final Iterator<? extends WB_Triangle> triItr = triangles.iterator();
		while (triItr.hasNext()) {
			drawTriangleUnmapped(triItr.next(), map);
		}
	}

	/**
	 *
	 *
	 * @param tri
	 * @param points
	 */
	public void drawTriangleUnmapped(final int[] tri, final List<? extends WB_Coord> points, WB_Map map) {
		for (int i = 0; i < tri.length; i += 3) {
			home.beginShape(PConstants.TRIANGLES);
			vertexUnmapped(points.get(tri[i]), map);
			vertexUnmapped(points.get(tri[i + 1]), map);
			vertexUnmapped(points.get(tri[i + 2]), map);
			home.endShape();
		}
	}

	/**
	 *
	 *
	 * @param tri
	 * @param points
	 */
	public void drawTriangleUnmapped(final int[] tri, final WB_Coord[] points, WB_Map map) {
		for (int i = 0; i < tri.length; i += 3) {
			home.beginShape(PConstants.TRIANGLES);
			vertexUnmapped(points[tri[i]], map);
			vertexUnmapped(points[tri[i + 1]], map);
			vertexUnmapped(points[tri[i + 2]], map);
			home.endShape();
		}
	}

	/**
	 *
	 *
	 * @param p1
	 * @param p2
	 * @param p3
	 */
	public void drawTriangleUnmapped(final WB_Coord p1, final WB_Coord p2, final WB_Coord p3, WB_Map map) {
		home.beginShape(PConstants.TRIANGLES);
		vertexUnmapped(p1, map);
		vertexUnmapped(p2, map);
		vertexUnmapped(p3, map);
		home.endShape();
	}

	/**
	 *
	 *
	 * @param T
	 */
	public void drawTriangleUnmapped(final WB_Triangle T, WB_Map map) {
		home.beginShape(PConstants.TRIANGLES);
		vertexUnmapped(T.p1(), map);
		vertexUnmapped(T.p2(), map);
		vertexUnmapped(T.p3(), map);
		home.endShape();
	}

	/**
	 *
	 *
	 * @param triangles
	 */
	public void drawTriangleUnmapped(final WB_Triangle[] triangles, WB_Map map) {
		for (final WB_Triangle triangle : triangles) {
			drawTriangleUnmapped(triangle, map);
		}
	}

	/**
	 *
	 *
	 * @param tri
	 * @param points
	 */

	public void drawTriangulation(final WB_Triangulation2D tri, final List<? extends WB_Coord> points) {
		final int[] triangles = tri.getTriangles();
		home.beginShape(PConstants.TRIANGLES);
		for (int i = 0; i < triangles.length; i += 3) {
			vertex(points.get(triangles[i]));
			vertex(points.get(triangles[i + 1]));
			vertex(points.get(triangles[i + 2]));
		}
		home.endShape();
	}

	/**
	 *
	 *
	 * @param tri
	 * @param points
	 */

	public void drawTriangulation(final WB_Triangulation2D tri, final WB_Coord[] points) {
		final int[] triangles = tri.getTriangles();
		home.beginShape(PConstants.TRIANGLES);
		for (int i = 0; i < triangles.length; i += 3) {
			vertex(points[triangles[i]]);
			vertex(points[triangles[i + 1]]);
			vertex(points[triangles[i + 2]]);
		}
		home.endShape();
	}

	public void drawTriangulation(final WB_Triangulation3D tri, final List<? extends WB_Coord> points) {
		drawTetrahedron(tri.getTetrahedra(), points);
	}

	public void drawTriangulation(final WB_Triangulation3D tri, final WB_Coord[] points) {
		drawTetrahedron(tri.getTetrahedra(), points);
	}

	/**
	 *
	 *
	 * @param tri
	 * @param points
	 */

	public void drawTriangulationEdges(final WB_Triangulation2D tri, final List<? extends WB_Coord> points) {
		final int[] edges = tri.getEdges();
		for (int i = 0; i < edges.length; i += 2) {
			drawSegment(points.get(edges[i]), points.get(edges[i + 1]));
		}
	}

	/**
	 *
	 *
	 * @param tri
	 * @param points
	 */

	public void drawTriangulationEdges(final WB_Triangulation2D tri, final WB_Coord[] points) {
		final int[] edges = tri.getEdges();
		for (int i = 0; i < edges.length; i += 2) {
			drawSegment(points[edges[i]], points[edges[i + 1]]);
		}
	}

	/**
	 *
	 *
	 * @param tri
	 * @param points
	 */
	public void drawTriangulationEdgesEmbedded2D(final WB_Triangulation2D tri, final List<? extends WB_Coord> points,
			WB_Map2D map) {
		final int[] edges = tri.getEdges();
		for (int i = 0; i < edges.length; i += 2) {
			drawSegmentEmbedded2D(points.get(edges[i]), points.get(edges[i + 1]), map);
		}
	}

	/**
	 *
	 *
	 * @param tri
	 * @param points
	 */
	public void drawTriangulationEdgesEmbedded2D(final WB_Triangulation2D tri, final WB_Coord[] points, WB_Map2D map) {
		final int[] edges = tri.getEdges();
		for (int i = 0; i < edges.length; i += 2) {
			drawSegmentEmbedded2D(points[edges[i]], points[edges[i + 1]], map);
		}
	}

	/**
	 *
	 *
	 * @param tri
	 * @param points
	 */
	public void drawTriangulationEdgesMapped(final WB_Triangulation2D tri, final List<? extends WB_Coord> points,
			WB_Map map) {
		final int[] edges = tri.getEdges();
		for (int i = 0; i < edges.length; i += 2) {
			drawSegmentMapped(points.get(edges[i]), points.get(edges[i + 1]), map);
		}
	}

	/**
	 *
	 *
	 * @param tri
	 * @param points
	 */
	public void drawTriangulationEdgesMapped(final WB_Triangulation2D tri, final WB_Coord[] points, WB_Map map) {
		final int[] edges = tri.getEdges();
		for (int i = 0; i < edges.length; i += 2) {
			drawSegmentMapped(points[edges[i]], points[edges[i + 1]], map);
		}
	}

	/**
	 *
	 *
	 * @param tri
	 * @param points
	 */
	public void drawTriangulationEdgesUnmapped(final WB_Triangulation2D tri, final List<? extends WB_Coord> points,
			WB_Map map) {
		final int[] edges = tri.getEdges();
		for (int i = 0; i < edges.length; i += 2) {
			drawSegmentUnmapped(points.get(edges[i]), points.get(edges[i + 1]), map);
		}
	}

	/**
	 *
	 *
	 * @param tri
	 * @param points
	 */
	public void drawTriangulationEdgesUnmapped(final WB_Triangulation2D tri, final WB_Coord[] points, WB_Map map) {
		final int[] edges = tri.getEdges();
		for (int i = 0; i < edges.length; i += 2) {
			drawSegmentUnmapped(points[edges[i]], points[edges[i + 1]], map);
		}
	}

	/**
	 *
	 *
	 * @param tri
	 * @param points
	 */
	public void drawTriangulationEmbedded2D(final WB_Triangulation2D tri, final List<? extends WB_Coord> points,
			WB_Map2D map) {
		final int[] triangles = tri.getTriangles();
		home.beginShape(PConstants.TRIANGLES);
		for (int i = 0; i < triangles.length; i += 3) {
			vertexEmbedded2D(points.get(triangles[i]), map);
			vertexEmbedded2D(points.get(triangles[i + 1]), map);
			vertexEmbedded2D(points.get(triangles[i + 2]), map);
		}
		home.endShape();
	}

	/**
	 *
	 *
	 * @param tri
	 * @param points
	 */
	public void drawTriangulationEmbedded2D(final WB_Triangulation2D tri, WB_Coord[] points, WB_Map2D map) {
		final int[] triangles = tri.getTriangles();
		home.beginShape(PConstants.TRIANGLES);
		for (int i = 0; i < triangles.length; i += 3) {
			vertexEmbedded2D(points[triangles[i]], map);
			vertexEmbedded2D(points[triangles[i + 1]], map);
			vertexEmbedded2D(points[triangles[i + 2]], map);
		}
		home.endShape();
	}

	/**
	 *
	 *
	 * @param tri
	 * @param points
	 */
	public void drawTriangulationMapped(final WB_Triangulation2D tri, final List<? extends WB_Coord> points,
			WB_Map map) {
		final int[] triangles = tri.getTriangles();
		home.beginShape(PConstants.TRIANGLES);
		for (int i = 0; i < triangles.length; i += 3) {
			vertexMapped(points.get(triangles[i]), map);
			vertexMapped(points.get(triangles[i + 1]), map);
			vertexMapped(points.get(triangles[i + 2]), map);
		}
		home.endShape();
	}

	/**
	 *
	 *
	 * @param tri
	 * @param points
	 */
	public void drawTriangulationMapped(final WB_Triangulation2D tri, WB_Coord[] points, WB_Map map) {
		final int[] triangles = tri.getTriangles();
		home.beginShape(PConstants.TRIANGLES);
		for (int i = 0; i < triangles.length; i += 3) {
			vertexMapped(points[triangles[i]], map);
			vertexMapped(points[triangles[i + 1]], map);
			vertexMapped(points[triangles[i + 2]], map);
		}
		home.endShape();
	}

	/**
	 *
	 *
	 * @param tri
	 * @param points
	 */
	public void drawTriangulationUnmapped(final WB_Triangulation2D tri, final List<? extends WB_Coord> points,
			WB_Map map) {
		final int[] triangles = tri.getTriangles();
		home.beginShape(PConstants.TRIANGLES);
		for (int i = 0; i < triangles.length; i += 3) {
			vertexUnmapped(points.get(triangles[i]), map);
			vertexUnmapped(points.get(triangles[i + 1]), map);
			vertexUnmapped(points.get(triangles[i + 2]), map);
		}
		home.endShape();
	}

	/**
	 *
	 *
	 * @param tri
	 * @param points
	 */
	public void drawTriangulationUnmapped(final WB_Triangulation2D tri, WB_Coord[] points, WB_Map map) {
		final int[] triangles = tri.getTriangles();
		home.beginShape(PConstants.TRIANGLES);
		for (int i = 0; i < triangles.length; i += 3) {
			vertexUnmapped(points[triangles[i]], map);
			vertexUnmapped(points[triangles[i + 1]], map);
			vertexUnmapped(points[triangles[i + 2]], map);
		}
		home.endShape();
	}

	/**
	 *
	 *
	 * @param p
	 * @param v
	 * @param r
	 */

	public void drawVector(final WB_Coord p, final WB_Coord v, final double r) {
		home.pushMatrix();
		translate(p);
		home.line(0f, 0f, 0f, (float) (r * v.xd()), (float) (r * v.yd()), (float) (r * v.zd()));
		home.popMatrix();
	}

	/**
	 *
	 *
	 * @param p
	 * @param v
	 * @param r
	 */
	public void drawVectorEmbedded2D(final WB_Coord p, final WB_Coord v, final double r, WB_Map2D map) {
		drawSegmentEmbedded2D(p, WB_Point.addMul(p, r, v), map);
	}

	/**
	 *
	 *
	 * @param p
	 * @param v
	 * @param r
	 */
	public void drawVectorMapped(final WB_Coord p, final WB_Coord v, final double r, WB_Map map) {
		drawSegmentMapped(p, WB_Point.addMul(p, r, v), map);
	}

	/**
	 *
	 *
	 * @param p
	 * @param v
	 * @param r
	 */
	public void drawVectorUnmapped(final WB_Coord p, final WB_Coord v, final double r, WB_Map map) {
		drawSegmentUnmapped(p, WB_Point.addMul(p, r, v), map);
	}

	/**
	 *
	 *
	 * @param key
	 * @param d
	 * @param mesh
	 * @deprecated Use {@link #drawVertex(Long,HE_MeshStructure,double)} instead
	 */
	@Deprecated
	public void drawVertex(final Long key, final double d, final HE_MeshStructure mesh) {
		drawVertex(key, mesh, d);
	}

	/**
	 *
	 *
	 * @param key
	 * @param mesh
	 * @param d
	 */
	public void drawVertex(final Long key, final HE_MeshStructure mesh, final double d) {
		final HE_Vertex v = mesh.getVertexWithKey(key);
		if (v != null) {
			home.pushMatrix();
			translate(v);
			home.box((float) d);
			home.popMatrix();
		}
	}

	/**
	 *
	 *
	 * @param d
	 * @param mesh
	 * @deprecated Use {@link #drawVertexNormals(HE_MeshStructure,double)}
	 *             instead
	 */
	@Deprecated
	public void drawVertexNormals(final double d, final HE_MeshStructure mesh) {
		drawVertexNormals(mesh, d);
	}

	/**
	 *
	 *
	 * @param mesh
	 * @param d
	 */
	public void drawVertexNormals(final HE_MeshStructure mesh, final double d) {
		final Iterator<HE_Vertex> vItr = mesh.vItr();
		WB_Coord vn;
		HE_Vertex v;
		while (vItr.hasNext()) {
			v = vItr.next();
			vn = v.getVertexNormal();
			drawVector(v, vn, d);
		}
	}

	/**
	 *
	 *
	 * @param d
	 * @param mesh
	 * @deprecated Use {@link #drawVertices(HE_MeshStructure,double)} instead
	 */
	@Deprecated
	public void drawVertices(final double d, final HE_MeshStructure mesh) {
		drawVertices(mesh, d);
	}

	/**
	 *
	 *
	 * @param mesh
	 * @param d
	 */
	public void drawVertices(final HE_MeshStructure mesh, final double d) {
		HE_Vertex v;
		final Iterator<HE_Vertex> vItr = mesh.vItr();
		while (vItr.hasNext()) {
			v = vItr.next();
			home.pushMatrix();
			translate(v);
			home.box((float) d);
			home.popMatrix();
		}
	}

	private int getColorFromPImage(final double u, final double v, final PImage texture) {
		return texture.get(Math.max(0, Math.min((int) (u * texture.width), texture.width - 1)),
				Math.max(0, Math.min((int) (v * texture.height), texture.height - 1)));
	}

	public PGraphicsOpenGL getHome() {
		return home;
	}

	/**
	 *
	 *
	 * @param x
	 * @param y
	 * @return
	 */
	public WB_Ray getPickingRay(final double x, final double y) {
		unproject.captureViewMatrix(home);
		unproject.calculatePickPoints(x, y, home.height);
		WB_Ray ray = new WB_Ray(unproject.ptStartPos, unproject.ptEndPos);
		final WB_Coord o = ray.getOrigin();
		WB_Point e = ray.getPointOnLine(1000);
		double error = WB_GeometryOp.getSqDistance2D(x, y, home.screenX(e.xf(), e.yf(), e.zf()),
				home.screenY(e.xf(), e.yf(), e.zf()));
		while (error > 1) {
			final WB_Point ne = e.add(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5);
			final double nerror = WB_GeometryOp.getSqDistance2D(x, y, home.screenX(ne.xf(), ne.yf(), ne.zf()),
					home.screenY(ne.xf(), ne.yf(), ne.zf()));
			if (nerror < error) {
				error = nerror;
				e = ne;
			}
		}
		ray = new WB_Ray(o, e.sub(o));
		return ray;
	}

	/**
	 *
	 *
	 * @param mesh
	 * @param x
	 * @param y
	 * @return
	 */
	public HE_Face pickClosestFace(final HE_Mesh mesh, final double x, final double y) {
		final WB_Ray mouseRay3d = getPickingRay(x, y);
		final HE_FaceIntersection p = HE_Intersection.getClosestIntersection(mesh, mouseRay3d);
		return (p == null) ? null : p.face;
	}

	/**
	 *
	 *
	 * @param meshtree
	 * @param x
	 * @param y
	 * @return
	 */
	public HE_Face pickClosestFace(final WB_AABBTree meshtree, final double x, final double y) {
		final WB_Ray mouseRay3d = getPickingRay(x, y);
		final HE_FaceIntersection p = HE_Intersection.getClosestIntersection(meshtree, mouseRay3d);
		return (p == null) ? null : p.face;
	}

	/**
	 *
	 *
	 * @param mesh
	 * @param x
	 * @param y
	 * @return
	 */
	public HE_Halfedge pickEdge(final HE_Mesh mesh, final double x, final double y) {
		final WB_Ray mouseRay3d = getPickingRay(x, y);
		final HE_FaceIntersection p = HE_Intersection.getClosestIntersection(mesh, mouseRay3d);
		if (p == null) {
			return null;
		}
		final HE_Face f = p.face;
		final HE_FaceEdgeCirculator fec = new HE_FaceEdgeCirculator(f);
		HE_Halfedge trial;
		HE_Halfedge closest = null;
		double d2 = 0;
		double d2min = Double.MAX_VALUE;
		while (fec.hasNext()) {
			trial = fec.next();
			d2 = WB_GeometryOp.getDistanceToSegment3D(p.point, trial.getStartVertex().getPoint(),
					trial.getEndVertex().getPoint());
			if (d2 < d2min) {
				d2min = d2;
				closest = trial;
			}
		}
		return closest;
	}

	/**
	 *
	 *
	 * @param mesh
	 * @param x
	 * @param y
	 * @return
	 */
	public List<HE_Face> pickFaces(final HE_Mesh mesh, final double x, final double y) {
		final WB_Ray mouseRay3d = getPickingRay(x, y);
		final List<HE_FaceIntersection> p = HE_Intersection.getIntersection(mesh, mouseRay3d);
		final List<HE_Face> result = new ArrayList<HE_Face>();
		for (final HE_FaceIntersection fi : p) {
			result.add(fi.face);
		}
		return result;
	}

	/**
	 *
	 *
	 * @param meshtree
	 * @param x
	 * @param y
	 * @return
	 */
	public List<HE_Face> pickFaces(final WB_AABBTree meshtree, final double x, final double y) {
		final WB_Ray mouseRay3d = getPickingRay(x, y);
		final List<HE_FaceIntersection> p = HE_Intersection.getIntersection(meshtree, mouseRay3d);
		final List<HE_Face> result = new ArrayList<HE_Face>();
		for (final HE_FaceIntersection fi : p) {
			result.add(fi.face);
		}
		return result;
	}

	/**
	 *
	 *
	 * @param mesh
	 * @param x
	 * @param y
	 * @return
	 */
	public HE_Face pickFurthestFace(final HE_Mesh mesh, final double x, final double y) {
		final WB_Ray mouseRay3d = getPickingRay(x, y);
		final HE_FaceIntersection p = HE_Intersection.getFurthestIntersection(mesh, mouseRay3d);
		return (p == null) ? null : p.face;
	}

	/**
	 *
	 *
	 * @param meshtree
	 * @param x
	 * @param y
	 * @return
	 */
	public HE_Face pickFurthestFace(final WB_AABBTree meshtree, final double x, final double y) {
		final WB_Ray mouseRay3d = getPickingRay(x, y);
		final HE_FaceIntersection p = HE_Intersection.getFurthestIntersection(meshtree, mouseRay3d);
		return (p == null) ? null : p.face;
	}

	/**
	 *
	 *
	 * @param mesh
	 * @param x
	 * @param y
	 * @return
	 */
	public HE_Vertex pickVertex(final HE_Mesh mesh, final double x, final double y) {
		final WB_Ray mouseRay3d = getPickingRay(x, y);
		final HE_FaceIntersection p = HE_Intersection.getClosestIntersection(mesh, mouseRay3d);
		if (p == null) {
			return null;
		}
		final HE_Face f = p.face;
		final HE_FaceVertexCirculator fvc = new HE_FaceVertexCirculator(f);
		HE_Vertex trial;
		HE_Vertex closest = null;
		double d2 = 0;
		double d2min = Double.MAX_VALUE;
		while (fvc.hasNext()) {
			trial = fvc.next();
			d2 = trial.getPoint().getSqDistance3D(p.point);
			if (d2 < d2min) {
				d2min = d2;
				closest = trial;
			}
		}
		return closest;
	}

	public void setFaceColorFromTexture(final HE_Mesh mesh, final PImage texture) {
		final HE_FaceIterator fitr = mesh.fItr();
		HE_Face f;
		HE_Vertex v;
		HE_TextureCoordinate uvw;
		while (fitr.hasNext()) {
			f = fitr.next();
			final HE_FaceVertexCirculator fvc = new HE_FaceVertexCirculator(f);
			final WB_Point p = new WB_Point();
			int id = 0;
			while (fvc.hasNext()) {
				v = fvc.next();
				uvw = v.getUVW(f);
				p.addSelf(uvw.ud(), uvw.vd(), 0);
				id++;
			}
			p.divSelf(id);
			f.setColor(getColorFromPImage(p.xd(), p.yd(), texture));
		}
	}

	public void setHalfedgeColorFromTexture(final HE_Mesh mesh, final PImage texture) {
		final HE_FaceIterator fitr = mesh.fItr();
		HE_Face f;
		HE_Halfedge he;
		HE_TextureCoordinate p;
		while (fitr.hasNext()) {
			f = fitr.next();
			final HE_FaceHalfedgeInnerCirculator fhec = new HE_FaceHalfedgeInnerCirculator(f);
			while (fhec.hasNext()) {
				he = fhec.next();
				p = he.getVertex().getUVW(f);
				he.setColor(getColorFromPImage(p.ud(), p.vd(), texture));
			}
		}
	}

	public void setVertexColorFromTexture(final HE_Mesh mesh, final PImage texture) {
		final HE_VertexIterator vitr = mesh.vItr();
		HE_Vertex v;
		HE_TextureCoordinate p;
		while (vitr.hasNext()) {
			v = vitr.next();
			p = v.getVertexUVW();
			v.setColor(getColorFromPImage(p.ud(), p.vd(), texture));
		}
	}

	/**
	 *
	 *
	 * @param mesh
	 * @return
	 */
	public PShape toFacetedPShape(final HE_Mesh mesh) {
		final PShape retained = home.createShape();
		retained.beginShape(PConstants.TRIANGLES);
		final HE_Mesh lmesh = mesh.get();
		lmesh.triangulate();
		final Iterator<HE_Face> fItr = lmesh.fItr();
		HE_Face f;
		HE_Vertex v;
		HE_Halfedge he;
		while (fItr.hasNext()) {
			f = fItr.next();
			he = f.getHalfedge();
			do {
				v = he.getVertex();
				retained.vertex(v.xf(), v.yf(), v.zf());
				he = he.getNextInFace();
			} while (he != f.getHalfedge());
		}
		retained.endShape();
		return retained;
	}

	public PShape toFacetedPShape(final HE_MeshStructure mesh, final double offset) {
		tracker.setStatus(this, "Creating PShape.", 1);
		final PShape retained = home.createShape();
		retained.beginShape(PConstants.TRIANGLES);
		WB_ProgressCounter counter = new WB_ProgressCounter(mesh.getNumberOfFaces(), 10);
		tracker.setStatus(this, "Writing faces.", counter);
		final Iterator<HE_Face> fItr = mesh.fItr();
		HE_Face f;
		List<HE_Vertex> vertices;
		HE_Vertex v;
		WB_Coord fn;
		final float df = (float) offset;
		while (fItr.hasNext()) {
			f = fItr.next();
			vertices = f.getFaceVertices();
			if (vertices.size() > 2) {
				final int[] tris = f.getTriangles();
				for (int i = 0; i < tris.length; i += 3) {
					v = vertices.get(tris[i]);
					fn = v.getVertexNormal();
					retained.vertex(v.xf() + df * fn.xf(), v.yf() + df * fn.yf(), v.zf() + df * fn.zf());
					v = vertices.get(tris[i + 1]);
					fn = v.getVertexNormal();
					retained.vertex(v.xf() + df * fn.xf(), v.yf() + df * fn.yf(), v.zf() + df * fn.zf());
					v = vertices.get(tris[i + 2]);
					fn = v.getVertexNormal();
					retained.vertex(v.xf() + df * fn.xf(), v.yf() + df * fn.yf(), v.zf() + df * fn.zf());
				}
			}
			counter.increment();
		}
		retained.endShape();
		tracker.setStatus(this, "Pshape created.", -1);
		return retained;
	}

	/**
	 *
	 *
	 * @param mesh
	 * @return
	 */
	public PShape toFacetedPShape(final WB_FaceListMesh mesh) {
		final PShape retained = home.createShape();
		retained.beginShape(PConstants.TRIANGLES);
		final WB_FaceListMesh lmesh = geometryfactory.createTriMesh(mesh);
		final List<WB_Point> seq = lmesh.getPoints();
		WB_Point p = seq.get(0);
		for (int i = 0; i < lmesh.getNumberOfFaces(); i++) {
			int id = lmesh.getFace(i)[0];
			p = seq.get(id);
			retained.vertex(p.xf(), p.yf(), p.zf());
			id = lmesh.getFace(i)[1];
			p = seq.get(id);
			retained.vertex(p.xf(), p.yf(), p.zf());
			id = lmesh.getFace(i)[2];
			p = seq.get(id);
			;
			retained.vertex(p.xf(), p.yf(), p.zf());
		}
		retained.endShape();
		return retained;
	}

	/**
	 *
	 *
	 * @param mesh
	 * @return
	 */
	public PShape toFacetedPShapeWithFaceColor(final HE_Mesh mesh) {
		final PShape retained = home.createShape();
		retained.beginShape(PConstants.TRIANGLES);
		final HE_Mesh lmesh = mesh.get();
		lmesh.triangulate();
		final Iterator<HE_Face> fItr = lmesh.fItr();
		HE_Face f;
		HE_Vertex v;
		HE_Halfedge he;
		while (fItr.hasNext()) {
			f = fItr.next();
			he = f.getHalfedge();
			retained.fill(f.getColor());
			do {
				v = he.getVertex();
				retained.vertex(v.xf(), v.yf(), v.zf());
				he = he.getNextInFace();
			} while (he != f.getHalfedge());
		}
		retained.endShape();
		return retained;
	}

	/**
	 *
	 *
	 * @param mesh
	 * @return
	 */
	public PShape toFacetedPShapeWithVertexColor(final HE_Mesh mesh) {
		final PShape retained = home.createShape();
		retained.beginShape(PConstants.TRIANGLES);
		final HE_Mesh lmesh = mesh.get();
		lmesh.triangulate();
		final Iterator<HE_Face> fItr = lmesh.fItr();
		HE_Face f;
		HE_Vertex v;
		HE_Halfedge he;
		while (fItr.hasNext()) {
			f = fItr.next();
			he = f.getHalfedge();
			do {
				v = he.getVertex();
				retained.fill(v.getColor());
				retained.vertex(v.xf(), v.yf(), v.zf());
				he = he.getNextInFace();
			} while (he != f.getHalfedge());
		}
		retained.endShape();
		return retained;
	}

	/**
	 *
	 *
	 * @param mesh
	 * @return
	 */
	public PShape toSmoothPShape(final HE_Mesh mesh) {
		final PShape retained = home.createShape();
		retained.beginShape(PConstants.TRIANGLES);
		final HE_Mesh lmesh = mesh.get();
		lmesh.triangulate();
		WB_Coord n = new WB_Vector();
		final Iterator<HE_Face> fItr = lmesh.fItr();
		HE_Face f;
		HE_Vertex v;
		HE_Halfedge he;
		while (fItr.hasNext()) {
			f = fItr.next();
			he = f.getHalfedge();
			do {
				v = he.getVertex();
				n = v.getVertexNormal();
				retained.normal(n.xf(), n.yf(), n.zf());
				retained.vertex(v.xf(), v.yf(), v.zf());
				he = he.getNextInFace();
			} while (he != f.getHalfedge());
		}
		retained.endShape();
		return retained;
	}

	/**
	 *
	 *
	 * @param mesh
	 * @return
	 */
	public PShape toSmoothPShape(final WB_FaceListMesh mesh) {
		final PShape retained = home.createShape();
		retained.beginShape(PConstants.TRIANGLES);
		final WB_FaceListMesh lmesh = geometryfactory.createTriMesh(mesh);
		final WB_Vector v = geometryfactory.createVector();
		final List<WB_Point> seq = lmesh.getPoints();
		WB_Point p = seq.get(0);
		for (int i = 0; i < lmesh.getNumberOfFaces(); i++) {
			int id = lmesh.getFace(i)[0];
			v.set(lmesh.getVertexNormal(id));
			retained.normal(v.xf(), v.yf(), v.zf());
			p = seq.get(id);
			retained.vertex(p.xf(), p.yf(), p.zf());
			id = lmesh.getFace(i)[1];
			v.set(lmesh.getVertexNormal(id));
			retained.normal(v.xf(), v.yf(), v.zf());
			p = seq.get(id);
			retained.vertex(p.xf(), p.yf(), p.zf());
			id = lmesh.getFace(i)[2];
			v.set(lmesh.getVertexNormal(id));
			retained.normal(v.xf(), v.yf(), v.zf());
			p = seq.get(id);
			retained.vertex(p.xf(), p.yf(), p.zf());
		}
		retained.endShape();
		return retained;
	}

	/**
	 *
	 *
	 * @param mesh
	 * @return
	 */
	public PShape toSmoothPShapeWithFaceColor(final HE_Mesh mesh) {
		final PShape retained = home.createShape();
		retained.beginShape(PConstants.TRIANGLES);
		final HE_Mesh lmesh = mesh.get();
		lmesh.triangulate();
		WB_Coord n = new WB_Vector();
		final Iterator<HE_Face> fItr = lmesh.fItr();
		HE_Face f;
		HE_Vertex v;
		HE_Halfedge he;
		while (fItr.hasNext()) {
			f = fItr.next();
			retained.fill(f.getColor());
			he = f.getHalfedge();
			do {
				v = he.getVertex();
				n = v.getVertexNormal();
				retained.normal(n.xf(), n.yf(), n.zf());
				retained.vertex(v.xf(), v.yf(), v.zf());
				he = he.getNextInFace();
			} while (he != f.getHalfedge());
		}
		retained.endShape();
		return retained;
	}

	/**
	 *
	 *
	 * @param mesh
	 * @return
	 */
	public PShape toSmoothPShapeWithVertexColor(final HE_Mesh mesh) {
		final PShape retained = home.createShape();
		retained.beginShape(PConstants.TRIANGLES);
		final HE_Mesh lmesh = mesh.get();
		lmesh.triangulate();
		WB_Coord n = new WB_Vector();
		final Iterator<HE_Face> fItr = lmesh.fItr();
		HE_Face f;
		HE_Vertex v;
		HE_Halfedge he;
		while (fItr.hasNext()) {
			f = fItr.next();
			he = f.getHalfedge();
			do {
				v = he.getVertex();
				retained.fill(v.getColor());
				n = v.getVertexNormal();
				retained.normal(n.xf(), n.yf(), n.zf());
				retained.vertex(v.xf(), v.yf(), v.zf());
				he = he.getNextInFace();
			} while (he != f.getHalfedge());
		}
		retained.endShape();
		return retained;
	}

	/**
	 *
	 *
	 * @param mesh
	 * @return
	 */
	public PShape toWireframePShape(final HE_MeshStructure mesh) {
		// tracker.setDefaultStatus(this,"Creating
		// PShape.");
		final PShape retained = home.createShape();
		if (mesh instanceof HE_Selection) {
			((HE_Selection) mesh).collectEdgesByFace();
		}
		// tracker.setDefaultStatus(this,"Writing Edges.",
		// mesh.getNumberOfEdges());
		final HE_EdgeIterator eItr = mesh.eItr();
		HE_Halfedge e;
		HE_Vertex v;
		retained.beginShape(PConstants.LINES);
		while (eItr.hasNext()) {
			e = eItr.next();
			v = e.getVertex();
			retained.vertex(v.xf(), v.yf(), v.zf());
			v = e.getEndVertex();
			retained.vertex(v.xf(), v.yf(), v.zf());
			// tracker.incrementCounter();
		}
		retained.endShape();
		// tracker.setDefaultStatus(this,"Pshape
		// created.");
		return retained;
	}

	public void translate(final WB_Coord p) {
		home.translate(p.xf(), p.yf(), p.zf());
	}

	public void vertex(final WB_Coord p) {
		home.vertex(p.xf(), p.yf(), p.zf());
	}

	public void normal(final WB_Coord n) {
		home.normal(n.xf(), n.yf(), n.zf());
	}

	public void vertexEmbedded2D(WB_Coord p, WB_Map2D map) {
		WB_Point q = new WB_Point();
		map.mapPoint3D(p, q);
		map.unmapPoint2D(q, q);
		vertex(q);
	}

	public void vertexMapped(WB_Coord p, WB_Map map) {
		WB_Point q = new WB_Point();
		map.mapPoint3D(p, q);
		vertex(q);
	}

	public void vertexUnmapped(WB_Coord p, WB_Map map) {
		WB_Point q = new WB_Point();
		map.mapPoint3D(p, q);
		vertex(q);
	}

	private void line(WB_Coord p, WB_Coord q) {
		home.beginShape(PConstants.LINES);
		vertex(p);
		vertex(q);
		home.endShape();
	}

}