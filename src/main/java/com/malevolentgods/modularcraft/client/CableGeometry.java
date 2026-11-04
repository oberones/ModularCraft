package com.malevolentgods.modularcraft.client;

import java.util.ArrayList;
import java.util.List;

/** Minecraft-independent cable geometry, owned by the client renderer. Units are blocks. */
public final class CableGeometry {
  public static final int SEGMENTS = 24;
  public static final int SIDES = 8;
  public static final double RADIUS = 1.0 / 64;
  public static final double COLLAR_RADIUS = 3.0 / 128;
  public static final double CLEARANCE = 1.0 / 64;
  private static final double PANEL_OFFSET = 1.0 / 32;

  private CableGeometry() {}

  public record Point(double x, double y, double z) {
    public Point add(Point other) {
      return new Point(x + other.x, y + other.y, z + other.z);
    }

    public Point scale(double scale) {
      return new Point(x * scale, y * scale, z * scale);
    }

    public Point subtract(Point other) {
      return add(other.scale(-1));
    }

    public double dot(Point other) {
      return x * other.x + y * other.y + z * other.z;
    }

    public Point cross(Point other) {
      return new Point(
          y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x);
    }

    private Point unit() {
      return scale(1 / Math.sqrt(dot(this)));
    }
  }

  /** Shared vertices at each curve sample keep adjacent quads joined exactly. */
  public record Ring(Point center, List<Point> vertices) {
    public Ring {
      vertices = List.copyOf(vertices);
    }
  }

  /** A closed tube plus an attached tapered sleeve; no line primitives or branches. */
  public record Mesh(List<Ring> tube, List<Ring> collar) {
    public Mesh {
      tube = List.copyOf(tube);
      collar = List.copyOf(collar);
    }
  }

  public enum Facing {
    NORTH,
    SOUTH,
    EAST,
    WEST
  }

  public static Point port(int x, int y, int z, Facing facing, int index, int count) {
    // Match generate-assets.py, which rounds jack centers to the nearest of 64 pixels.
    double u = Math.rint((index + .5) * 64 / count) / 64;
    return switch (facing) {
      case NORTH -> new Point(x + 1 - u, y + .125, z - PANEL_OFFSET);
      case SOUTH -> new Point(x + u, y + .125, z + 1 + PANEL_OFFSET);
      case EAST -> new Point(x + 1 + PANEL_OFFSET, y + .125, z + 1 - u);
      case WEST -> new Point(x - PANEL_OFFSET, y + .125, z + u);
    };
  }

  /** Smooth sag bounded by the lower block base, including the widest collar radius. */
  public static List<Point> centerline(Point from, Point to, double lowerBase) {
    List<Point> points = new ArrayList<>();
    double sag = sag(from, to, lowerBase);
    for (int step = 0; step <= SEGMENTS; step++)
      points.add(sample(from, to, sag, step / (double) SEGMENTS));
    return List.copyOf(points);
  }

  /** Bounded to 25 tube rings and three collar rings. Coincident endpoints emit nothing. */
  public static Mesh mesh(Point from, Point to, double lowerBase) {
    Point delta = to.subtract(from);
    double length = Math.sqrt(delta.dot(delta));
    if (length < 1e-9) return new Mesh(List.of(), List.of());
    double horizontal = Math.hypot(delta.x, delta.z);
    // The curve lies in one vertical plane: its perpendicular never rotates or flips.
    Point side =
        horizontal > 1e-12
            ? new Point(-delta.z / horizontal, 0, delta.x / horizontal)
            : new Point(1, 0, 0);
    double sag = sag(from, to, lowerBase);
    List<Ring> tube = new ArrayList<>();
    for (int step = 0; step <= SEGMENTS; step++)
      tube.add(ring(from, to, side, sag, step / (double) SEGMENTS, RADIUS));
    // About 0.1 block long, shrinking with very short cables; taper points at the input.
    double start = Math.max(.5, 1 - .16 / length);
    double end = Math.max(.8, 1 - .06 / length);
    return new Mesh(
        tube,
        List.of(
            ring(from, to, side, sag, start, RADIUS),
            ring(from, to, side, sag, start, COLLAR_RADIUS),
            ring(from, to, side, sag, end, RADIUS)));
  }

  private static double sag(Point from, Point to, double lowerBase) {
    double available = Math.max(0, Math.min(from.y, to.y) - lowerBase - CLEARANCE - COLLAR_RADIUS);
    // Suppress loops on short/vertical cables while retaining a shallow curve elsewhere.
    return Math.min(.08, Math.min(available, .12 * Math.hypot(to.x - from.x, to.z - from.z)));
  }

  private static Point sample(Point from, Point to, double sag, double t) {
    if (t == 0) return from;
    if (t == 1) return to;
    return from.scale(1 - t).add(to.scale(t)).add(new Point(0, -sag * Math.sin(Math.PI * t), 0));
  }

  private static Ring ring(Point from, Point to, Point side, double sag, double t, double radius) {
    Point center = sample(from, to, sag, t);
    Point tangent =
        to.subtract(from).add(new Point(0, -sag * Math.PI * Math.cos(Math.PI * t), 0)).unit();
    Point up = tangent.cross(side).unit();
    List<Point> vertices = new ArrayList<>();
    for (int i = 0; i < SIDES; i++) {
      double angle = 2 * Math.PI * i / SIDES;
      vertices.add(
          center.add(side.scale(radius * Math.cos(angle))).add(up.scale(radius * Math.sin(angle))));
    }
    return new Ring(center, vertices);
  }
}
