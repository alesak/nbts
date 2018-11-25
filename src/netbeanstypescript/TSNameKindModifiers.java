/*
 * Copyright 2015-2018 Everlaw
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package netbeanstypescript;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.ImageIcon;
import org.json.simple.JSONObject;
import org.netbeans.modules.csl.api.ElementKind;
import org.netbeans.modules.csl.api.Modifier;
import org.openide.util.ImageUtilities;

/**
 *
 * @author jeffrey
 */
public class TSNameKindModifiers {
    static final ImageIcon enumIcon = new ImageIcon(ImageUtilities.loadImage(
            "org/netbeans/modules/csl/source/resources/icons/enum.png"));
    static final ImageIcon interfaceIcon = new ImageIcon(ImageUtilities.loadImage(
            "org/netbeans/modules/csl/source/resources/icons/interface.png"));
    static final ImageIcon folderIcon = new ImageIcon(ImageUtilities.loadImage(
            "org/openide/loaders/defaultFolder.gif"));

    String name;
    ElementKind kind = ElementKind.OTHER;
    ImageIcon icon = null;
    Set<Modifier> modifiers = Collections.emptySet();

    TSNameKindModifiers(JSONObject obj) {
        name = (String) obj.get("name");

        // See ScriptElementKind in services/types.ts
        switch ((String) obj.get("kind")) {
            case "warning": break;
            case "keyword": kind = ElementKind.KEYWORD; break;
            case "script": kind = ElementKind.FILE; break;
            case "module": kind = ElementKind.MODULE; break;
            case "class": case "local class": kind = ElementKind.CLASS; break;
            case "interface": case "type": kind = ElementKind.INTERFACE; icon = interfaceIcon; break;
            case "enum": kind = ElementKind.CLASS; icon = enumIcon; break;
            case "enum member": kind = ElementKind.PARAMETER; break;
            case "var": kind = ElementKind.VARIABLE; break;
            case "local var": kind = ElementKind.VARIABLE; break;
            case "function": kind = ElementKind.METHOD; break;
            case "local function": kind = ElementKind.METHOD; break;
            case "method": kind = ElementKind.METHOD; break;
            case "getter": kind = ElementKind.FIELD; break;
            case "setter": kind = ElementKind.FIELD; break;
            case "property": kind = ElementKind.FIELD; break;
            case "constructor": kind = ElementKind.CONSTRUCTOR; break;
            case "call": break;
            case "index": break;
            case "construct": break;
            case "parameter": kind = ElementKind.PARAMETER; break;
            case "type parameter": break;
            case "primitive type": break;
            case "label": break;
            case "alias": break;
            case "const": kind = ElementKind.CONSTANT; break;
            case "let": kind = ElementKind.VARIABLE; break;
            case "directory": kind = ElementKind.PACKAGE; icon = folderIcon; break;
            case "external module name": kind = ElementKind.MODULE; break;
            case "JSX attribute": break;
            default: TSService.log.log(Level.WARNING, "Unknown symbol kind [{0}]", obj.get("kind"));
        }

        // See ScriptElementKindModifier in services/types.ts
        String kindModifiers = (String) obj.get("kindModifiers");
        if (kindModifiers != null && ! kindModifiers.isEmpty()) {
            modifiers = EnumSet.noneOf(Modifier.class);
            for (String modifier: kindModifiers.split(",")) {
                switch (modifier) {
                    case "public": modifiers.add(Modifier.PUBLIC); break;
                    case "private": modifiers.add(Modifier.PRIVATE); break;
                    case "protected": modifiers.add(Modifier.PROTECTED); break;
                    case "export": break;
                    case "declare": break;
                    case "static": modifiers.add(Modifier.STATIC); break;
                    case "abstract": modifiers.add(Modifier.ABSTRACT); break;
                    case "optional": break;
                    case "deprecated": modifiers.add(Modifier.DEPRECATED); break;
                    default: TSService.log.log(Level.WARNING, "Unknown modifier [{0}]", modifier);
                }
            }
        }
    }

    public String getName() { return name; }
    public ElementKind getKind() { return kind; }
    public ImageIcon getIcon() { return icon; }
    public Set<Modifier> getModifiers() { return modifiers; }
}
