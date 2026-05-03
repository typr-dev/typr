package testdb

import org.mariadb.jdbc.`type`.*
import org.scalatest.funsuite.AnyFunSuite
import testdb.mariatest_spatial.*
import testdb.mariatest_spatial_null.*
import testdb.customtypes.Defaulted

/** Tests for spatial/geometry types in MariaDB. */
class SpatialTypesTest extends AnyFunSuite {
  val spatialRepo: MariatestSpatialRepoImpl = new MariatestSpatialRepoImpl
  val spatialNullRepo: MariatestSpatialNullRepoImpl = new MariatestSpatialNullRepoImpl

  /** Create test point */
  private def createPoint(x: Double, y: Double): Point = new Point(x, y)

  /** Create test linestring */
  private def createLineString(): LineString = {
    val points = Array(createPoint(0, 0), createPoint(1, 1), createPoint(2, 2))
    new LineString(points, false)
  }

  /** Create test polygon (simple square) */
  private def createPolygon(): Polygon = {
    val ring = Array(
      createPoint(0, 0),
      createPoint(0, 1),
      createPoint(1, 1),
      createPoint(1, 0),
      createPoint(0, 0)
    )
    val rings = Array(new LineString(ring, true))
    new Polygon(rings)
  }

  /** Create test multipoint */
  private def createMultiPoint(): MultiPoint = {
    val points = Array(createPoint(0, 0), createPoint(1, 1), createPoint(2, 2))
    new MultiPoint(points)
  }

  /** Create test multilinestring */
  private def createMultiLineString(): MultiLineString = {
    val lines = Array(createLineString())
    new MultiLineString(lines)
  }

  /** Create test multipolygon */
  private def createMultiPolygon(): MultiPolygon = {
    val polygons = Array(createPolygon())
    new MultiPolygon(polygons)
  }

  /** Create test geometry collection */
  private def createGeometryCollection(): GeometryCollection = {
    val geometries: Array[Geometry] = Array(createPoint(5, 5), createPoint(6, 6))
    new GeometryCollection(geometries)
  }

  test("pointInsertAndSelect") {
    withConnection {
      val unsaved = MariatestSpatialRowUnsaved(
        createPoint(1, 2), // geometry_col (Point is a subtype of Geometry)
        createPoint(3, 4), // point_col
        createLineString(),
        createPolygon(),
        createMultiPoint(),
        createMultiLineString(),
        createMultiPolygon(),
        createGeometryCollection()
      )

      val inserted = spatialRepo.insert(unsaved)

      val _ = assert(inserted != null)
      val _ = assert(inserted.id.value >= 0)
      val _ = assert(inserted.pointCol != null)

      // Verify point coordinates
      val _ = assert(Math.abs(inserted.pointCol.getX - 3.0) < 0.001)
      val _ = assert(Math.abs(inserted.pointCol.getY - 4.0) < 0.001)

      // Select back
      val found = spatialRepo.selectById(inserted.id)
      val _ = assert(found.isDefined)
      val _ = assert(Math.abs(found.get.pointCol.getX - 3.0) < 0.001)
      assert(Math.abs(found.get.pointCol.getY - 4.0) < 0.001)
    }
  }

  test("lineStringInsertAndSelect") {
    withConnection {
      val linestring = createLineString()

      val unsaved = MariatestSpatialRowUnsaved(
        createPoint(0, 0),
        createPoint(0, 0),
        linestring,
        createPolygon(),
        createMultiPoint(),
        createMultiLineString(),
        createMultiPolygon(),
        createGeometryCollection()
      )

      val inserted = spatialRepo.insert(unsaved)

      val _ = assert(inserted.linestringCol != null)
      // LineString should have 3 points
      val _ = assert(inserted.linestringCol.getPoints.length > 0)

      // Select back
      val found = spatialRepo.selectById(inserted.id).get
      assert(found.linestringCol != null)
    }
  }

  test("polygonInsertAndSelect") {
    withConnection {
      val polygon = createPolygon()

      val unsaved = MariatestSpatialRowUnsaved(
        createPoint(0, 0),
        createPoint(0, 0),
        createLineString(),
        polygon,
        createMultiPoint(),
        createMultiLineString(),
        createMultiPolygon(),
        createGeometryCollection()
      )

      val inserted = spatialRepo.insert(unsaved)

      val _ = assert(inserted.polygonCol != null)

      // Select back
      val found = spatialRepo.selectById(inserted.id).get
      assert(found.polygonCol != null)
    }
  }

