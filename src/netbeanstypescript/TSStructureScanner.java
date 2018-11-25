/*
 * Copyright 2015-2017 Everlaw
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.ImageIcon;
import org.json.simple.JSONObject;
import org.netbeans.modules.csl.api.*;
import org.netbeans.modules.csl.spi.ParserResult;
import org.openide.filesystems.FileObject;

/**
 *
 * @author jeffrey
 */
public class TSStructureScanner implements StructureScanner {

    static class TSStructureItem extends TSElementHandle implements StructureItem {
        String typeExtends;
        String type;
        Object overrides;

        FileObject fileObj;
        TSStructureItem parent;
        int numOfName;
        List<TSStructureItem> children;

        TSStructureItem(JSONObject item) {
            super(new OffsetRange(((Number) item.get("start")).intValue(),
                                  ((Number) item.get("end")).intValue()),
                    item);
            typeExtends = (String) item.get("extends");
            type = (String) item.get("type");
            overrides = item.get("overrides");
        }

        @Override
        public String getSortText() { return name; }
        @Override
        public String getHtml(HtmlFormatter hf) {
            if (modifiers.contains(Modifier.DEPRECATED)) {
                hf.deprecated(true);
                hf.appendText(name);
                hf.deprecated(false);
            } else {
                hf.appendText(name);
            }
            if (typeExtends != null) {
                hf.appendText(" :: ");
                hf.type(true);
                hf.appendText(typeExtends);
                hf.type(false);
            }
            if (type != null) {
                hf.appendText(" : ");
                hf.type(true);
                hf.appendText(type);
                hf.type(false);
            }
            return hf.getText();
        }
        @Override
        public ElementHandle getElementHandle() { return this; }
        @Override
        public FileObject getFileObject() { return fileObj; }
        @Override
        public String getMimeType() { return "text/typescript"; }
        @Override
        public boolean isLeaf() { return children.isEmpty(); }
        @Override
        public List<? extends StructureItem> getNestedItems() { return children; }
        @Override
        public long getPosition() { return textSpan.getStart(); }
        @Override
        public long getEndPosition() { return textSpan.getEnd(); }
        @Override
        public ImageIcon getCustomIcon() { return getIcon(); }

        @Override
        public boolean equals(Object obj) {
            if (! (obj instanceof TSStructureItem)) return false;
            TSStructureItem left = this;
            TSStructureItem right = (TSStructureItem) obj;
            while (left != right) {
                if (left == null || right == null) return false;
                if (left.kind != right.kind) return false;
                if (! left.name.equals(right.name)) return false;
                if (left.numOfName != right.numOfName) return false;
                left = left.parent;
                right = right.parent;
            }
            return true;
        }
        @Override
        public int hashCode() {
            return Objects.hash(kind, name, numOfName);
        }
    }

    private List<TSStructureItem> convertStructureItems(FileObject fileObj, TSStructureItem parent, Object arr) {
        if (arr == null) {
            return Collections.emptyList();
        }
        List<TSStructureItem> items = new ArrayList<>();
        Map<String, Integer> nameCounts = new HashMap<>();
        for (JSONObject elem: (List<JSONObject>) arr) {
            TSStructureItem item = new TSStructureItem(elem);
            Integer numOfName = nameCounts.get(item.name);
            if (numOfName == null) numOfName = 0;
            nameCounts.put(item.name, numOfName + 1);

            item.fileObj = fileObj;
            item.parent = parent;
            item.numOfName = numOfName;
            item.children = convertStructureItems(fileObj, item, elem.get("children"));
            items.add(item);
        }
        return items;
    }

    @Override
    public List<? extends StructureItem> scan(ParserResult pr) {
        FileObject fo = pr.getSnapshot().getSource().getFileObject();
        return convertStructureItems(fo, null, TSService.call("getStructureItems", fo));
    }

    private final Pattern editorFolds = Pattern.compile("</?editor-fold\\b.*\\s*");

    @Override
    public Map<String, List<OffsetRange>> folds(ParserResult pr) {
        Object arr = TSService.call("getFolds", pr.getSnapshot().getSource().getFileObject());
        if (arr == null) {
            return Collections.emptyMap();
        }
        List<OffsetRange> ranges = new ArrayList<>();
        CharSequence text = pr.getSnapshot().getText();
        for (JSONObject span: (List<JSONObject>) arr) {
            int start = ((Number) span.get("start")).intValue();
            int end = ((Number) span.get("end")).intValue();
            if (text.charAt(start) == '/') {
                // ts.OutliningElementsCollector creates folds for sequences of multiple //-comments
                // preceding a declaration, but this can prevent NetBeans's <editor-fold> directives
                // from working. Remove all lines up to and including the last such directive.
                int startDelta = 0;
                for (Matcher m = editorFolds.matcher(text.subSequence(start, end)); m.find(); ) {
                    startDelta = m.end();
                }
                start += startDelta;
                // There may be only a single line left - for consistency, don't fold it
                if (text.subSequence(start, end).toString().indexOf('\n') < 0) {
                    continue;
                }
            }
            ranges.add(new OffsetRange(start, end));
        }
        return Collections.singletonMap("codeblocks", ranges);
    }

    @Override
    public Configuration getConfiguration() {
        return new Configuration(true, true);
    }    
}
