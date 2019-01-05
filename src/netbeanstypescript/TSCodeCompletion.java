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
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import netbeanstypescript.lexer.api.JsTokenId;
import netbeanstypescript.lexer.api.LexUtilities;
import netbeanstypescript.options.OptionsUtils;
import org.json.simple.JSONObject;
import org.netbeans.api.lexer.TokenId;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.lib.editor.codetemplates.api.CodeTemplate;
import org.netbeans.lib.editor.codetemplates.spi.CodeTemplateFilter;
import org.netbeans.modules.csl.api.*;
import org.netbeans.modules.csl.spi.DefaultCompletionResult;
import org.netbeans.modules.csl.spi.ParserResult;
import org.openide.filesystems.FileObject;

/**
 *
 * @author jeffrey
 */
public class TSCodeCompletion implements CodeCompletionHandler {

    public static class TSCompletionProposal extends TSElementHandle implements CompletionProposal {
        FileObject fileObj;
        int caretOffset;
        int anchorOffset;

        String type;

        TSCompletionProposal(FileObject fileObj, int caretOffset, int anchorOffset, JSONObject m) {
            super(OffsetRange.NONE, m);
            this.fileObj = fileObj;
            this.caretOffset = caretOffset;
            this.anchorOffset = anchorOffset;
            type = (String) m.get("type"); // may be null
        }
        
        @Override
        public int getAnchorOffset() { return anchorOffset; }
        @Override
        public ElementHandle getElement() {
            // Keywords don't have documentation or location info. Calling getCompletionEntryDetails
            // may show irrelevant info about a symbol with the same name.
            // https://github.com/Microsoft/TypeScript/issues/3921
            return kind == ElementKind.KEYWORD ? null : this;
        }
        @Override
        public String document() {
            Object info = TSService.call("getCompletionEntryDetails", fileObj, caretOffset, name);
            return info == null ? null : new TSElementHandle(OffsetRange.NONE, (JSONObject) info).document();
        }
        @Override
        public String getInsertPrefix() { return name; }
        @Override
        public String getSortText() { return null; }
        @Override
        public String getLhsHtml(HtmlFormatter hf) {
            if (modifiers.contains(Modifier.DEPRECATED)) {
                hf.deprecated(true);
                hf.appendText(name);
                hf.deprecated(false);
            } else {
                hf.appendText(name);
            }
            return hf.getText();
        }
        @Override
        public String getRhsHtml(HtmlFormatter hf) {
            hf.setMaxLength(OptionsUtils.forLanguage(JsTokenId.javascriptLanguage()).getCodeCompletionItemSignatureWidth());
            if (type == null) {
                return null;
            }
            hf.type(true);
            hf.appendText(type);
            hf.type(false);
            return hf.getText();
        }
        @Override
        public boolean isSmart() { return false; }
        @Override
        public int getSortPrioOverride() { return 0; }
        @Override
        public String getCustomInsertTemplate() {
            String suffix = "";
            switch (getKind()) {
                case METHOD: suffix = "(${cursor})"; break;
                case PACKAGE: suffix = "/"; break;
            }
            return getInsertPrefix() + suffix;
        }

        private JSONObject location;
        @Override
        public FileObject getFileObject() {
            location = (JSONObject) TSService.call("getCompletionEntryLocation",
                    fileObj, caretOffset, name);
            if (location == null) return null;
            return TSService.findAnyFileObject((String) location.get("fileName"));
        }
        @Override
        public String getMimeType() {
            return "text/typescript";
        }
        @Override
        public OffsetRange getOffsetRange(ParserResult pr) {
            // This method only gets called when the location is in the same file that the
            // completion was done in. Otherwise, csl.api's UiUtils.open just opens the file
            // and doesn't set the offset, even though we have it. :(
            if (location == null) return OffsetRange.NONE;
            return new OffsetRange(((Number) location.get("start")).intValue(),
                                   ((Number) location.get("end")).intValue());
        }
    }

    static long lastCompletionTime;
    static boolean lastCompletionWasGlobal;

