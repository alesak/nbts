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

import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.text.BadLocationException;
import org.json.simple.JSONObject;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.csl.api.Formatter;
import org.netbeans.modules.csl.api.OffsetRange;
import org.netbeans.modules.csl.spi.GsfUtilities;
import org.netbeans.modules.csl.spi.ParserResult;
import org.netbeans.modules.editor.indent.api.IndentUtils;
import org.netbeans.modules.editor.indent.spi.CodeStylePreferences;
import org.netbeans.modules.editor.indent.spi.Context;
import org.openide.util.Exceptions;

/**
 *
 * @author jeffrey
 */
public class TSFormatter implements Formatter {

    public static JSONObject getFormattingSettings(BaseDocument doc) {
        JSONObject settings = new JSONObject();
        settings.put("indentSize", IndentUtils.indentLevelSize(doc));
        settings.put("tabSize", IndentUtils.tabSize(doc));
        settings.put("newLineCharacter", "\n");
        settings.put("convertTabsToSpaces", IndentUtils.isExpandTabs(doc));
        settings.put("indentStyle", 2);
        // TODO: The JS editor's settings don't correspond well with ts.FormatCodeSettings.
        // Should probably create a separate text/typescript style preferences dialog, so
        // it's clear to the user what can and can't be changed.
        Preferences jsPrefs = CodeStylePreferences.get(doc, "text/javascript").getPreferences();
        settings.put("insertSpaceAfterCommaDelimiter",
                jsPrefs.getBoolean("spaceAfterComma", true));
        settings.put("insertSpaceAfterSemicolonInForStatements",
                jsPrefs.getBoolean("spaceAfterSemi", true));
        settings.put("insertSpaceBeforeAndAfterBinaryOperators",
                jsPrefs.getBoolean("spaceAroundBinaryOps", true));
        settings.put("insertSpaceAfterKeywordsInControlFlowStatements",
                jsPrefs.getBoolean("spaceBeforeIfParen", true));
        settings.put("insertSpaceAfterFunctionKeywordForAnonymousFunctions",
                jsPrefs.getBoolean("spaceBeforeAnonMethodDeclParen", true));
        settings.put("insertSpaceAfterOpeningAndBeforeClosingNonemptyParenthesis",
                jsPrefs.getBoolean("spaceWithinParens", false));
        settings.put("insertSpaceAfterOpeningAndBeforeClosingNonemptyBrackets",
                jsPrefs.getBoolean("spaceWithinArrayBrackets", false));
        settings.put("insertSpaceAfterOpeningAndBeforeClosingNonemptyBraces",
                jsPrefs.getBoolean("spaceWithinBraces", false));
        settings.put("insertSpaceAfterOpeningAndBeforeClosingTemplateStringBraces",
                jsPrefs.getBoolean("spaceWithinBraces", false));
        settings.put("insertSpaceAfterOpeningAndBeforeClosingJsxExpressionBraces",
                jsPrefs.getBoolean("spaceWithinBraces", false));
        settings.put("insertSpaceAfterTypeAssertion",
                // JS doesn't have typecasts...
                CodeStylePreferences.get(doc, "text/x-java").getPreferences()
                        .getBoolean("spaceAfterTypeCast", true));
        settings.put("placeOpenBraceOnNewLineForFunctions",
                jsPrefs.get("functionDeclBracePlacement", "").startsWith("NEW"));
        settings.put("placeOpenBraceOnNewLineForControlBlocks",
                jsPrefs.get("ifBracePlacement", "").startsWith("NEW"));
        return settings;
    }

    @Override
    public void reformat(Context context, ParserResult pr) {
        final BaseDocument doc = (BaseDocument) context.document();
        final Object edits = TSService.call("getFormattingEdits",
                GsfUtilities.findFileObject(doc), context.startOffset(), context.endOffset(),
                getFormattingSettings(doc));
        if (edits == null) {
            return;
        }
        doc.runAtomic(new Runnable() {
            @Override
            public void run() {
                try {
                    applyEdits(doc, edits);
                } catch (BadLocationException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        });
    }

    public static OffsetRange applyEdits(BaseDocument doc, Object edits) throws BadLocationException {
        int sizeChange = 0;
        int editsStart = -1, editsEnd = -1;
        for (JSONObject edit: (List<JSONObject>) edits) {
            JSONObject span = (JSONObject) edit.get("span");
            int start = ((Number) span.get("start")).intValue() + sizeChange;
            int length = ((Number) span.get("length")).intValue();
            String newText = (String) edit.get("newText");
            doc.replace(start, length, newText, null);
            sizeChange += newText.length() - length;
            if (editsStart == -1) {
                editsStart = start;
            }
            editsEnd = start + newText.length();
        }
        return editsStart >= 0 ? new OffsetRange(editsStart, editsEnd) : null;
    }

    @Override
    public void reindent(Context context) {} // JsTypedBreakInterceptor handles indentation

    @Override
    public boolean needsParserResult() {
        return true; // just making sure services has an up to date copy of the source
    }

    @Override
    public int indentSize() { return 4; }

    @Override
    public int hangingIndentSize() { return 8; }
}
