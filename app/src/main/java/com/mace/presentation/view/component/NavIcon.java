package com.mace.presentation.view.component;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.shape.*;

/**
 * Iconos vectoriales minimalistas para el menu lateral (CPU, GPU, Historial, Procesos).
 *
 * Dibujados a mano con formas basicas de JavaFX (Rectangle, Circle, Line, Polyline) en
 * vez de un archivo SVG o una libreria de iconos externa, para no agregar ninguna
 * dependencia nueva al pom.xml. El color se controla por completo desde dark-theme.css
 * via la clase "nav-icon" (cambia solo con :hover/:selected del boton contenedor).
 */
public final class NavIcon {

    private static final double SIZE = 18;
    private static final double STROKE_WIDTH = 1.6;

    private NavIcon() {
    }

    public static Node cpu() {
        Rectangle outer = square(4, 4, 10);
        Rectangle inner = square(7, 7, 4);

        Line pinTop = line(9, 1, 9, 4);
        Line pinBottom = line(9, 14, 9, 17);
        Line pinLeft = line(1, 9, 4, 9);
        Line pinRight = line(14, 9, 17, 9);

        return icon(outer, inner, pinTop, pinBottom, pinLeft, pinRight);
    }

    public static Node gpu() {
        Rectangle body = new Rectangle(2, 5, 14, 8);
        body.setArcWidth(3);
        body.setArcHeight(3);
        style(body);

        Circle fan = new Circle(9, 9, 2.3);
        style(fan);

        Line port = line(6, 15.5, 12, 15.5);

        return icon(body, fan, port);
    }

    public static Node historial() {
        Polyline trend = new Polyline(2, 14, 6, 8, 9, 11, 12, 5, 16, 9);
        style(trend);

        Line baseline = line(2, 16, 16, 16);

        return icon(trend, baseline);
    }

    public static Node procesos() {
        Circle bullet1 = dot(2, 5);
        Circle bullet2 = dot(2, 9);
        Circle bullet3 = dot(2, 13);

        Line row1 = line(5, 5, 16, 5);
        Line row2 = line(5, 9, 16, 9);
        Line row3 = line(5, 13, 16, 13);

        return icon(bullet1, row1, bullet2, row2, bullet3, row3);
    }

    // ---- helpers ----

    private static Node icon(Shape... shapes) {
        Group group = new Group(shapes);
        group.setManaged(false);
        group.resize(SIZE, SIZE);
        return group;
    }

    private static Rectangle square(double x, double y, double side) {
        Rectangle r = new Rectangle(x, y, side, side);
        r.setArcWidth(2);
        r.setArcHeight(2);
        style(r);
        return r;
    }

    private static Line line(double x1, double y1, double x2, double y2) {
        Line l = new Line(x1, y1, x2, y2);
        style(l);
        return l;
    }

    private static Circle dot(double cx, double cy) {
        Circle c = new Circle(cx, cy, 1);
        c.getStyleClass().add("nav-icon-dot");
        return c;
    }

    private static void style(Shape shape) {
        shape.setFill(javafx.scene.paint.Color.TRANSPARENT);
        shape.setStrokeWidth(STROKE_WIDTH);
        shape.setStrokeLineCap(StrokeLineCap.ROUND);
        shape.setStrokeLineJoin(StrokeLineJoin.ROUND);
        shape.getStyleClass().add("nav-icon");
    }
}