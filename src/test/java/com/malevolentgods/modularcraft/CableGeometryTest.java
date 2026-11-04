package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.client.CableGeometry;
import com.malevolentgods.modularcraft.client.CableGeometry.Facing;
import com.malevolentgods.modularcraft.client.CableGeometry.Point;
import java.util.ArrayList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CableGeometryTest {
  private static final double EPSILON = 1e-10;

  @Test
  void sagLeavesRoomForTheWholeCableAboveTheBlockBase() {
    var points = CableGeometry.centerline(new Point(0, .2, 0), new Point(1, .2, 0), 0);
    for (Point point : points)
      Assertions.assertTrue(point.y() - 3.0 / 128 >= 1.0 / 64, "Cable clips below base: " + point);
    Assertions.assertEquals(25, points.size());
  }

  @Test
  void endpointsMatchThePaintedJacksOnEveryFacing() {
    int[][] paintedCenters = {{32}, {16, 48}, {11, 32, 53}};
    for (Facing face : Facing.values()) {
      for (int count = 1; count <= 3; count++) {
        for (int index = 0; index < count; index++) {
          Point point = CableGeometry.port(0, 0, 0, face, index, count);
          Assertions.assertEquals(.125, point.y(), 1e-12);
          double u = paintedCenters[count - 1][index] / 64.0;
          double actual =
              switch (face) {
                case NORTH -> 1 - point.x();
                case SOUTH -> point.x();
                case EAST -> 1 - point.z();
                case WEST -> point.z();
              };
          Assertions.assertEquals(u, actual, 1e-12);
          double outward =
              switch (face) {
                case NORTH -> -point.z();
                case SOUTH -> point.z() - 1;
                case EAST -> point.x() - 1;
                case WEST -> -point.x();
              };
          Assertions.assertTrue(outward > 3.0 / 128);
        }
      }
    }
  }

  @Test
  void tubeIsOneContinuousEightSidedSurfaceWithConstantDiameter() {
    Point from = new Point(0, .125, 0), to = new Point(2, .125, 0);
    var mesh = CableGeometry.mesh(from, to, 0);
    Assertions.assertEquals(25, mesh.tube().size());
    Assertions.assertEquals(from, mesh.tube().getFirst().center());
    assertNear(to, mesh.tube().getLast().center());
    for (var ring : mesh.tube()) {
      Assertions.assertEquals(8, ring.vertices().size());
      for (int side = 0; side < 8; side++) {
        Point point = ring.vertices().get(side);
        Assertions.assertEquals(1.0 / 64, distance(point, ring.center()), EPSILON);
        Assertions.assertEquals(
            1.0 / 32, distance(point, ring.vertices().get((side + 4) % 8)), EPSILON);
      }
    }
    Assertions.assertEquals(3, mesh.collar().size());
    assertNear(mesh.collar().get(0).center(), mesh.collar().get(1).center());
    Assertions.assertTrue(
        distance(mesh.collar().get(1).vertices().getFirst(), mesh.collar().get(1).center())
            > distance(mesh.collar().get(2).vertices().getFirst(), mesh.collar().get(2).center()));
    Assertions.assertTrue(
        distance(to, mesh.collar().get(1).center()) > distance(to, mesh.collar().get(2).center()));
  }

  @Test
  void everyVertexClearsTheBaseForAllOrientationsAndElevations() {
    for (Facing fromFace : Facing.values()) {
      for (Facing toFace : Facing.values()) {
        for (int height : new int[] {-12, 0, 12}) {
          Point from = CableGeometry.port(-3, -5, 2, fromFace, 0, 1);
          Point to = CableGeometry.port(-2, -5 + height, 2, toFace, 0, 3);
          var mesh = CableGeometry.mesh(from, to, Math.min(-5, -5 + height));
          var rings = new ArrayList<>(mesh.tube());
          rings.addAll(mesh.collar());
          for (var ring : rings)
            for (Point vertex : ring.vertices())
              Assertions.assertTrue(vertex.y() >= Math.min(-5, -5 + height) + 1.0 / 64 - EPSILON);
        }
      }
    }
  }

  @Test
  void verticalShortAndMaximumLengthCablesHaveFiniteUntwistedRings() {
    Point from = new Point(0, .125, 0);
    for (Point to :
        new Point[] {
          new Point(0, 16.125, 0),
          new Point(0, -15.875, 0),
          new Point(.000001, .125, 0),
          new Point(16, .125, 0),
          new Point(.000001, 1.125, .000001)
        }) {
      var mesh = CableGeometry.mesh(from, to, Math.floor(Math.min(from.y(), to.y())));
      Point previous = null;
      for (var ring : mesh.tube()) {
        Point radial = ring.vertices().getFirst().subtract(ring.center());
        if (previous != null) Assertions.assertTrue(radial.dot(previous) > 0);
        previous = radial;
        for (Point point : ring.vertices()) {
          Assertions.assertTrue(
              Double.isFinite(point.x())
                  && Double.isFinite(point.y())
                  && Double.isFinite(point.z()));
          Assertions.assertEquals(1.0 / 64, distance(point, ring.center()), EPSILON);
        }
      }
    }
    Assertions.assertTrue(CableGeometry.mesh(from, from, 0).tube().isEmpty());
  }

  @Test
  void fanOutRetainsIndependentCablesWithSharedJackEndpoints() {
    Point from = CableGeometry.port(0, 0, 0, Facing.NORTH, 0, 1);
    var first = CableGeometry.mesh(from, CableGeometry.port(1, 0, 0, Facing.NORTH, 1, 3), 0);
    var second = CableGeometry.mesh(from, CableGeometry.port(2, 0, 0, Facing.NORTH, 1, 2), 0);
    Assertions.assertEquals(first.tube().getFirst().center(), second.tube().getFirst().center());
    Assertions.assertNotEquals(first.tube().getLast().center(), second.tube().getLast().center());
    Assertions.assertEquals(25, first.tube().size());
    Assertions.assertEquals(25, second.tube().size());
  }

  private static double distance(Point a, Point b) {
    Point delta = a.subtract(b);
    return Math.sqrt(delta.dot(delta));
  }

  private static void assertNear(Point expected, Point actual) {
    Assertions.assertEquals(0, distance(expected, actual), EPSILON);
  }
}
