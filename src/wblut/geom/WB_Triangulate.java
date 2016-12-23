/*
 * This file is part of HE_Mesh, a library for creating and manipulating meshes.
 * It is dedicated to the public domain. To the extent possible under law,
 * I , Frederik Vanhoutte, have waived all copyright and related or neighboring
 * rights.
 *
 * This work is published from Belgium. (http://creativecommons.org/publicdomain/zero/1.0/)
 *
 */
package wblut.geom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.triangulate.ConformingDelaunayTriangulationBuilder;
import com.vividsolutions.jts.triangulate.DelaunayTriangulationBuilder;
import com.vividsolutions.jts.triangulate.quadedge.QuadEdgeSubdivision;

import javolution.util.FastMap;
import javolution.util.FastTable;
import wblut.external.Delaunay.WB_Delaunay;
import wblut.external.ProGAL.CEdge;
import wblut.external.ProGAL.CTetrahedron;
import wblut.external.ProGAL.CTriangle;
import wblut.external.ProGAL.CVertex;
import wblut.external.ProGAL.DelaunayComplex;
import wblut.external.ProGAL.Point;
import wblut.external.constrainedDelaunay.WB_ConstrainedTriangulation;
import wblut.math.WB_Epsilon;

/**
 *
 */
public class WB_Triangulate {
	/**
	 *
	 */
	private static WB_GeometryFactory geometryfactory = new WB_GeometryFactory();

	/**
	 *
	 */
	public WB_Triangulate() {
	}

	/**
	 *
	 *
	 * @param points
	 * @param closest
	 * @return
	 */
	public static WB_Triangulation3D triangulate3D(final WB_Coord[] points, final double closest) {
		final WB_Triangulation3D result = new WB_Triangulation3D(WB_Delaunay.getTriangulation3D(points, closest).Tri);
		return result;
	}

	/**
	 *
	 *
	 * @param points
	 * @param closest
	 * @return
	 */
	public static WB_Triangulation3D triangulate3D(final Collection<? extends WB_Coord> points, final double closest) {
		final WB_Triangulation3D result = new WB_Triangulation3D(WB_Delaunay.getTriangulation3D(points, closest).Tri);
		return result;
	}

	/**
	 *
	 *
	 * @param points
	 * @return
	 */
	public static WB_Triangulation3D triangulate3D(final WB_Coord[] points) {
		// WB_Predicates predicates = new WB_Predicates();
		final int n = points.length;
		final List<Point> tmppoints = new ArrayList<Point>(n);
		final WB_KDTree<WB_Coord, Integer> tree = new WB_KDTree<WB_Coord, Integer>();
		for (int i = 0; i < n; i++) {
			tmppoints.add(new Point(points[i].xd(), points[i].yd(), points[i].zd()));
			tree.add(points[i], i);
		}
		final DelaunayComplex dc = new DelaunayComplex(tmppoints);
		final List<CTetrahedron> tetras = dc.getTetrahedra();
		final List<CTriangle> tris = dc.getTriangles();
		final List<CEdge> edges = dc.getEdges();
		int nt = tetras.size();
		List<int[]> tmpresult = new ArrayList<int[]>();
		for (int i = 0; i < nt; i++) {
			final int[] tmp = new int[4];
			final CTetrahedron tetra = tetras.get(i);
			int index = tree.getNearestNeighbor(convert(tetra.getPoint(0))).value;
			tmp[0] = index;
			index = tree.getNearestNeighbor(convert(tetra.getPoint(1))).value;
			tmp[1] = index;
			index = tree.getNearestNeighbor(convert(tetra.getPoint(2))).value;
			tmp[2] = index;
			index = tree.getNearestNeighbor(convert(tetra.getPoint(3))).value;
			tmp[3] = index;
			/*
			 * double o = predicates.orientTetra(points[tmp[0]].coords(),
			 * points[tmp[1]].coords(), points[tmp[2]].coords(),
			 * points[tmp[3]].coords()); if (o != 0) {
			 */
			tmpresult.add(tmp);
			/*
			 * }
			 */
		}
		final int[] tetra = new int[4 * tmpresult.size()];
		for (int i = 0; i < tmpresult.size(); i++) {
			for (int j = 0; j < 4; j++) {
				tetra[i * 4 + j] = tmpresult.get(i)[j];
			}
		}
		nt = tris.size();
		tmpresult = new ArrayList<int[]>();
		for (int i = 0; i < nt; i++) {
			final int[] tmp = new int[3];
			final CTriangle tri = tris.get(i);
			int index = tree.getNearestNeighbor(convert(tri.getPoint(0))).value;
			tmp[0] = index;
			index = tree.getNearestNeighbor(convert(tri.getPoint(1))).value;
			tmp[1] = index;
			index = tree.getNearestNeighbor(convert(tri.getPoint(2))).value;
			tmp[2] = index;
			/*
			 * double o = predicates.orientTetra(points[tmp[0]].coords(),
			 * points[tmp[1]].coords(), points[tmp[2]].coords(),
			 * points[tmp[3]].coords()); if (o != 0) {
			 */
			tmpresult.add(tmp);
			/*
			 * }
			 */
		}
		final int[] tri = new int[3 * tmpresult.size()];
		for (int i = 0; i < tmpresult.size(); i++) {
			for (int j = 0; j < 3; j++) {
				tri[3 * i + j] = tmpresult.get(i)[j];
			}
		}
		nt = edges.size();
		tmpresult = new ArrayList<int[]>();
		for (int i = 0; i < nt; i++) {
			final int[] tmp = new int[3];
			final CEdge edge = edges.get(i);
			int index = tree.getNearestNeighbor(convert(edge.getPoint(0))).value;
			tmp[0] = index;
			index = tree.getNearestNeighbor(convert(edge.getPoint(1))).value;
			tmp[1] = index;
			/*
			 * double o = predicates.orientTetra(points[tmp[0]].coords(),
			 * points[tmp[1]].coords(), points[tmp[2]].coords(),
			 * points[tmp[3]].coords()); if (o != 0) {
			 */
			tmpresult.add(tmp);
			/*
			 * }
			 */
		}
		final int[] edge = new int[2 * tmpresult.size()];
		for (int i = 0; i < tmpresult.size(); i++) {
			for (int j = 0; j < 2; j++) {
				edge[2 * i + j] = tmpresult.get(i)[j];
			}
		}
		final List<WB_Coord> pts = new FastTable<WB_Coord>();
		for (final WB_Coord p : points) {
			pts.add(p);
		}
		final WB_Triangulation3D result = new WB_Triangulation3D(tetra, tri, edge);
		return result;
	}

