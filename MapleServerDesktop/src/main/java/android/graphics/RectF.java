package android.graphics;

// Desktop stand-in for android.graphics.RectF: the float counterpart of Rect.
public final class RectF {
    public float left;
    public float top;
    public float right;
    public float bottom;

    public RectF() {
        this.left = 0f;
        this.top = 0f;
        this.right = 0f;
        this.bottom = 0f;
    }

    public RectF(float left, float top, float right, float bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public RectF(Rect r) {
        this.left = r.left;
        this.top = r.top;
        this.right = r.right;
        this.bottom = r.bottom;
    }

    public void set(float left, float top, float right, float bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public final float width() {
        return right - left;
    }

    public final float height() {
        return bottom - top;
    }

    public boolean isEmpty() {
        return left >= right || top >= bottom;
    }

    public boolean contains(float x, float y) {
        return left < right && top < bottom && x >= left && x < right && y >= top && y < bottom;
    }

    @Override
    public String toString() {
        return "RectF(" + left + ", " + top + " - " + right + ", " + bottom + ")";
    }
}