    @Override
    public CodeCompletionResult complete(CodeCompletionContext ccc) {
        FileObject fileObj = ccc.getParserResult().getSnapshot().getSource().getFileObject();
        int caretOffset = ccc.getCaretOffset();
        String prefix = ccc.getPrefix();
        if (! ccc.isCaseSensitive()) prefix = prefix.toLowerCase();
        JSONObject info;
        synchronized (TSCodeCompletion.class) {
            info = (JSONObject) TSService.call("getCompletions", fileObj, caretOffset);
            lastCompletionTime = System.currentTimeMillis();
            lastCompletionWasGlobal = info != null && Boolean.TRUE.equals(info.get("isGlobalCompletion"));
            TSCodeCompletion.class.notify();
        }
        if (info == null) {
            return CodeCompletionResult.NONE;
        }

        List<CompletionProposal> lst = new ArrayList<>();
        for (JSONObject entry: (List<JSONObject>) info.get("entries")) {
            String name = (String) entry.get("name");
            if (! ccc.isCaseSensitive()) name = name.toLowerCase();
            if (ccc.isPrefixMatch() ? name.startsWith(prefix) : name.equals(prefix)) {
                lst.add(new TSCodeCompletion.TSCompletionProposal(
                        fileObj,
                        caretOffset,
                        caretOffset - prefix.length(),
                        entry));
            }
        }
        return new DefaultCompletionResult(lst, false);
    }

    @Override
    public String document(ParserResult pr, ElementHandle eh) {
        return ((TSElementHandle) eh).document();
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

    // CHARS_NO_AUTO_COMPLETE and getAutoQuery from javascript2.editor JsCodeCompletion
    private static final String CHARS_NO_AUTO_COMPLETE = ";,/+-\\:={}[]()"; //NOI18N

    @Override
    public QueryType getAutoQuery(JTextComponent component, String typedText) {
        if (typedText.length() == 0) {
            return QueryType.NONE;
        }

        int offset = component.getCaretPosition();
        TokenSequence<? extends JsTokenId> ts = LexUtilities.getJsTokenSequence(component.getDocument(), offset);
        if (ts != null) {
            int diff = ts.move(offset);
            TokenId currentTokenId = null;
            if (diff == 0 && ts.movePrevious() || ts.moveNext()) {
                currentTokenId = ts.token().id();
            }

            char lastChar = typedText.charAt(typedText.length() - 1);
            if (currentTokenId == JsTokenId.BLOCK_COMMENT || currentTokenId == JsTokenId.DOC_COMMENT
                    || currentTokenId == JsTokenId.LINE_COMMENT) {
                if (lastChar == '@') { //NOI18N
                    return QueryType.COMPLETION;
                }
            } else if (currentTokenId == JsTokenId.STRING && lastChar == '/') {
                return QueryType.COMPLETION;
            } else {
                switch (lastChar) {
                    case '.': //NOI18N
                        if (OptionsUtils.forLanguage(JsTokenId.javascriptLanguage()).autoCompletionAfterDot()) {
                            return QueryType.COMPLETION;
                        }
                        break;
                    default:
                        if (OptionsUtils.forLanguage(JsTokenId.javascriptLanguage()).autoCompletionFull()) {
                            if (!Character.isWhitespace(lastChar) && CHARS_NO_AUTO_COMPLETE.indexOf(lastChar) == -1) {
                                return QueryType.COMPLETION;
                            }
                        }
                        return QueryType.NONE;
                }
            }
        }
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

    public static class TemplateFilterFactory implements CodeTemplateFilter.ContextBasedFactory {
        @Override
        public CodeTemplateFilter createFilter(JTextComponent component, int offset) {
            if (! Thread.currentThread().getName().equals("Code Completion")) {
                // Called from AbbrevDetection or SurroundWithFix - just allow it
                return createFilter(true);
            }
            // This is a code completion (called from CodeTemplateCompletionProvider). To determine
            // whether code templates should show up, we need the result from .complete(), but it's
            // running in a different thread. To make matters even worse, typing or backspacing
            // characters during a completion may re-run this method without re-running .complete().
            synchronized (TSCodeCompletion.class) {
                // Assume that if .complete() is called, the two synchronized blocks are entered
                // within 50ms of each other.
                if (lastCompletionTime < System.currentTimeMillis() - 50) {
                    try {
                        // .complete() has not yet entered its sync block; wait for it.
                        TSCodeCompletion.class.wait(50);
                        // If it was called, it'll have finished its sync block now. If not, we're
                        // still in the same completion and lastCompletionWasGlobal is still valid.
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return createFilter(lastCompletionWasGlobal);
            }
        }

        private CodeTemplateFilter createFilter(final boolean accept) {
            return new CodeTemplateFilter() {
                @Override public boolean accept(CodeTemplate template) { return accept; }
            };
        }

        @Override
        public List<String> getSupportedContexts() {
            return Collections.singletonList("JavaScript-Code");
        }
    }
}