  test("multiPointInsertAndSelect") {
    withConnection {
      val multipoint = createMultiPoint()

      val unsaved = MariatestSpatialRowUnsaved(
        createPoint(0, 0),
        createPoint(0, 0),
        createLineString(),
        createPolygon(),
        multipoint,
        createMultiLineString(),
        createMultiPolygon(),
        createGeometryCollection()
      )

      val inserted = spatialRepo.insert(unsaved)

      val _ = assert(inserted.multipointCol != null)

      // Select back
      val found = spatialRepo.selectById(inserted.id).get
      assert(found.multipointCol != null)
    }
  }

  test("geometryCollectionInsertAndSelect") {
    withConnection {
      val collection = createGeometryCollection()

      val unsaved = MariatestSpatialRowUnsaved(
        createPoint(0, 0),
        createPoint(0, 0),
        createLineString(),
        createPolygon(),
        createMultiPoint(),
        createMultiLineString(),
        createMultiPolygon(),
        collection
      )

      val inserted = spatialRepo.insert(unsaved)

      val _ = assert(inserted.geometrycollectionCol != null)

      // Select back
      val found = spatialRepo.selectById(inserted.id).get
      assert(found.geometrycollectionCol != null)
    }
  }

  test("nullableSpatialWithAllNulls") {
    withConnection {
      // Use short constructor that sets all fields to UseDefault
      val unsaved = MariatestSpatialNullRowUnsaved()

      val inserted = spatialNullRepo.insert(unsaved)

      val _ = assert(inserted != null)
      val _ = assert(inserted.id.value >= 0)

      // All spatial columns should be empty
      val _ = assert(inserted.geometryCol.isEmpty)
      val _ = assert(inserted.pointCol.isEmpty)
      val _ = assert(inserted.linestringCol.isEmpty)
      val _ = assert(inserted.polygonCol.isEmpty)
      val _ = assert(inserted.multipointCol.isEmpty)
      val _ = assert(inserted.multilinestringCol.isEmpty)
      val _ = assert(inserted.multipolygonCol.isEmpty)
      assert(inserted.geometrycollectionCol.isEmpty)
    }
  }

  test("nullableSpatialWithValues") {
    withConnection {
      val point = createPoint(10, 20)

      // Use short constructor and chain copy methods
      val unsaved = MariatestSpatialNullRowUnsaved(
        geometryCol = Defaulted.Provided(Some(point)),
        pointCol = Defaulted.Provided(Some(point))
      )

      val inserted = spatialNullRepo.insert(unsaved)

      val _ = assert(inserted != null)
      val _ = assert(inserted.geometryCol.isDefined)
      val _ = assert(inserted.pointCol.isDefined)

      val _ = assert(Math.abs(inserted.pointCol.get.getX - 10.0) < 0.001)
      assert(Math.abs(inserted.pointCol.get.getY - 20.0) < 0.001)
    }
  }

  test("spatialUpdate") {
    withConnection {
      val unsaved = MariatestSpatialRowUnsaved(
        createPoint(0, 0),
        createPoint(1, 1),
        createLineString(),
        createPolygon(),
        createMultiPoint(),
        createMultiLineString(),
        createMultiPolygon(),
        createGeometryCollection()
      )

      val inserted = spatialRepo.insert(unsaved)

      // Update point
      val newPoint = createPoint(100, 200)
      val updated = inserted.copy(pointCol = newPoint)
      val _ = spatialRepo.update(updated)

      // Verify update
      val found = spatialRepo.selectById(inserted.id).get
      val _ = assert(Math.abs(found.pointCol.getX - 100.0) < 0.001)
      assert(Math.abs(found.pointCol.getY - 200.0) < 0.001)
    }
  }

  test("spatialDelete") {
    withConnection {
      val unsaved = MariatestSpatialRowUnsaved(
        createPoint(0, 0),
        createPoint(1, 1),
        createLineString(),
        createPolygon(),
        createMultiPoint(),
        createMultiLineString(),
        createMultiPolygon(),
        createGeometryCollection()
      )

      val inserted = spatialRepo.insert(unsaved)

      val deleted = spatialRepo.deleteById(inserted.id)
      val _ = assert(deleted)

      val found = spatialRepo.selectById(inserted.id)
      assert(found.isEmpty)
    }
  }
}