	/**
	 *
	 *
	 * @param points
	 * @return
	 */
	public static WB_Triangulation3D triangulate3D(final Collection<? extends WB_Coord> points) {
		final int n = points.size();
		final List<Point> tmppoints = new ArrayList<Point>(n);
		final WB_KDTree<WB_Coord, Integer> tree = new WB_KDTree<WB_Coord, Integer>();
		int i = 0;
		for (final WB_Coord p : points) {
			tmppoints.add(new Point(p.xd(), p.yd(), p.zd()));
			tree.add(p, i);
			i++;
		}
		final DelaunayComplex dc = new DelaunayComplex(tmppoints);
		final List<CTetrahedron> tetras = dc.getTetrahedra();
		final List<CTriangle> tris = dc.getTriangles();
		final List<CEdge> edges = dc.getEdges();
		int nt = tetras.size();
		List<int[]> tmpresult = new ArrayList<int[]>();
		for (i = 0; i < nt; i++) {
			final int[] tmp = new int[4];
			final CTetrahedron tetra = tetras.get(i);
			int index = tree.getNearestNeighbor(convert(tetra.getPoint(0))).value;
			tmp[0] = index;
			index = tree.getNearestNeighbor(convert(tetra.getPoint(1))).value;
			tmp[1] = index;
			index = tree.getNearestNeighbor(convert(tetra.getPoint(2))).value;
			tmp[2] = index;
			index = tree.getNearestNeighbor(convert(tetra.getPoint(3))).value;
			tmp[3] = index;
			/*
			 * double o = predicates.orientTetra(points[tmp[0]].coords(),
			 * points[tmp[1]].coords(), points[tmp[2]].coords(),
			 * points[tmp[3]].coords()); if (o != 0) {
			 */
			tmpresult.add(tmp);
			/*
			 * }
			 */
		}
		final int[] tetra = new int[4 * tmpresult.size()];
		for (i = 0; i < tmpresult.size(); i += 4) {
			for (int j = 0; j < 4; j++) {
				tetra[4 * i + j] = tmpresult.get(i)[j];
			}
		}
		nt = tris.size();
		tmpresult = new ArrayList<int[]>();
		for (i = 0; i < nt; i++) {
			final int[] tmp = new int[3];
			final CTriangle tri = tris.get(i);
			int index = tree.getNearestNeighbor(convert(tri.getPoint(0))).value;
			tmp[0] = index;
			index = tree.getNearestNeighbor(convert(tri.getPoint(1))).value;
			tmp[1] = index;
			index = tree.getNearestNeighbor(convert(tri.getPoint(2))).value;
			tmp[2] = index;
			/*
			 * double o = predicates.orientTetra(points[tmp[0]].coords(),
			 * points[tmp[1]].coords(), points[tmp[2]].coords(),
			 * points[tmp[3]].coords()); if (o != 0) {
			 */
			tmpresult.add(tmp);
			/*
			 * }
			 */
		}
		final int[] tri = new int[3 * tmpresult.size()];
		for (i = 0; i < tmpresult.size(); i++) {
			for (int j = 0; j < 3; j++) {
				tri[3 * i + j] = tmpresult.get(i)[j];
			}
		}
		nt = edges.size();
		tmpresult = new ArrayList<int[]>();
		for (i = 0; i < nt; i++) {
			final int[] tmp = new int[3];
			final CEdge edge = edges.get(i);
			int index = tree.getNearestNeighbor(convert(edge.getPoint(0))).value;
			tmp[0] = index;
			index = tree.getNearestNeighbor(convert(edge.getPoint(1))).value;
			tmp[1] = index;
			/*
			 * double o = predicates.orientTetra(points[tmp[0]].coords(),
			 * points[tmp[1]].coords(), points[tmp[2]].coords(),
			 * points[tmp[3]].coords()); if (o != 0) {
			 */
			tmpresult.add(tmp);
			/*
			 * }
			 */
		}
		final int[] edge = new int[2 * tmpresult.size()];
		for (i = 0; i < tmpresult.size(); i++) {
			for (int j = 0; j < 2; j++) {
				edge[2 * i + j] = tmpresult.get(i)[j];
			}
		}
		final WB_Triangulation3D result = new WB_Triangulation3D(tetra, tri, edge);
		return result;
	}

