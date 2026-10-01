package myau.util.font;

public interface RavenFontRenderer {
    int drawString(String text, float x, float y, int color, boolean shadow);
    default int drawString(String text, float x, float y, int color) {
        return this.drawString(text, x, y, color, false);
    }
    default int drawStringWithShadow(String text, float x, float y, int color) {
        return this.drawString(text, x, y, color, true);
    }
    int getStringWidth(String text);
    int getFontHeight();
    default int getLineHeight() {
        return this.getFontHeight();
    }
    default int getTextTopOffset() {
        return 0;
    }
    default int getTextBottomOffset() {
        return this.getFontHeight();
    }
    default void destroy() {
    }
}
