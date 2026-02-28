package com.sorted.common.utils;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.geo.GeoJsonLineString;
import org.springframework.data.mongodb.core.geo.GeoJsonPolygon;

import java.util.List;

public class GeoUtils {

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    public static Polygon toJTSPolygon(GeoJsonPolygon geoJsonPolygon) {

        GeoJsonLineString ring = geoJsonPolygon.getCoordinates().get(0);
        List<Point> points = ring.getCoordinates();

        if (points.size() < 4) {
            throw new IllegalArgumentException("Invalid polygon");
        }

        Coordinate[] coordinates = points.stream()
                .map(p -> new Coordinate(p.getX(), p.getY()))
                .toArray(Coordinate[]::new);

        coordinates = closeRing(coordinates);

        return GEOMETRY_FACTORY.createPolygon(coordinates);
    }

    public static boolean contains(GeoJsonPolygon polygon, double lat, double lng) {

        Polygon jtsPolygon = toJTSPolygon(polygon);

        org.locationtech.jts.geom.Point point =
                GEOMETRY_FACTORY.createPoint(new Coordinate(lng, lat));

        return jtsPolygon.covers(point); // 🔥 better than contains
    }

    private static Coordinate[] closeRing(Coordinate[] coords) {
        if (!coords[0].equals2D(coords[coords.length - 1])) {
            Coordinate[] closed = new Coordinate[coords.length + 1];
            System.arraycopy(coords, 0, closed, 0, coords.length);
            closed[closed.length - 1] = coords[0];
            return closed;
        }
        return coords;
    }
}
