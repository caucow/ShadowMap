package com.caucraft.shadowmap.client.util.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

/**
 * Utility class for figuring out if a block's collision boxes fill a full
 * square.
 */
public class Area {

    private List<Rectangle> existingList;

    public Area() {
        existingList = new ArrayList<>();
        existingList.add(new Rectangle(0, 0, 1, 1));
    }

    public void subtract(double x1, double y1, double x2, double y2) {
        // If area already fully covered, do nothing.
        if (isEmpty()) {
            return;
        }

        ListIterator<Rectangle> rectangleIterator = existingList.listIterator();
        int remaining = existingList.size();

        while (rectangleIterator.hasNext() && remaining-- > 0) {
            Rectangle r = rectangleIterator.next();

            // If passed rectangle doesn't intersect at all with current, skip current.
            if (x1 >= r.x2 || x2 <= r.x1 || y1 >= r.y2 || y2 <= r.y1) {
                continue;
            }

            // Remove current, it definitely intersects, we'll add up to 4 new ones depending on intersection.
            rectangleIterator.remove();

            // Cache values for readables ig
            double rx1 = r.x1;
            double ry1 = r.y1;
            double rx2 = r.x2;
            double ry2 = r.y2;

            // Re-add remaining non-intersecting rectangles to list.
            // X-axis rectangles get greedy-priority, Y-axis gets the remainder.
            if (x1 > rx1 && x1 <= rx2) {
                rectangleIterator.add(new Rectangle(rx1, ry1, x1, ry2));
            }
            if (x2 >= rx1 && x2 < rx2) {
                rectangleIterator.add(new Rectangle(x2, ry1, rx2, ry2));
            }
            if (y1 > ry1 && y1 <= ry2) {
                rectangleIterator.add(new Rectangle(Math.max(x1, rx1), ry1, Math.min(x2, rx2), y1));
            }
            if (y2 >= ry1 && y2 < ry2) {
                rectangleIterator.add(new Rectangle(Math.max(x1, rx1), y2, Math.min(x2, rx2), ry2));
            }
        }

        // If area has been covered, now set the list to null since it's not needed.
        if (existingList.isEmpty()) {
            existingList = null;
        }
    }

    public boolean isEmpty() {
        return existingList == null;
    }

    public List<Rectangle> getRemaining() {
        if (isEmpty()) {
            return Collections.EMPTY_LIST;
        }
        return Collections.unmodifiableList(existingList);
    }

    public static record Rectangle(double x1, double y1, double x2, double y2) {}
}
