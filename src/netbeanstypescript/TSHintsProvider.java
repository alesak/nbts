/*
 * Copyright 2017-2018 Everlaw
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import javax.swing.text.BadLocationException;
import org.json.simple.JSONObject;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.csl.api.*;
import org.netbeans.modules.csl.api.Error;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;

/**
 *
 * @author jeffrey
 */
public class TSHintsProvider implements HintsProvider {

    @Override
    public void computeHints(HintsManager manager, RuleContext context, List<Hint> hints) {}

    public static void doFixes(final BaseDocument doc, FileObject fileObj, final List<JSONObject> changes) {
        for (JSONObject change: changes) {
            Object fileName = change.get("fileName");
            if (! fileName.equals(fileObj.getPath())) {
                String error = "Unimplemented: code fix involves changes to a different file " + fileName;
                DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message(error, NotifyDescriptor.ERROR_MESSAGE));
                return;
            }
        }
        doc.runAtomic(new Runnable() {
            @Override
            public void run() {
                try {
                    for (JSONObject change: changes) {
                        TSFormatter.applyEdits(doc, change.get("textChanges"));
                    }
                } catch (BadLocationException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        });
    }

    @Override
    public void computeSuggestions(HintsManager manager, final RuleContext context, List<Hint> suggestions, int caretOffset) {
        // Group the possibly-fixable errors by span and dedupe, as getCodeFixesAtPosition requires
        LinkedHashMap<OffsetRange, LinkedHashSet<Integer>> errsBySpan = new LinkedHashMap<>();
        for (Error err: context.parserResult.getDiagnostics()) {
            int errStart = err.getStartPosition(), errEnd = err.getEndPosition();
            if (err.getKey() != null && caretOffset >= errStart && caretOffset <= errEnd) {
                OffsetRange span = new OffsetRange(errStart, errEnd);
                LinkedHashSet<Integer> errCodes = errsBySpan.get(span);
                if (errCodes == null) {
                    errsBySpan.put(span, errCodes = new LinkedHashSet<>());
                }
                errCodes.add(Integer.parseInt(err.getKey()));
            }
        }
        for (OffsetRange span: errsBySpan.keySet()) {
            final FileObject fileObj = context.parserResult.getSnapshot().getSource().getFileObject();
            Object fixes = TSService.call("getCodeFixesAtPosition", fileObj, span.getStart(), span.getEnd(),
                    errsBySpan.get(span), // amazingly, LinkedHashSet<Integer>'s toString is valid JSON
                    TSFormatter.getFormattingSettings(context.doc));
            if (fixes == null) {
                continue;
            }
            List<HintFix> hintFixes = new ArrayList<>();
            for (final JSONObject fix: (List<JSONObject>) fixes) {
                hintFixes.add(new HintFix() {
                    @Override
                    public String getDescription() {
                        return (String) fix.get("description");
                    }
                    @Override
                    public void implement() {
                        doFixes(context.doc, fileObj, (List<JSONObject>) fix.get("changes"));
                    }
                    @Override
                    public boolean isSafe() { return false; }
                    @Override
                    public boolean isInteractive() { return false; }
                });
            }
            if (! hintFixes.isEmpty()) {
                Rule rule = new Rule() {
                    @Override public boolean appliesTo(RuleContext rc) { return true; }
                    @Override public String getDisplayName() { return "TS code fix"; }
                    @Override public boolean showInTasklist() { return false; }
                    @Override public HintSeverity getDefaultSeverity() { return HintSeverity.ERROR; }
                };
                suggestions.add(new Hint(rule,
                        hintFixes.size() + (hintFixes.size() == 1 ? " code fix" : " code fixes") + " available",
                        fileObj, span, hintFixes, 0));
            }
        }
        computeSelectionHints(manager, context, suggestions, caretOffset, caretOffset);
    }

    @Override
    public void computeSelectionHints(HintsManager manager, final RuleContext context,
            List<Hint> suggestions, final int start, final int end) {
        final FileObject fileObj = context.parserResult.getSnapshot().getSource().getFileObject();
        Object refactors = TSService.call("getApplicableRefactors", fileObj, start, end);
        if (refactors == null) {
            return;
        }
        for (final JSONObject refactor: (List<JSONObject>) refactors) {
            List<HintFix> hintFixes = new ArrayList<>();
            for (final JSONObject action: (List<JSONObject>) refactor.get("actions")) {
                hintFixes.add(new HintFix() {
                    @Override
                    public String getDescription() { return (String) action.get("description"); }
                    @Override
                    public void implement() {
                        JSONObject edits = (JSONObject) TSService.call("getEditsForRefactor", fileObj,
                                TSFormatter.getFormattingSettings(context.doc),
                                start, end, refactor.get("name"), action.get("name"));
                        if (edits == null) {
                            String error = "getEditsForRefactor returned null";
                            DialogDisplayer.getDefault().notify(
                                    new NotifyDescriptor.Message(error, NotifyDescriptor.ERROR_MESSAGE));
                            return;
                        }
                        doFixes(context.doc, fileObj, (List<JSONObject>) edits.get("edits"));
                    }
                    @Override
                    public boolean isSafe() { return false; }
                    @Override
                    public boolean isInteractive() { return false; }
                });
            }
            Rule rule = new Rule() {
                @Override public boolean appliesTo(RuleContext rc) { return true; }
                @Override public String getDisplayName() { return "TS refactor"; }
                @Override public boolean showInTasklist() { return false; }
                @Override public HintSeverity getDefaultSeverity() { return HintSeverity.INFO; }
            };
            suggestions.add(new Hint(rule,
                    (String) refactor.get("description"),
                    fileObj, new OffsetRange(start, end), hintFixes, 0));
        }
    }

    @Override
    public void computeErrors(HintsManager manager, RuleContext context, List<Hint> hints, List<Error> unhandled) {
        // There may be relevant code fixes, but we can't query code fixes without fully computing
        // their diffs, which is potentially too expensive to do for all errors in the file.
        // So we provide code fixes as "suggestions" instead.
        unhandled.addAll(context.parserResult.getDiagnostics());
    }

    @Override
    public void cancel() {}

    @Override
    public List<Rule> getBuiltinRules() { return null; }

    @Override
    public RuleContext createRuleContext() { return new RuleContext(); }
}
