// SPDX-License-Identifier: AGPL-3.0-only
import com.android.aapt.Resources;
import com.android.aapt.Resources.XmlAttribute;
import com.android.aapt.Resources.XmlElement;
import com.android.aapt.Resources.XmlNode;
import java.nio.file.Files;
import java.nio.file.Path;

// Edit the official SDK's typed manifest representation; untouched resource IDs stay typed.
public final class Manifest {
  static final String ORIGINAL = "com.hoptodesk.app";
  static final String APP = "cn.desklink.hopcontroller";
  static final String ANDROID = "http://schemas.android.com/apk/res/android";

  static XmlAttribute.Builder string(XmlAttribute.Builder a, String value) {
    a.setValue(value);
    if (a.hasCompiledItem())
      a.getCompiledItemBuilder().setStr(Resources.String.newBuilder().setValue(value));
    return a;
  }

  static String attr(XmlElement e, String name) {
    for (XmlAttribute a : e.getAttributeList()) if (a.getName().equals(name)) return a.getValue();
    return "";
  }

  static XmlNode edit(XmlNode node) {
    if (!node.hasElement()) return node;
    XmlElement original = node.getElement();
    XmlElement.Builder e = original.toBuilder().clearChild();
    boolean main =
        "activity".equals(e.getName())
            && (ORIGINAL + ".MainActivity").equals(attr(original, "name"));
    for (int i = 0; i < e.getAttributeCount(); i++) {
      XmlAttribute.Builder a = e.getAttributeBuilder(i);
      String value = a.getValue();
      if (e.getName().equals("manifest") && a.getName().equals("package")) string(a, APP);
      if (e.getName().equals("application") && a.getName().equals("label"))
        string(a, "DeskLink · HopToDesk");
      if (e.getName().equals("provider")
          && a.getName().equals("authorities")
          && value.startsWith(ORIGINAL + ".")) string(a, APP + value.substring(ORIGINAL.length()));
      if (value.equals(ORIGINAL + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"))
        string(a, APP + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");
      if (main && a.getName().equals("exported")) {
        a.setValue("false");
        a.getCompiledItemBuilder().setPrim(Resources.Primitive.newBuilder().setBooleanValue(false));
      }
    }
    for (XmlNode child : original.getChildList()) {
      XmlElement el = child.getElement();
      if (main && el.getName().equals("intent-filter")) continue;
      if (el.getName().equals("uses-permission")
          && attr(el, "name").equals("android.permission.REQUEST_INSTALL_PACKAGES")) continue;
      e.addChild(edit(child));
      if (el.getName().equals("activity") && attr(el, "name").equals(ORIGINAL + ".MainActivity"))
        e.addChild(homeFrom(el));
    }
    return node.toBuilder().setElement(e).build();
  }

  static XmlNode homeFrom(XmlElement main) {
    XmlElement.Builder home = XmlElement.newBuilder().setName("activity");
    for (XmlAttribute a : main.getAttributeList()) {
      if (a.getName().equals("name"))
        home.addAttribute(string(a.toBuilder(), APP + ".HomeActivity"));
      if (a.getName().equals("exported")) home.addAttribute(a);
    }
    for (XmlNode filter : main.getChildList())
      for (XmlNode child : filter.getElement().getChildList())
        if (attr(child.getElement(), "name").equals("android.intent.action.MAIN"))
          home.addChild(filter);
    if (home.getChildCount() != 1 || home.getAttributeCount() != 2)
      throw new IllegalStateException("Unknown upstream launch metadata");
    return XmlNode.newBuilder().setElement(home).build();
  }

  public static void main(String[] args) throws Exception {
    XmlNode n = XmlNode.parseFrom(Files.readAllBytes(Path.of(args[0])));
    if (!ORIGINAL.equals(attr(n.getElement(), "package")))
      throw new IllegalArgumentException("Wrong upstream identity");
    Files.write(Path.of(args[1]), edit(n).toByteArray());
  }
}