	/**
	 * Planar Delaunay triangulation.
	 *
	 * @param points
	 * @return
	 */
	public static WB_Triangulation2D triangulate2D(final WB_Coord[] points) {
		final int n = points.length;
		final List<Coordinate> coords = new FastTable<Coordinate>();
		for (int i = 0; i < n; i++) {
			coords.add(new Coordinate(points[i].xd(), points[i].yd(), i));
		}
		final WB_Triangulation2D result = getTriangles2D(coords);
		return result;
	}

	/**
	 *
	 *
	 * @param points
	 * @return
	 */
	public static WB_Triangulation2D triangulate2D(final Collection<? extends WB_Coord> points) {
		final int n = points.size();
		final ArrayList<Coordinate> coords = new ArrayList<Coordinate>(n);
		int id = 0;
		for (final WB_Coord p : points) {
			coords.add(new Coordinate(p.xd(), p.yd(), id));
			id++;
		}
		final WB_Triangulation2D result = getTriangles2D(coords);
		return result;
	}

	/**
	 *
	 *
	 * @param points
	 * @param context
	 * @return
	 */
	public static WB_Triangulation2D triangulate2D(final WB_Coord[] points, final WB_Map2D context) {
		final int n = points.length;
		final ArrayList<Coordinate> coords = new ArrayList<Coordinate>(n);
		final WB_Point tmp = geometryfactory.createPoint();
		for (int i = 0; i < n; i++) {
			context.mapPoint3D(points[i], tmp);
			coords.add(new Coordinate(tmp.xd(), tmp.yd(), i));
		}
		final WB_Triangulation2D result = getTriangles2D(coords);
		return result;
	}

	/**
	 *
	 *
	 * @param points
	 * @param context
	 * @return
	 */
	public static WB_Triangulation2D triangulate2D(final Collection<? extends WB_Coord> points,
			final WB_Map2D context) {
		final List<Coordinate> coords = new FastTable<Coordinate>();
		int id = 0;
		final WB_Point tmp = geometryfactory.createPoint();
		for (final WB_Coord p : points) {
			context.mapPoint3D(p, tmp);
			coords.add(new Coordinate(tmp.xd(), tmp.yd(), id));
			id++;
		}
		final WB_Triangulation2D result = getTriangles2D(coords);
		return result;
	}

	/**
	 *
	 *
	 * @param coords
	 * @return
	 */
	private static WB_Triangulation2D getTriangles2D(final List<Coordinate> coords) {
		final DelaunayTriangulationBuilder dtb = new DelaunayTriangulationBuilder();
		dtb.setSites(coords);
		final QuadEdgeSubdivision qesd = dtb.getSubdivision();
		final GeometryCollection tris = (GeometryCollection) qesd.getTriangles(new GeometryFactory());
		final int ntris = tris.getNumGeometries();
		List<int[]> result = new FastTable<int[]>();
		for (int i = 0; i < ntris; i++) {
			final Polygon tri = (Polygon) tris.getGeometryN(i);
			final Coordinate[] tricoord = tri.getCoordinates();
			final int[] triind = new int[3];
			for (int j = 0; j < 3; j++) {
				triind[j] = (int) tricoord[j].z;
			}
			result.add(triind);
		}
		final int[] T = new int[3 * result.size()];
		for (int i = 0; i < result.size(); i++) {
			T[3 * i] = result.get(i)[0];
			T[3 * i + 1] = result.get(i)[1];
			T[3 * i + 2] = result.get(i)[2];
		}
		final MultiLineString edges = (MultiLineString) qesd.getEdges(new GeometryFactory());
		final int nedges = edges.getNumGeometries();
		result = new FastTable<int[]>();
		for (int i = 0; i < nedges; i++) {
			final LineString edge = (LineString) edges.getGeometryN(i);
			final Coordinate[] edgecoord = edge.getCoordinates();
			final int[] edgeind = new int[2];
			for (int j = 0; j < 2; j++) {
				edgeind[j] = (int) edgecoord[j].z;
			}
			result.add(edgeind);
		}
		final int[] E = new int[2 * result.size()];
		for (int i = 0; i < result.size(); i++) {
			E[2 * i] = result.get(i)[0];
			E[2 * i + 1] = result.get(i)[1];
		}
		return new WB_Triangulation2D(T, E);
	}

	/**
	 *
	 *
	 * @param points
	 * @return
	 */
	public static WB_Triangulation2DWithPoints triangulateConforming2D(final WB_Coord[] points) {
		final int[] constraints = new int[2 * points.length];
		for (int i = 0, j = points.length - 1; i < points.length; j = i++) {
			constraints[2 * i] = j;
			constraints[2 * i + 1] = i;
		}
		final int n = points.length;
		final Coordinate[] coords = new Coordinate[n];
		for (int i = 0; i < n; i++) {
			coords[i] = new Coordinate(points[i].xd(), points[i].yd(), i);
		}
		return getConformingTriangles2D(coords, constraints, WB_Epsilon.EPSILON);
	}

