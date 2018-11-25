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

import netbeanstypescript.lexer.api.JsTokenId;
import org.netbeans.api.lexer.Language;
import org.netbeans.core.spi.multiview.MultiViewElement;
import org.netbeans.core.spi.multiview.text.MultiViewEditorElement;
import org.netbeans.modules.csl.api.*;
import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;
import org.netbeans.modules.parsing.spi.Parser;
import org.openide.filesystems.MIMEResolver;
import org.openide.util.Lookup;
import org.openide.windows.TopComponent;

/**
 *
 * @author jeffrey
 */
@LanguageRegistration(mimeType="text/typescript", useMultiview=true)
public class TSLanguage extends DefaultLanguageConfig {

    @MIMEResolver.ExtensionRegistration(
            displayName = "TypeScript files",
            mimeType = "text/typescript",
            extension = {"ts", "tsx"}
    )
    @MultiViewElement.Registration(
            displayName = "Source",
            iconBase = "netbeanstypescript/resources/typescript.png",
            mimeType = "text/typescript",
            persistenceType = TopComponent.PERSISTENCE_ONLY_OPENED,
            preferredID = "TS"
    )
    public static MultiViewEditorElement createEditor(Lookup context) {
        return new MultiViewEditorElement(context);
    }

    @Override
    public String getLineCommentPrefix() { return "//"; }

    @Override
    public Language getLexerLanguage() { return JsTokenId.javascriptLanguage(); }

    @Override
    public String getDisplayName() { return "TypeScript"; }

    @Override
    public Parser getParser() { return new TSParser(); }

    @Override
    public CodeCompletionHandler getCompletionHandler() { return new TSCodeCompletion(); }

    @Override
    public InstantRenamer getInstantRenamer() { return new TSInstantRenamer(); }

    @Override
    public boolean hasStructureScanner() { return true; }
    @Override
    public StructureScanner getStructureScanner() { return new TSStructureScanner(); }
    
    @Override
    public DeclarationFinder getDeclarationFinder() { return new TSDeclarationFinder(); }

    @Override
    public boolean hasOccurrencesFinder() { return true; }
    @Override
    public OccurrencesFinder getOccurrencesFinder() { return new TSOccurrencesFinder(); }

    @Override
    public SemanticAnalyzer getSemanticAnalyzer() { return new TSSemanticAnalyzer(); }

    @Override
    public boolean hasFormatter() { return true; }
    @Override
    public Formatter getFormatter() { return new TSFormatter(); }

    @Override
    public OverridingMethods getOverridingMethods() { return new TSOverridingMethods(); }

    @Override
    public boolean hasHintsProvider() { return true; }
    @Override
    public HintsProvider getHintsProvider() { return new TSHintsProvider(); }
}
