package org.example.coin_laundry_app_backend.geo.external;

import java.util.List;

public record GetReverseGeocodingResponse(Status status, List<Result> results) {

    public record Status(int code, String name, String message) {}

    public record Result(String name, Code code, Region region) {}

    public record Code(String id, String type, String mappingId) {}

    public record Region(Area area0, Area area1, Area area2, Area area3, Area area4) {}

    public record Area(String name, Coords coords, String alias) {
        final static Area EMPTY = new Area("", new Coords(new Center("", 0, 0)), "");
    }

    public record Coords(Center center) {}

    public record Center(String crs, double x, double y) {}

}