	/**
	 *
	 *
	 * @param points
	 * @param tol
	 * @return
	 */
	public static WB_Triangulation2DWithPoints triangulateConforming2D(final WB_Coord[] points, final double tol) {
		final int[] constraints = new int[2 * points.length];
		for (int i = 0, j = points.length - 1; i < points.length; j = i++) {
			constraints[2 * i] = j;
			constraints[2 * i + 1] = i;
		}
		final int n = points.length;
		final Coordinate[] coords = new Coordinate[n];
		for (int i = 0; i < n; i++) {
			coords[i] = new Coordinate(points[i].xd(), points[i].yd(), i);
		}
		return getConformingTriangles2D(coords, constraints, tol);
	}

	/**
	 *
	 *
	 * @param points
	 * @param constraints
	 * @return
	 */
	public static WB_Triangulation2DWithPoints triangulateConforming2D(final WB_Coord[] points,
			final int[] constraints) {
		if (constraints == null) {
			return new WB_Triangulation2DWithPoints(triangulate2D(points));
		}
		final int m = constraints.length;
		if (m == 0 || m % 2 == 1) {
			return new WB_Triangulation2DWithPoints(triangulate2D(points));
		}
		final int n = points.length;
		final Coordinate[] coords = new Coordinate[n];
		for (int i = 0; i < n; i++) {
			coords[i] = new Coordinate(points[i].xd(), points[i].yd(), i);

		}
		return getConformingTriangles2D(coords, constraints, WB_Epsilon.EPSILON);
	}

	/**
	 *
	 *
	 * @param points
	 * @param constraints
	 * @param tol
	 * @return
	 */
	public static WB_Triangulation2DWithPoints triangulateConforming2D(final WB_Coord[] points, final int[] constraints,
			final double tol) {
		if (constraints == null) {
			return new WB_Triangulation2DWithPoints(triangulate2D(points));
		}
		final int m = constraints.length;
		if (m == 0 || m % 2 == 1) {
			return new WB_Triangulation2DWithPoints(triangulate2D(points));
		}
		final int n = points.length;
		final Coordinate[] coords = new Coordinate[n];
		for (int i = 0; i < n; i++) {
			coords[i] = new Coordinate(points[i].xd(), points[i].yd(), i);
		}
		return getConformingTriangles2D(coords, constraints, tol);
	}

	/**
	 *
	 *
	 * @param points
	 * @param context
	 * @return
	 */
	public static WB_Triangulation2DWithPoints triangulateConforming2D(final WB_Coord[] points,
			final WB_Map2D context) {
		final int[] constraints = new int[2 * points.length];
		for (int i = 0, j = points.length - 1; i < points.length; j = i++) {
			constraints[2 * i] = j;
			constraints[2 * i + 1] = i;
		}
		final int n = points.length;
		final Coordinate[] coords = new Coordinate[n];
		final WB_Point point = geometryfactory.createPoint();
		for (int i = 0; i < n; i++) {
			context.mapPoint3D(points[i], point);
			coords[i] = new Coordinate(point.xd(), point.yd(), i);
		}
		return getConformingTriangles2D(coords, constraints, WB_Epsilon.EPSILON);
	}

	/**
	 *
	 *
	 * @param points
	 * @param tol
	 * @param context
	 * @return
	 */
	public static WB_Triangulation2DWithPoints triangulateConforming2D(final WB_Coord[] points, final double tol,
			final WB_Map2D context) {
		final int[] constraints = new int[2 * points.length];
		for (int i = 0, j = points.length - 1; i < points.length; j = i++) {
			constraints[2 * i] = j;
			constraints[2 * i + 1] = i;
		}
		final int n = points.length;
		final Coordinate[] coords = new Coordinate[n];
		final WB_Point point = geometryfactory.createPoint();
		for (int i = 0; i < n; i++) {
			context.mapPoint3D(points[i], point);
			coords[i] = new Coordinate(point.xd(), point.yd(), i);
		}
		return getConformingTriangles2D(coords, constraints, tol);
	}

	/**
	 *
	 *
	 * @param points
	 * @param constraints
	 * @param context
	 * @return
	 */
	public static WB_Triangulation2DWithPoints triangulateConforming2D(final WB_Coord[] points, final int[] constraints,
			final WB_Map2D context) {
		if (constraints == null) {
			return new WB_Triangulation2DWithPoints(triangulate2D(points, context));
		}
		final int m = constraints.length;
		if (m == 0 || m % 2 == 1) {
			return new WB_Triangulation2DWithPoints(triangulate2D(points, context));
		}
		final int n = points.length;
		final Coordinate[] coords = new Coordinate[n];
		final WB_Point point = geometryfactory.createPoint();
		for (int i = 0; i < n; i++) {
			context.mapPoint3D(points[i], point);
			coords[i] = new Coordinate(point.xd(), point.yd(), i);
		}
		return getConformingTriangles2D(coords, constraints, WB_Epsilon.EPSILON);
	}

