package android.graphics;

// Desktop stand-in for android.graphics.Point. The server core uses it purely as an int x/y value
// type, so this reproduces the public fields and the small method surface it relies on.
public final class Point {
    public int x;
    public int y;

    public Point() {
        this.x = 0;
        this.y = 0;
    }

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public Point(Point src) {
        this.x = src.x;
        this.y = src.y;
    }

    public void set(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void set(Point src) {
        this.x = src.x;
        this.y = src.y;
    }

    public void negate() {
        x = -x;
        y = -y;
    }

    public void offset(int dx, int dy) {
        x += dx;
        y += dy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Point)) {
            return false;
        }
        Point p = (Point) o;
        return x == p.x && y == p.y;
    }

    @Override
    public int hashCode() {
        return 31 * (31 + x) + y;
    }

    @Override
    public String toString() {
        return "Point(" + x + ", " + y + ")";
    }
}
