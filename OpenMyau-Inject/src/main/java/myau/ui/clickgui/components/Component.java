package myau.ui.clickgui.components;

public class Component {
    public void render() {
    }
    public void drawScreen(int mouseX, int mouseY) {
    }
    public boolean onClick(int mouseX, int mouseY, int button) {
        return false;
    }
    public void mouseReleased(int mouseX, int mouseY, int button) {
    }
    public void keyTyped(char typed, int key) {
    }

    public void onScroll(int scroll) {
    }

    public void onGuiClosed() {
    }
    public void updateHeight(float offset) {
    }
    public float getOffset() {
        return 0.0F;
    }
    public int getHeight() {
        return Math.round(this.getHeightF());
    }
    public float getHeightF() {
        return 0.0F;
    }
    public boolean isBaseVisible() {
        return true;
    }
}