	/**
	 *
	 *
	 * @param points
	 * @param constraints
	 * @param tol
	 * @param context
	 * @return
	 */
	public static WB_Triangulation2DWithPoints triangulateConforming2D(final WB_Coord[] points, final int[] constraints,
			final double tol, final WB_Map2D context) {
		if (constraints == null) {
			return new WB_Triangulation2DWithPoints(triangulate2D(points, context));
		}
		final int m = constraints.length;
		if (m == 0 || m % 2 == 1) {
			return new WB_Triangulation2DWithPoints(triangulate2D(points, context));
		}
		final int n = points.length;
		final Coordinate[] coords = new Coordinate[n];
		final WB_Point point = geometryfactory.createPoint();
		for (int i = 0; i < n; i++) {
			context.mapPoint3D(points[i], point);
			coords[i] = new Coordinate(point.xd(), point.yd(), i);
		}
		return getConformingTriangles2D(coords, constraints, tol);
	}

	/**
	 *
	 *
	 * @param points
	 * @return
	 */
	public static WB_Triangulation2DWithPoints triangulateConforming2D(final Collection<? extends WB_Coord> points) {
		int n = points.size();
		final int[] constraints = new int[2 * n];
		for (int i = 0, j = n - 1; i < n; j = i++) {
			constraints[2 * i] = j;
			constraints[2 * i + 1] = i;
		}

		final Coordinate[] coords = new Coordinate[n];
		int i = 0;
		for (WB_Coord p : points) {
			coords[i] = new Coordinate(p.xd(), p.yd(), i);
			i++;
		}
		return getConformingTriangles2D(coords, constraints, WB_Epsilon.EPSILON);
	}

	/**
	 *
	 *
	 * @param points
	 * @param tol
	 * @return
	 */
	public static WB_Triangulation2DWithPoints triangulateConforming2D(final Collection<? extends WB_Coord> points,
			final double tol) {
		int n = points.size();
		final int[] constraints = new int[2 * n];
		for (int i = 0, j = n - 1; i < n; j = i++) {
			constraints[2 * i] = j;
			constraints[2 * i + 1] = i;
		}

		final Coordinate[] coords = new Coordinate[n];
		int i = 0;
		for (WB_Coord p : points) {
			coords[i] = new Coordinate(p.xd(), p.yd(), i);
			i++;
		}
		return getConformingTriangles2D(coords, constraints, tol);
	}

	/**
	 *
	 *
	 * @param points
	 * @param constraints
	 * @return
	 */
	public static WB_Triangulation2DWithPoints triangulateConforming2D(final Collection<? extends WB_Coord> points,
			final int[] constraints) {
		if (constraints == null) {
			return new WB_Triangulation2DWithPoints(triangulate2D(points));
		}
		final int m = constraints.length;
		if (m == 0 || m % 2 == 1) {
			return new WB_Triangulation2DWithPoints(triangulate2D(points));
		}
		final int n = points.size();
		final Coordinate[] coords = new Coordinate[n];
		int i = 0;
		for (WB_Coord p : points) {
			coords[i] = new Coordinate(p.xd(), p.yd(), i);
			i++;
		}
		return getConformingTriangles2D(coords, constraints, WB_Epsilon.EPSILON);
	}

	/**
	 *
	 *
	 * @param points
	 * @param constraints
	 * @param tol
	 * @return
	 */
	public static WB_Triangulation2DWithPoints triangulateConforming2D(final Collection<? extends WB_Coord> points,
			final int[] constraints, final double tol) {
		if (constraints == null) {
			return new WB_Triangulation2DWithPoints(triangulate2D(points));
		}
		final int m = constraints.length;
		if (m == 0 || m % 2 == 1) {
			return new WB_Triangulation2DWithPoints(triangulate2D(points));
		}
		final int n = points.size();
		final Coordinate[] coords = new Coordinate[n];
		int i = 0;
		for (WB_Coord p : points) {
			coords[i] = new Coordinate(p.xd(), p.yd(), i);
			i++;
		}

		return getConformingTriangles2D(coords, constraints, tol);
	}

	/**
	 *
	 *
	 * @param points
	 * @param context
	 * @return
	 */
	public static WB_Triangulation2DWithPoints triangulateConforming2D(final Collection<? extends WB_Coord> points,
			final WB_Map2D context) {
		final int n = points.size();
		final int[] constraints = new int[2 * n];
		for (int i = 0, j = n - 1; i < n; j = i++) {
			constraints[2 * i] = j;
			constraints[2 * i + 1] = i;
		}

		final Coordinate[] coords = new Coordinate[n];
		final WB_Point point = geometryfactory.createPoint();
		int i = 0;
		for (WB_Coord p : points) {
			context.mapPoint3D(p, point);
			coords[i] = new Coordinate(point.xd(), point.yd(), i);
			i++;
		}
		return getConformingTriangles2D(coords, constraints, WB_Epsilon.EPSILON);
	}

