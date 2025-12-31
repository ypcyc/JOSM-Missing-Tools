# MissingTools — JOSM Plugin

**MissingTools** is a JOSM plugin that adds several helpful editing tools that are not available in JOSM by default.  
It provides improved geometry manipulation for polygons and multipolygons — making advanced editing workflows faster and more intuitive.

---

## ✨ Features

### 🔹 Cut Polygon (CutPolygonAction) — **Shortcut: Shift + X**

Cuts a multipolygon using a cutting line automatically computed from the user’s selection.

#### How to use:

1. Select **two nodes outside** the multipolygon you want to cut (for example highways, waterways or power lines can be used).
2. Press and **keep holding** **Shift + X**.
3. **Click Left mouse button and keep it pressed** and start moving cursor away from center of imaginary cutting line.
4. The plugin finds a **valid route** between those two nodes using surrounding ways.
5. That found route becomes the **cutting line**. You can see projected cutting lines that you can move with mouse.
6. Release **Left mouse button** and later also **Shift + X**
7. The multipolygon is then **split into two valid multipolygons**.
8. Resulting relations are **rebuilt and cleaned up** to remain valid.

#### Technical details:

- The simplified routing logic is adapted from the JOSM **routing plugin**  
  https://github.com/JOSM/josm-plugins/tree/master/routing
- The multipolygon building and cleanup logic is partly adapted from the **reltoolbox plugin**  
  https://github.com/JOSM/josm-plugins/tree/master/reltoolbox
- Combined together, they allow complex multipolygon cuts even across nested relations.

### 🔹 Unlink Polygon from Ways (UnlinkPolygonAction) — **Shortcut: Shift + G**

Unlinks polygon geometry from adjacent ways by ungluing shared nodes.

Useful when:

- cleaning up overly-connected geometry
- separating boundaries from road networks
- preparing areas for geometry correction tools

---

## 📦 Installation

### Option 1 — From JOSM Plugin Repository (recommended)

Once approved, install via:
