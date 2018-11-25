/*
 * Copyright 2016 Everlaw
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

import netbeanstypescript.lexer.api.JsTokenId;
import org.netbeans.api.lexer.Language;
import org.netbeans.core.spi.multiview.MultiViewElement;
import org.netbeans.core.spi.multiview.text.MultiViewEditorElement;
import org.netbeans.modules.csl.api.CodeCompletionHandler;
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
@LanguageRegistration(mimeType="text/tsconfig+x-json", useMultiview=true)
public class TSConfigLanguage extends DefaultLanguageConfig {

    @MIMEResolver.Registration(displayName = "tsconfig", resource = "../resources/tsconfig-resolver.xml")
    @MultiViewElement.Registration(
            displayName = "Source",
            iconBase = "netbeanstypescript/resources/typescript.png",
            mimeType = "text/tsconfig+x-json",
            persistenceType = TopComponent.PERSISTENCE_ONLY_OPENED,
            preferredID = "TS"
    )
    public static MultiViewEditorElement createEditor(Lookup context) {
        return new MultiViewEditorElement(context);
    }

    @Override
    public String getLineCommentPrefix() { return "//"; }

    @Override
    public Language<?> getLexerLanguage() { return JsTokenId.javascriptLanguage(); }

    @Override
    public String getDisplayName() { return "TypeScript tsconfig.json"; }

    @Override
    public Parser getParser() { return new TSConfigParser(); }

    @Override
    public CodeCompletionHandler getCompletionHandler() { return new TSConfigCodeCompletion(); }
}