	/**
	 *
	 *
	 * @param points
	 * @param tol
	 * @param context
	 * @return
	 */
	public static WB_Triangulation2DWithPoints triangulateConforming2D(final Collection<? extends WB_Coord> points,
			final double tol, final WB_Map2D context) {
		final int n = points.size();
		final int[] constraints = new int[2 * n];
		for (int i = 0, j = n - 1; i < n; j = i++) {
			constraints[2 * i] = j;
			constraints[2 * i + 1] = i;
		}

		final Coordinate[] coords = new Coordinate[n];
		final WB_Point point = geometryfactory.createPoint();
		int i = 0;
		for (WB_Coord p : points) {
			context.mapPoint3D(p, point);
			coords[i] = new Coordinate(point.xd(), point.yd(), i);
			i++;
		}
		return getConformingTriangles2D(coords, constraints, tol);
	}

	/**
	 *
	 *
	 * @param points
	 * @param constraints
	 * @param context
	 * @return
	 */
	public static WB_Triangulation2DWithPoints triangulateConforming2D(final Collection<? extends WB_Coord> points,
			final int[] constraints, final WB_Map2D context) {
		if (constraints == null) {
			return new WB_Triangulation2DWithPoints(triangulate2D(points, context));
		}
		final int m = constraints.length;
		if (m == 0 || m % 2 == 1) {
			return new WB_Triangulation2DWithPoints(triangulate2D(points, context));
		}
		final int n = points.size();
		final Coordinate[] coords = new Coordinate[n];
		final WB_Point point = geometryfactory.createPoint();
		int i = 0;
		for (WB_Coord p : points) {
			context.mapPoint3D(p, point);
			coords[i] = new Coordinate(point.xd(), point.yd(), i);
			i++;
		}
		return getConformingTriangles2D(coords, constraints, WB_Epsilon.EPSILON);
	}

	/**
	 *
	 *
	 * @param points
	 * @param constraints
	 * @param tol
	 * @param context
	 * @return
	 */
	public static WB_Triangulation2DWithPoints triangulateConforming2D(final Collection<? extends WB_Coord> points,
			final int[] constraints, final double tol, final WB_Map2D context) {
		if (constraints == null) {
			return new WB_Triangulation2DWithPoints(triangulate2D(points, context));
		}
		final int m = constraints.length;
		if (m == 0 || m % 2 == 1) {
			return new WB_Triangulation2DWithPoints(triangulate2D(points, context));
		}
		final int n = points.size();
		final Coordinate[] coords = new Coordinate[n];
		final WB_Point point = geometryfactory.createPoint();
		int i = 0;
		for (WB_Coord p : points) {
			context.mapPoint3D(p, point);
			coords[i] = new Coordinate(point.xd(), point.yd(), i);
			i++;
		}
		return getConformingTriangles2D(coords, constraints, tol);
	}

	/**
	 *
	 *
	 * @param polygon
	 * @return
	 */
	public static WB_Triangulation2DWithPoints triangulateConforming2D(final WB_Polygon polygon) {
		return triangulateConforming2D(polygon, WB_Epsilon.EPSILON);

	}

	/**
	 *
	 *
	 * @param polygon
	 * @param tol
	 * @return
	 */
	public static WB_Triangulation2DWithPoints triangulateConforming2D(final WB_Polygon polygon, final double tol) {
		final int n = polygon.getNumberOfPoints();
		final int[] constraints = new int[2 * n];
		int index = 0;
		for (int i = 0, j = polygon.getNumberOfShellPoints() - 1; i < polygon.getNumberOfShellPoints(); j = i++) {
			constraints[2 * index] = j;
			constraints[2 * index + 1] = i;
			index++;
		}

		int nh = polygon.getNumberOfHoles();
		int[] npc = polygon.getNumberOfPointsPerContour();
		int offset = 0;
		for (int i = 0; i < nh; i++) {
			offset += npc[i];
			for (int j = 0; j < npc[i + 1]; j++) {
				constraints[2 * index] = offset + j;
				constraints[2 * index + 1] = offset + (j + 1) % npc[i + 1];
				index++;
			}
		}

		final Coordinate[] coords = new Coordinate[n];
		final WB_Point p = new WB_Point();
		final WB_Map2D context = geometryfactory.createEmbeddedPlane(polygon.getPlane());
		for (int i = 0; i < n; i++) {
			context.mapPoint3D(polygon.getPoint(i), p);
			coords[i] = new Coordinate(p.xd(), p.yd(), i);
		}

		WB_Triangulation2DWithPoints tri = getConformingTriangles2D(coords, constraints, tol);
		List<WB_Point> upoints = new FastTable<WB_Point>();
		for (WB_Coord point : tri.getPoints()) {
			WB_Point q = new WB_Point();
			context.unmapPoint2D(point, q);
			upoints.add(q);
		}
		return new WB_Triangulation2DWithPoints(tri.getTriangles(), tri.getEdges(), upoints);

	}

