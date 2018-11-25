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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import javax.swing.text.Document;
import netbeanstypescript.lexer.api.JsTokenId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.modules.csl.api.DeclarationFinder;
import org.netbeans.modules.csl.api.ElementHandle;
import org.netbeans.modules.csl.api.HtmlFormatter;
import org.netbeans.modules.csl.api.OffsetRange;
import org.netbeans.modules.csl.spi.ParserResult;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.ParseException;
import org.openide.filesystems.FileObject;
import org.openide.util.RequestProcessor;

/**
 *
 * @author jeffrey
 */
public class TSDeclarationFinder implements DeclarationFinder {

    private final RequestProcessor RP = new RequestProcessor(TSDeclarationFinder.class.getName());

    @Override
    public DeclarationLocation findDeclaration(ParserResult info, int caretOffset) {
        FileObject fileObj = info.getSnapshot().getSource().getFileObject();
        JSONArray defs = (JSONArray) TSService.call("getDefsAtPosition", fileObj, caretOffset);
        if (defs == null) {
            return DeclarationLocation.NONE;
        }

        TSElementHandle eh = null;
        JSONObject quickInfo = (JSONObject) TSService.call("getQuickInfoAtPosition", fileObj, caretOffset);
        if (quickInfo != null) {
            eh = new TSElementHandle(new OffsetRange(
                    ((Number) quickInfo.get("start")).intValue(),
                    ((Number) quickInfo.get("end")).intValue()), quickInfo);
        }

        DeclarationLocation allLocs = new DeclarationLocation(fileObj, caretOffset, eh);

        for (final JSONObject def: (List<JSONObject>) defs) {
            final String destFileName = (String) def.get("fileName");
            FileObject destFileObj = TSService.findAnyFileObject(destFileName);
            if (destFileObj == null) {
                return DeclarationLocation.NONE;
            }
            int destOffset = ((Number) def.get("start")).intValue();
            final DeclarationLocation declLoc = new DeclarationLocation(destFileObj, destOffset, eh);
            if (defs.size() == 1) {
                return declLoc; // can't use AlternativeLocations when there's only one
            }

            final TSElementHandle handle = new TSElementHandle(OffsetRange.NONE, def);
            allLocs.addAlternative(new DeclarationFinder.AlternativeLocation() {
                @Override
                public ElementHandle getElement() { return handle; }
                @Override
                public String getDisplayHtml(HtmlFormatter hf) {
                    hf.appendHtml("<nobr>"); // Workaround for https://netbeans.org/bugzilla/show_bug.cgi?id=269729
                    hf.appendText((String) def.get("kind"));
                    hf.appendText(" ");
                    String containerName = (String) def.get("containerName");
                    if (! containerName.isEmpty()) {
                        hf.appendText(containerName);
                        hf.appendText(".");
                    }
                    hf.appendText((String) def.get("name"));
                    hf.appendText(" @ ");
                    hf.appendText(destFileName);
                    hf.appendText(":");
                    hf.appendText(def.get("line").toString());
                    return hf.getText();
                }
                @Override
                public DeclarationLocation getLocation() { return declLoc; }
                @Override
                public int compareTo(DeclarationFinder.AlternativeLocation o) { return 0; }
            });
        }
        return allLocs;
    }

    private final Set<String> possibleRefs = new HashSet<>(Arrays.asList(
            "comment", "identifier", "keyword", "string"));

    @Override
    public OffsetRange getReferenceSpan(final Document doc, final int caretOffset) {
        final OffsetRange[] tokenRange = new OffsetRange[1];
        doc.render(new Runnable() {
            @Override public void run() {
                TokenSequence<?> ts = TokenHierarchy.get(doc)
                        .tokenSequence(JsTokenId.javascriptLanguage());
                int offsetWithinToken = ts.move(caretOffset);
                if (ts.moveNext()) {
                    Token<?> tok = ts.token();
                    if (possibleRefs.contains(tok.id().primaryCategory())) {
                        int start = caretOffset - offsetWithinToken;
                        tokenRange[0] = new OffsetRange(start, start + tok.length());
                        return;
                    }
                }
                // If we're right between two tokens, check the previous
                if (offsetWithinToken == 0 && ts.movePrevious()) {
                    Token<?> tok = ts.token();
                    if (possibleRefs.contains(tok.id().primaryCategory())) {
                        tokenRange[0] = new OffsetRange(caretOffset - tok.length(), caretOffset);
                    }
                }
            }
        });
        if (tokenRange[0] == null) {
            return OffsetRange.NONE;
        }

        // Now query the language service to see if this is actually a reference
        final AtomicBoolean isReference = new AtomicBoolean();
        class ReferenceSpanTask extends UserTask implements Runnable {
            @Override
            public void run() {
                try {
                    ParserManager.parse(Collections.singleton(Source.create(doc)), this);
                } catch (ParseException e) {
                    TSService.log.log(Level.WARNING, null, e);
                }
            }
            @Override
            public void run(ResultIterator ri) throws ParseException {
                // Calling ResultIterator#getParserResult() ensures latest snapshot pushed to server
                Object defs = TSService.call("getDefsAtPosition",
                        ri.getParserResult().getSnapshot().getSource().getFileObject(),
                        caretOffset);
                isReference.set(defs != null);
            }
        }
        // Don't block the UI thread for too long in case server is busy
        RequestProcessor.Task task = RP.post(new ReferenceSpanTask());
        try {
            task.waitFinished(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            task.cancel();
        }
        return isReference.get() ? tokenRange[0] : OffsetRange.NONE;
    }
}
