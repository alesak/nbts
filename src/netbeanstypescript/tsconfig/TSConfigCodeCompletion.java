/*
 * Copyright 2016-2018 Everlaw
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
package netbeanstypescript.tsconfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import netbeanstypescript.tsconfig.TSConfigParser.ConfigNode;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.netbeans.modules.csl.api.*;
import org.netbeans.modules.csl.spi.DefaultCompletionProposal;
import org.netbeans.modules.csl.spi.DefaultCompletionResult;
import org.netbeans.modules.csl.spi.ParserResult;
import org.openide.filesystems.FileObject;

/**
 *
 * @author jeffrey
 */
public class TSConfigCodeCompletion implements CodeCompletionHandler {

    static class TSConfigElementHandle implements ElementHandle {
        private static final Set<String> commandLineOnlySet = new HashSet<>(Arrays.asList(
                "all", "help", "init", "locale", "project", "version"));

        String name;
        boolean commandLineOnly;
        Object type;
        boolean hidden;
        String message;
        TSConfigElementHandle element;
        String deprecated;

        TSConfigElementHandle(JSONObject obj) {
            name = (String) obj.get("name");
            commandLineOnly = commandLineOnlySet.contains(name);
            type = obj.get("type");
            message = (String) obj.get("description");
            if (message != null) {
                if (message.startsWith("[Deprecated]")) {
                    int depMessageEnd = message.indexOf('.', 12);
                    deprecated = message.substring(12, depMessageEnd >= 0 ? depMessageEnd + 1 : message.length());
                    hidden = true;
                }
            } else {
                hidden = true;
            }
            JSONObject elem = (JSONObject) obj.get("element");
            if (elem != null) {
                element = new TSConfigElementHandle(elem);
            }
        }

        TSConfigElementHandle(String name, Object type, String message) {
            this.name = name;
            this.type = type;
            this.message = message;
        }

        @Override
        public FileObject getFileObject() { return null; }
        @Override
        public String getMimeType() { return null; }
        @Override
        public String getName() { return name; }
        @Override
        public String getIn() { return null; }
        @Override
        public ElementKind getKind() { return ElementKind.PROPERTY; }
        @Override
        public Set<Modifier> getModifiers() { return Collections.emptySet(); }
        @Override
        public boolean signatureEquals(ElementHandle eh) { return false; }
        @Override
        public OffsetRange getOffsetRange(ParserResult pr) { return OffsetRange.NONE; }
    }

    @Override
    public CodeCompletionResult complete(CodeCompletionContext ccc) {
        ConfigNode node = ((TSConfigParser.Result) ccc.getParserResult()).root;
        if (node == null) {
            return CodeCompletionResult.NONE;
        }

        final int caret = ccc.getCaretOffset();
        String prefix = ccc.getPrefix();
        NODE: while (true) {
            if (node.properties != null) {
                int keyStart = caret;
                for (ConfigNode property: node.properties.values()) {
                    if (caret >= property.keyOffset && caret <= property.endOffset) {
                        if (caret >= property.startOffset) {
                            node = property;
                            continue NODE;
                        }
                        keyStart = property.keyOffset;
                    }
                }
                if (node.validMap != null && caret > node.startOffset && caret < node.endOffset) {
                    List<CompletionProposal> proposals = new ArrayList<>();
                    for (final TSConfigElementHandle element: node.validMap.values()) {
                        if (element.commandLineOnly || element.hidden || ! element.name.startsWith(prefix)) {
                            continue;
                        }
                        DefaultCompletionProposal prop = new DefaultCompletionProposal() {
                            @Override
                            public String getName() { return element.name; }
                            @Override
                            public ElementHandle getElement() { return element; }
                            @Override
                            public String getRhsHtml(HtmlFormatter hf) {
                                hf.type(true);
                                hf.appendText(element.type instanceof String ? (String) element.type : "enum");
                                hf.type(false);
                                return hf.getText();
                            }
                            @Override
                            public String getCustomInsertTemplate() {
                                String value = "";
                                if (element.type instanceof String) {
                                    switch ((String) element.type) {
                                        case "boolean": value = "true"; break;
                                        case "string": value = "\"${cursor}\""; break;
                                        case "object": value = "{${cursor}}"; break;
                                        case "list": value = "[${cursor}]"; break;
                                    }
                                }
                                return '"' + getInsertPrefix() + "\": " + value;
                            }
                        };
                        prop.setAnchorOffset(keyStart);
                        prop.setKind(element.getKind());
                        proposals.add(prop);
                    }
                    return new DefaultCompletionResult(proposals, false);
                }
            }
            if (node.expectedType instanceof JSONArray) {
                List<CompletionProposal> proposals = new ArrayList<>();
                for (final Object validValue: (JSONArray) node.expectedType) {
                    DefaultCompletionProposal prop = new DefaultCompletionProposal() {
                        @Override
                        public String getName() { return "\"" + validValue + '"'; }
                        @Override
                        public ElementHandle getElement() { return null; }
                    };
                    prop.setAnchorOffset(node.missing ? caret : node.startOffset);
                    prop.setKind(ElementKind.PARAMETER);
                    proposals.add(prop);
                }
                return new DefaultCompletionResult(proposals, false);
            }
            return CodeCompletionResult.NONE;
        }
    }

    @Override
    public String document(ParserResult pr, ElementHandle eh) {
        TSConfigElementHandle elem = (TSConfigElementHandle) eh;
        return elem.message;
    }

    @Override
    public ElementHandle resolveLink(String string, ElementHandle eh) {
        return null;
    }

    @Override
    public String getPrefix(ParserResult info, int caretOffset, boolean upToOffset) {
        CharSequence seq = info.getSnapshot().getText();
        int i = caretOffset, j = i;
        while (i > 0 && Character.isJavaIdentifierPart(seq.charAt(i - 1))) {
            i--;
        }
        while (! upToOffset && j < seq.length() && Character.isJavaIdentifierPart(seq.charAt(j))) {
            j++;
        }
        return seq.subSequence(i, j).toString();
    }

    @Override
    public QueryType getAutoQuery(JTextComponent jtc, String string) {
        return QueryType.NONE;
    }

    @Override
    public String resolveTemplateVariable(String string, ParserResult pr, int i, String string1, Map map) {
        return null;
    }

    @Override
    public Set<String> getApplicableTemplates(Document dcmnt, int i, int i1) {
        return null;
    }

    @Override
    public ParameterInfo parameters(ParserResult pr, int i, CompletionProposal cp) {
        return ParameterInfo.NONE;
    }
}