	/**
	 *
	 *
	 * @param coords
	 * @param constraints
	 * @param tol
	 * @return
	 */
	private static WB_Triangulation2DWithPoints getConformingTriangles2D(final Coordinate[] coords,
			final int[] constraints, final double tol) {
		final int m = constraints.length;
		final GeometryFactory geomFact = new GeometryFactory();
		final LineString[] constraintlines = new LineString[m / 2];
		for (int i = 0; i < m; i += 2) {
			final Coordinate[] pair = { coords[constraints[i]], coords[constraints[i + 1]] };
			constraintlines[i / 2] = geomFact.createLineString(pair);
		}
		final ConformingDelaunayTriangulationBuilder dtb = new ConformingDelaunayTriangulationBuilder();
		dtb.setTolerance(tol);
		dtb.setSites(geomFact.createMultiPoint(coords));
		dtb.setConstraints(geomFact.createMultiLineString(constraintlines));
		final QuadEdgeSubdivision qesd = dtb.getSubdivision();
		final GeometryCollection tris = (GeometryCollection) qesd.getTriangles(new GeometryFactory());
		final Coordinate[] newcoords = tris.getCoordinates();
		final List<WB_Coord> uniquePoints = new FastTable<WB_Coord>();
		final WB_KDTreeInteger<WB_Point> tree = new WB_KDTreeInteger<WB_Point>();
		int currentSize = 0;
		for (final Coordinate newcoord : newcoords) {
			final WB_Point p = geometryfactory.createPoint(newcoord.x, newcoord.y, 0);
			final Integer index = tree.add(p, currentSize);
			if (index == -1) {
				currentSize++;
				uniquePoints.add(p);
			}
		}
		final int ntris = tris.getNumGeometries();
		List<int[]> result = new FastTable<int[]>();
		for (int i = 0; i < ntris; i++) {
			final Polygon tri = (Polygon) tris.getGeometryN(i);
			final Coordinate[] tricoord = tri.getCoordinates();
			final int[] triind = new int[3];
			for (int j = 0; j < 3; j++) {
				triind[j] = tree.add(geometryfactory.createPoint(tricoord[j].x, tricoord[j].y, 0), 0);
			}
			result.add(triind);
		}
		final int[] T = new int[3 * result.size()];
		for (int i = 0; i < result.size(); i++) {
			T[3 * i] = result.get(i)[0];
			T[3 * i + 1] = result.get(i)[1];
			T[3 * i + 2] = result.get(i)[2];
		}
		final MultiLineString edges = (MultiLineString) qesd.getEdges(new GeometryFactory());
		final int nedges = edges.getNumGeometries();
		result = new FastTable<int[]>();
		for (int i = 0; i < nedges; i++) {
			final LineString edge = (LineString) edges.getGeometryN(i);
			final Coordinate[] edgecoord = edge.getCoordinates();
			final int[] edgeind = new int[2];
			for (int j = 0; j < 2; j++) {
				edgeind[j] = tree.add(geometryfactory.createPoint(edgecoord[j].x, edgecoord[j].y, 0), 0);
			}
			result.add(edgeind);
		}
		final int[] E = new int[2 * result.size()];
		for (int i = 0; i < result.size(); i++) {
			E[2 * i] = result.get(i)[0];
			E[2 * i + 1] = result.get(i)[1];
		}
		final List<WB_Coord> Points = new FastTable<WB_Coord>();
		for (int i = 0; i < uniquePoints.size(); i++) {
			Points.add(uniquePoints.get(i));
		}
		return new WB_Triangulation2DWithPoints(T, E, Points);
	}

	/**
	 *
	 *
	 * @param poly
	 * @return
	 */
	public static WB_Triangulation2D triangulateConstrained2D(final WB_Polygon poly) {

		WB_ConstrainedTriangulation conTri = new WB_ConstrainedTriangulation();
		conTri.triangulatePolygon(poly);
		int[] triangles = conTri.getTrianglesAsIndices();

		return new WB_Triangulation2D(triangles, extractEdgesTri(triangles));

	}

	/**
	 *
	 *
	 * @param v
	 * @return
	 */
	private static WB_Point convert(final CVertex v) {
		return geometryfactory.createPoint(v.x(), v.y(), v.z());
	}

	/**
	 *
	 *
	 * @param v
	 * @return
	 */
	private static WB_Point convert(final Point v) {
		return geometryfactory.createPoint(v.x(), v.y(), v.z());
	}

	/**
	 *
	 *
	 * @param points
	 * @param closest
	 * @return
	 */
	public static WB_Triangulation4D triangulate4D(final List<? extends WB_Coord> points, final double closest) {
		return new WB_Triangulation4D(WB_Delaunay.getTriangulation4D(points, closest).Tri);

	}

	/**
	 *
	 *
	 * @param points
	 * @param closest
	 * @param epsilon
	 * @return
	 */
	public static WB_Triangulation4D triangulate4D(final List<? extends WB_Coord> points, final double closest,
			final double epsilon) {
		return new WB_Triangulation4D(WB_Delaunay.getTriangulation4D(points, closest, epsilon).Tri);
	}

	/**
	 *
	 *
	 * @param points
	 * @param closest
	 * @return
	 */
	public static WB_Triangulation4D triangulate4D(final WB_Coord[] points, final double closest) {
		return new WB_Triangulation4D(WB_Delaunay.getTriangulation4D(points, closest).Tri);
	}

	/**
	 *
	 *
	 * @param points
	 * @param closest
	 * @param epsilon
	 * @return
	 */
	public static WB_Triangulation4D triangulate4D(final WB_Coord[] points, final double closest,
			final double epsilon) {
		return new WB_Triangulation4D(WB_Delaunay.getTriangulation4D(points, closest, epsilon).Tri);
	}

	/**
	 *
	 *
	 * @param points
	 * @return
	 */
	public static WB_AlphaTriangulation3D alphaTriangulate3D(final WB_Coord[] points) {

		final WB_Triangulation3D tri = WB_Triangulate.triangulate3D(points);
		return new WB_AlphaTriangulation3D(tri.getTetrahedra(), points);
	}

	/**
	 *
	 *
	 * @param points
	 * @return
	 */
	public static WB_AlphaTriangulation3D alphaTriangulate3D(final Collection<? extends WB_Coord> points) {

		final WB_Triangulation3D tri = WB_Triangulate.triangulate3D(points);
		return new WB_AlphaTriangulation3D(tri.getTetrahedra(), points);
	}

	/**
	 *
	 * @param points
	 * @param jitter
	 * @return
	 */
	public static WB_AlphaTriangulation3D alphaTriangulate3D(final WB_Coord[] points, final double jitter) {
		WB_Coord[] jigPoints = Arrays.copyOf(points, points.length);
		WB_RandomOnSphere ros = new WB_RandomOnSphere();
		int i = 0;
		for (WB_Coord p : points) {
			jigPoints[i++] = WB_Point.addMul(p, jitter, ros.nextVector());

		}

		final WB_Triangulation3D tri = WB_Triangulate.triangulate3D(jigPoints);
		return new WB_AlphaTriangulation3D(tri.getTetrahedra(), points);
	}

	/**
	 *
	 * @param points
	 * @param jitter
	 * @return
	 */
	public static WB_AlphaTriangulation3D alphaTriangulate3D(final Collection<? extends WB_Coord> points,
			final double jitter) {
		FastTable<WB_Point> jigPoints = new FastTable<WB_Point>();
		WB_RandomOnSphere ros = new WB_RandomOnSphere();
		for (WB_Coord p : points) {
			jigPoints.add(WB_Point.addMul(p, jitter, ros.nextVector()));

		}
		final WB_Triangulation3D tri = WB_Triangulate.triangulate3D(jigPoints);
		return new WB_AlphaTriangulation3D(tri.getTetrahedra(), points);
	}

	/**
	 *
	 *
	 * @param ears
	 * @return
	 */
	private static int[] extractEdgesTri(final int[] ears) {
		final int f = ears.length;
		final FastMap<Long, int[]> map = new FastMap<Long, int[]>();
		for (int i = 0; i < ears.length; i += 3) {
			final int v0 = ears[i];
			final int v1 = ears[i + 1];
			final int v2 = ears[i + 2];
			long index = getIndex(v0, v1, f);
			map.put(index, new int[] { v0, v1 });
			index = getIndex(v1, v2, f);
			map.put(index, new int[] { v1, v2 });
			index = getIndex(v2, v0, f);
			map.put(index, new int[] { v2, v0 });
		}
		final int[] edges = new int[2 * map.size()];
		final Collection<int[]> values = map.values();
		int i = 0;
		for (final int[] value : values) {
			edges[2 * i] = value[0];
			edges[2 * i + 1] = value[1];
			i++;
		}
		return edges;
	}

	/**
	 *
	 *
	 * @param i
	 * @param j
	 * @param f
	 * @return
	 */
	private static long getIndex(final int i, final int j, final int f) {
		return i > j ? j + i * f : i + j * f;
	}

	/**
	 *
	 *
	 * @param points
	 * @return
	 */
	public static WB_AlphaTriangulation2D alphaTriangulate2D(final Collection<? extends WB_Coord> points) {

		final WB_Triangulation2D tri = WB_Triangulate.triangulate2D(points);
		return new WB_AlphaTriangulation2D(tri.getTriangles(), points);
	}

	/**
	 *
	 * @param points
	 * @param jitter
	 * @return
	 */
	public static WB_AlphaTriangulation2D alphaTriangulate2D(final Collection<? extends WB_Coord> points,
			final double jitter) {
		FastTable<WB_Point> jigPoints = new FastTable<WB_Point>();
		WB_RandomOnSphere ros = new WB_RandomOnSphere();
		for (WB_Coord p : points) {
			jigPoints.add(WB_Point.addMul(p, jitter, ros.nextVector()));

		}
		final WB_Triangulation2D tri = WB_Triangulate.triangulate2D(jigPoints);
		return new WB_AlphaTriangulation2D(tri.getTriangles(), points);
	}

	/**
	 *
	 *
	 * @param points
	 * @return
	 */
	public static WB_AlphaTriangulation2D alphaTriangulate2D(final WB_Coord[] points) {

		final WB_Triangulation2D tri = WB_Triangulate.triangulate2D(points);
		return new WB_AlphaTriangulation2D(tri.getTriangles(), points);
	}

	/**
	 *
	 * @param points
	 * @param jitter
	 * @return
	 */
	public static WB_AlphaTriangulation2D alphaTriangulate2D(final WB_Coord[] points, final double jitter) {
		WB_Coord[] jigPoints = Arrays.copyOf(points, points.length);
		WB_RandomOnSphere ros = new WB_RandomOnSphere();
		int i = 0;
		for (WB_Coord p : points) {
			jigPoints[i++] = WB_Point.addMul(p, jitter, ros.nextVector());

		}

		final WB_Triangulation2D tri = WB_Triangulate.triangulate2D(jigPoints);
		return new WB_AlphaTriangulation2D(tri.getTriangles(), points);
	}
}
